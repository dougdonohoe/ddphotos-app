package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Utils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.config.StylesConfig;
import com.donohoedigital.ddphotos.runner.CommandRunner;
import com.donohoedigital.gui.DDButton;
import com.donohoedigital.gui.DDCheckBox;
import com.donohoedigital.gui.DDIconButtons;
import com.donohoedigital.gui.DDLabel;
import com.donohoedigital.gui.DDPanel;
import com.donohoedigital.gui.DDTextField;
import com.donohoedigital.gui.GuiUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Styled console output area shared by CommandRunnerPanel and WizardRunnerPanel: a JTextPane
 * in a scroll pane with stdout/stderr/system styling, clickable URL links, and helpers for
 * piping a process's output streams into it.
 */
public class RunnerConsole extends JPanel {

    private static final Object URL_KEY = new Object();
    // Match URLs but don't let trailing sentence punctuation (e.g. the period
    // ending "Deploy done to https://ddphotos.donohoe.info.") become part of
    // the clickable link - require the URL to end on a non-punctuation char.
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S*[^\\s.,;:!?)\\]}>'\"]");

    private final JTextPane outputPane_;
    private final JScrollPane scrollPane_;
    private final JLayeredPane layers_;
    private final SearchBar searchBar_;

    private SimpleAttributeSet stderrStyle_;
    private SimpleAttributeSet systemStyle_;
    private SimpleAttributeSet systemErrorStyle_;
    private SimpleAttributeSet linkBaseStyle_;

    // ── Find state ──────────────────────────────────────────────────────────────────
    private static final int OVERLAY_INSET = 6;
    private final Highlighter.HighlightPainter matchPainter_ =
            new DefaultHighlighter.DefaultHighlightPainter(StylesConfig.getColor("Console.searchMatch"));
    private final Highlighter.HighlightPainter currentPainter_ =
            new DefaultHighlighter.DefaultHighlightPainter(StylesConfig.getColor("Console.searchCurrent"));
    private final List<int[]> matchRanges_ = new ArrayList<>();
    private final List<Object> matchTags_ = new ArrayList<>();
    private int current_ = -1;
    // Document offset from which the next incremental match scan resumes, so streamed
    // output can be searched live without rescanning the whole document each append.
    private int searchFrom_ = 0;

    public RunnerConsole() {
        super(new BorderLayout());

        outputPane_ = buildOutputPane();
        scrollPane_ = new JScrollPane(outputPane_,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane_.setBorder(null);

        searchBar_ = new SearchBar();
        searchBar_.setVisible(false);

        // Float the search bar over the top-right of the output (iTerm/Chrome style)
        // without pushing the output down. A JLayeredPane keeps the scroll pane filling
        // the area while the bar sits above it on the PALETTE layer.
        layers_ = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();
                scrollPane_.setBounds(0, 0, w, h);
                if (searchBar_.isVisible()) {
                    // Keep the bar clear of the (always-on) vertical scrollbar on the right.
                    JScrollBar vbar = scrollPane_.getVerticalScrollBar();
                    int rightInset = OVERLAY_INSET + (vbar.isVisible() ? vbar.getWidth() : 0);
                    Dimension pref = searchBar_.getPreferredSize();
                    int bw = Math.min(pref.width, w - OVERLAY_INSET - rightInset);
                    int x = Math.max(OVERLAY_INSET, w - bw - rightInset);
                    searchBar_.setBounds(x, OVERLAY_INSET, bw, pref.height);
                }
            }
        };
        layers_.add(scrollPane_, JLayeredPane.DEFAULT_LAYER);
        layers_.add(searchBar_, JLayeredPane.PALETTE_LAYER);

        add(layers_, BorderLayout.CENTER);

        installFindKeyBindings();
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Construction
    // ──────────────────────────────────────────────────────────────────────────────

    private JTextPane buildOutputPane() {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setFocusable(true);

        Font font = StylesConfig.getFont("Console.jtextpane", new Font(Font.MONOSPACED, Font.PLAIN, 16));
        pane.setFont(font);

        // setFont() alone doesn't reach the StyledDocument — push into the default style
        Style defaultStyle = pane.getStyledDocument().getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setFontFamily(defaultStyle, font.getFamily());
        StyleConstants.setFontSize(defaultStyle, font.getSize());

        stderrStyle_ = new SimpleAttributeSet();
        StyleConstants.setForeground(stderrStyle_, StylesConfig.getColor("Console.error"));

        systemStyle_ = new SimpleAttributeSet();
        StyleConstants.setForeground(systemStyle_, StylesConfig.getColor("Console.system"));
        StyleConstants.setItalic(systemStyle_, true);

        systemErrorStyle_ = new SimpleAttributeSet();
        StyleConstants.setForeground(systemErrorStyle_, StylesConfig.getColor("Console.error"));
        StyleConstants.setItalic(systemErrorStyle_, true);

        linkBaseStyle_ = new SimpleAttributeSet();
        StyleConstants.setForeground(linkBaseStyle_, StylesConfig.getColor("Console.link"));
        StyleConstants.setUnderline(linkBaseStyle_, true);

        pane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String url = urlAt(pane, e.getPoint());
                if (url != null) Utils.openURL(url);
            }
        });
        pane.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                pane.setCursor(urlAt(pane, e.getPoint()) != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });

        return pane;
    }

    private String urlAt(JTextPane pane, Point p) {
        int pos = pane.viewToModel2D(p);
        if (pos < 0) return null;
        AttributeSet attrs = pane.getStyledDocument().getCharacterElement(pos).getAttributes();
        return (String) attrs.getAttribute(URL_KEY);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Appending text
    // ──────────────────────────────────────────────────────────────────────────────

    public void appendOutput(String text, boolean stderr) {
        boolean wasAtBottom = isAtBottom();
        insertWithLinks(text, stderr ? stderrStyle_ : null);
        liveRescan();
        if (wasAtBottom) scrollToBottom();
    }

    public void appendSystem(String text) {
        boolean wasAtBottom = isAtBottom();
        insert(text + "\n", systemStyle_);
        liveRescan();
        if (wasAtBottom) scrollToBottom();
    }

    public void appendSystemError(String text) {
        boolean wasAtBottom = isAtBottom();
        insert(text + "\n", systemErrorStyle_);
        liveRescan();
        if (wasAtBottom) scrollToBottom();
    }

    /**
     * A {@link CommandRunner.OutputSink} that writes to this console, marshaling every call to the
     * EDT (the kill path runs docker on a background thread). system→green, output→normal, error→red.
     */
    public CommandRunner.OutputSink asOutputSink() {
        return new CommandRunner.OutputSink() {
            public void system(String line) { SwingUtilities.invokeLater(() -> appendSystem(line)); }
            public void output(String text) { SwingUtilities.invokeLater(() -> appendOutput(text, false)); }
            public void error(String line)  { SwingUtilities.invokeLater(() -> appendSystemError(line)); }
        };
    }

    private void insertWithLinks(String text, AttributeSet baseStyle) {
        Matcher m = URL_PATTERN.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) insert(text.substring(last, m.start()), baseStyle);
            SimpleAttributeSet linkAttrs = new SimpleAttributeSet(linkBaseStyle_);
            linkAttrs.addAttribute(URL_KEY, m.group());
            insert(m.group(), linkAttrs);
            last = m.end();
        }
        if (last < text.length()) insert(text.substring(last), baseStyle);
    }

    private void insert(String text, AttributeSet style) {
        StyledDocument doc = outputPane_.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException e) {
            // ignore
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Stream pumping (run on a caller-owned background thread; appends marshal to the EDT)
    // ──────────────────────────────────────────────────────────────────────────────

    public void pumpStream(InputStream is, boolean stderr) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String text = line + "\n";
                SwingUtilities.invokeLater(() -> appendOutput(text, stderr));
            }
        } catch (IOException e) {
            // normal on process exit
        }
    }

    /**
     * Like pumpStream(), but also accumulates the output into the given StringBuffer
     * (used for prerequisite checks that need to inspect the captured output). stderr
     * lines are colored as errors but still captured so passed() sees the full output.
     * A StringBuffer (not StringBuilder) is used because stdout and stderr are pumped
     * on separate threads; its append() is internally synchronized.
     */
    public void pumpStreamCapturing(InputStream is, StringBuffer captured, boolean stderr) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String text = line + "\n";
                captured.append(text);
                SwingUtilities.invokeLater(() -> appendOutput(text, stderr));
            }
        } catch (IOException e) {
            // normal on process exit
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Scrolling / clearing
    // ──────────────────────────────────────────────────────────────────────────────

    public void clear() {
        outputPane_.setText("");
        clearMatches();
        searchBar_.updateCount();
    }

    /**
     * Prepare the console for a fresh run. A fresh console per run keeps output easy
     * to read; routing all run-time clears through here gives a single place to gate
     * this on a future preference.
     */
    public static void clearForRun(RunnerConsole console) {
        console.clear();
    }

    public void scrollToTop() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vbar = scrollPane_.getVerticalScrollBar();
            vbar.setValue(vbar.getMinimum());
        });
    }

    public void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vbar = scrollPane_.getVerticalScrollBar();
            vbar.setValue(vbar.getMaximum());
        });
    }

    private boolean isAtBottom() {
        JScrollBar vbar = scrollPane_.getVerticalScrollBar();
        BoundedRangeModel m = vbar.getModel();
        return m.getValue() + m.getExtent() >= m.getMaximum() - 5;
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Find (Cmd-F / Ctrl-F)
    // ──────────────────────────────────────────────────────────────────────────────

    /** Reveal the floating search bar, focus the query field and (re)run any existing query. */
    public void showSearch() {
        searchBar_.setVisible(true);
        layers_.revalidate();
        layers_.repaint();
        searchBar_.queryField_.requestFocusInWindow();
        searchBar_.queryField_.selectAll();
        recomputeMatches();
    }

    /** Hide the search bar, drop highlights and return focus to the output. */
    public void hideSearch() {
        searchBar_.setVisible(false);
        clearMatches();
        layers_.revalidate();
        layers_.repaint();
        outputPane_.requestFocusInWindow();
    }

    @SuppressWarnings("MagicConstant")
    private void installFindKeyBindings() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        // WHEN_IN_FOCUSED_WINDOW so Cmd/Ctrl-F works wherever focus sits in the panel;
        // only the visible tab's console reacts (each tab owns its own RunnerConsole).
        GuiUtils.addKeyAction(this, JComponent.WHEN_IN_FOCUSED_WINDOW, "console-find",
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (isShowing()) showSearch();
                    }
                }, KeyEvent.VK_F, menuMask);

        GuiUtils.addKeyAction(outputPane_, JComponent.WHEN_FOCUSED, "console-find-esc",
                keyAction(_ -> hideSearch()), KeyEvent.VK_ESCAPE, 0);
    }

    /** Full rescan from the top - used when the query or case option changes. */
    private void recomputeMatches() {
        clearMatches();
        String query = searchBar_.queryField_.getText();
        if (query == null || query.isEmpty()) {
            searchBar_.updateCount();
            return;
        }
        scanForMatches(true);
    }

    /**
     * Rescan the document tail from {@link #searchFrom_} onward and append any new matches.
     * Called both for the initial search and incrementally as output streams in, so only the
     * newly-added text is examined. {@code scrollToFirst} scrolls to the first match when one
     * first appears (wanted on an explicit search, not while output streams in).
     */
    private void scanForMatches(boolean scrollToFirst) {
        String query = searchBar_.queryField_.getText();
        if (query == null || query.isEmpty()) return;

        Document doc = outputPane_.getDocument();
        int docLen = doc.getLength();
        int from = Math.min(searchFrom_, docLen);
        int tailLen = docLen - from;
        if (tailLen <= 0) {
            searchBar_.updateCount();
            return;
        }

        String tail;
        try {
            tail = doc.getText(from, tailLen);
        } catch (BadLocationException e) {
            return;
        }

        boolean caseSensitive = searchBar_.caseToggle_.isSelected();
        String hay = caseSensitive ? tail : tail.toLowerCase();
        String needle = caseSensitive ? query : query.toLowerCase();

        Highlighter hl = outputPane_.getHighlighter();
        int rel = 0;
        int idx;
        int consumed = from;
        while ((idx = hay.indexOf(needle, rel)) >= 0) {
            int start = from + idx;
            int end = start + needle.length();
            try {
                Object tag = hl.addHighlight(start, end, matchPainter_);
                matchRanges_.add(new int[]{start, end});
                matchTags_.add(tag);
            } catch (BadLocationException e) {
                break;
            }
            rel = idx + needle.length();
            consumed = end;
        }

        // Don't rescan settled text, but keep a (needle-1) overlap so a match split across
        // this append and the next is still found when more output arrives.
        searchFrom_ = Math.max(0, Math.max(consumed, docLen - (needle.length() - 1)));

        if (current_ < 0 && !matchRanges_.isEmpty()) {
            selectMatch(0, scrollToFirst);
        } else {
            searchBar_.updateCount();
        }
    }

    /** Scan newly-appended output for matches when the search bar is open with a query. */
    private void liveRescan() {
        if (!searchBar_.isVisible()) return;
        String query = searchBar_.queryField_.getText();
        if (query != null && !query.isEmpty()) scanForMatches(false);
    }

    private void selectMatch(int idx, boolean scroll) {
        if (matchRanges_.isEmpty()) return;
        int n = matchRanges_.size();
        idx = ((idx % n) + n) % n; // wrap around in both directions

        Highlighter hl = outputPane_.getHighlighter();
        // Restore the previously-current match to the normal painter.
        if (current_ >= 0 && current_ < n) repaintMatch(hl, current_, matchPainter_);
        current_ = idx;
        repaintMatch(hl, current_, currentPainter_);

        if (scroll) {
            int[] r = matchRanges_.get(current_);
            try {
                Rectangle2D rect = outputPane_.modelToView2D(r[0]);
                if (rect != null) outputPane_.scrollRectToVisible(rect.getBounds());
            } catch (BadLocationException ignore) {
                // match no longer in document
            }
        }
        searchBar_.updateCount();
    }

    /** Re-add a single match's highlight with a different painter (Swing has no painter setter). */
    private void repaintMatch(Highlighter hl, int idx, Highlighter.HighlightPainter painter) {
        hl.removeHighlight(matchTags_.get(idx));
        int[] r = matchRanges_.get(idx);
        try {
            matchTags_.set(idx, hl.addHighlight(r[0], r[1], painter));
        } catch (BadLocationException ignore) {
            // match no longer in document
        }
    }

    private void nextMatch() {
        if (matchRanges_.isEmpty()) recomputeMatches();
        else selectMatch(current_ + 1, true);
    }

    private void prevMatch() {
        if (matchRanges_.isEmpty()) recomputeMatches();
        else selectMatch(current_ - 1, true);
    }

    private void clearMatches() {
        Highlighter hl = outputPane_.getHighlighter();
        for (Object tag : matchTags_) hl.removeHighlight(tag);
        matchTags_.clear();
        matchRanges_.clear();
        current_ = -1;
        searchFrom_ = 0;
    }

    private static AbstractAction keyAction(java.util.function.Consumer<ActionEvent> body) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                body.accept(e);
            }
        };
    }

    /**
     * Floating find bar: query field, match counter, case toggle and prev/next/close buttons.
     * Lives on the layered pane above the output; styled as an opaque chip so it reads over text.
     */
    private class SearchBar extends DDPanel {
        private static final String STYLE = "Options";

        private final DDTextField queryField_ = new DDTextField("findquery", STYLE);
        private final DDLabel countLabel_ = new DDLabel("findcount", STYLE);
        private final DDCheckBox caseToggle_ = new DDCheckBox("findcase", STYLE);

        SearchBar() {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setOpaque(true);
            setBackground(UIManager.getColor("Panel.background"));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(StylesConfig.getColor("Console.searchBorder")),
                    BorderFactory.createEmptyBorder(4, 8, 4, 6)));

            queryField_.setColumns(16);
            constrainHeight(queryField_);
            queryField_.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { recomputeMatches(); }
                @Override public void removeUpdate(DocumentEvent e) { recomputeMatches(); }
                @Override public void changedUpdate(DocumentEvent e) { recomputeMatches(); }
            });

            countLabel_.setPreferredSize(new Dimension(64, queryField_.getPreferredSize().height));
            countLabel_.setHorizontalAlignment(SwingConstants.CENTER);

            caseToggle_.addActionListener(_ -> recomputeMatches());

            DDButton prevBtn = DDIconButtons.iconButton("findprev", STYLE, DDIconButtons.CHEVRON_UP);
            DDButton nextBtn = DDIconButtons.iconButton("findnext", STYLE, DDIconButtons.CHEVRON_DOWN);
            DDButton closeBtn = DDIconButtons.iconButton("findclose", STYLE, DDIconButtons.CLOSE);
            prevBtn.addActionListener(_ -> prevMatch());
            nextBtn.addActionListener(_ -> nextMatch());
            closeBtn.addActionListener(_ -> hideSearch());

            // Key handling while typing in the query field.
            GuiUtils.addKeyAction(queryField_, JComponent.WHEN_FOCUSED, "find-next",
                    keyAction(_ -> nextMatch()), KeyEvent.VK_ENTER, 0);
            GuiUtils.addKeyAction(queryField_, JComponent.WHEN_FOCUSED, "find-prev",
                    keyAction(_ -> prevMatch()), KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);
            GuiUtils.addKeyAction(queryField_, JComponent.WHEN_FOCUSED, "find-down",
                    keyAction(_ -> nextMatch()), KeyEvent.VK_DOWN, 0);
            GuiUtils.addKeyAction(queryField_, JComponent.WHEN_FOCUSED, "find-up",
                    keyAction(_ -> prevMatch()), KeyEvent.VK_UP, 0);
            GuiUtils.addKeyAction(queryField_, JComponent.WHEN_FOCUSED, "find-esc",
                    keyAction(_ -> hideSearch()), KeyEvent.VK_ESCAPE, 0);

            add(queryField_);
            add(Box.createHorizontalStrut(6));
            add(countLabel_);
            add(Box.createHorizontalStrut(6));
            add(caseToggle_);
            add(Box.createHorizontalStrut(6));
            add(prevBtn);
            add(Box.createHorizontalStrut(2));
            add(nextBtn);
            add(Box.createHorizontalStrut(2));
            add(closeBtn);

            updateCount();
        }

        private void constrainHeight(JComponent c) {
            c.setMaximumSize(new Dimension(c.getMaximumSize().width, c.getPreferredSize().height));
        }

        void updateCount() {
            String query = queryField_.getText();
            if (query == null || query.isEmpty()) {
                countLabel_.setText("");
            } else if (matchRanges_.isEmpty()) {
                countLabel_.setText(PropertyConfig.getMessage("msg.find.noresults"));
            } else {
                countLabel_.setText(PropertyConfig.getMessage("msg.find.count",
                        current_ + 1, matchRanges_.size()));
            }
            // layers_ is null while the bar is built in the RunnerConsole constructor.
            if (layers_ != null) {
                layers_.revalidate();
                layers_.repaint();
            }
        }
    }
}
