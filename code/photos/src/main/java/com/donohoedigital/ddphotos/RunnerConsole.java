package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Utils;
import com.donohoedigital.config.StylesConfig;
import com.donohoedigital.ddphotos.runner.CommandRunner;
import com.donohoedigital.gui.TextFindSupport;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Styled console output area shared by CommandRunnerPanel and WizardRunnerPanel: a JTextPane
 * in a scroll pane with stdout/stderr/system styling, clickable URL links, find (via
 * {@link TextFindSupport}), and helpers for piping a process's output streams into it.
 */
public class RunnerConsole extends JPanel {

    private static final Object URL_KEY = new Object();
    // Match URLs but don't let trailing sentence punctuation (e.g. the period
    // ending "Deploy done to https://ddphotos.donohoe.info.") become part of
    // the clickable link - require the URL to end on a non-punctuation char.
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S*[^\\s.,;:!?)\\]}>'\"]");

    private final JTextPane outputPane_;
    private final JScrollPane scrollPane_;
    private final TextFindSupport find_;

    private SimpleAttributeSet stderrStyle_;
    private SimpleAttributeSet systemStyle_;
    private SimpleAttributeSet systemErrorStyle_;
    private SimpleAttributeSet linkBaseStyle_;

    public RunnerConsole() {
        super(new BorderLayout());

        outputPane_ = buildOutputPane();
        scrollPane_ = new JScrollPane(outputPane_,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane_.setBorder(null);

        find_ = new TextFindSupport(outputPane_, scrollPane_, "Options");
        add(find_.getComponent(), BorderLayout.CENTER);
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
        find_.onTextAppended();
        if (wasAtBottom) scrollToBottom();
    }

    public void appendSystem(String text) {
        boolean wasAtBottom = isAtBottom();
        insert(text + "\n", systemStyle_);
        find_.onTextAppended();
        if (wasAtBottom) scrollToBottom();
    }

    public void appendSystemError(String text) {
        boolean wasAtBottom = isAtBottom();
        insert(text + "\n", systemErrorStyle_);
        find_.onTextAppended();
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
    // Process output (see ProcessPump; handlers run on its reader threads)
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * A {@link ProcessPump} line handler: strips ANSI codes from each line and queues it for the
     * console, colored as an error if {@code stderr}.  Then hands the stripped line to
     * {@code observer} (may be null) - used to capture a prerequisite check's output and to spot
     * the preview-server URL a command announces.  The observer runs on the reader thread, after
     * the console append has been queued, so anything it queues of its own lands in the console
     * in the order it happened.
     */
    public Consumer<String> lineHandler(boolean stderr, Consumer<String> observer) {
        return line -> {
            String stripped = PhotosUtils.stripAnsi(line);
            SwingUtilities.invokeLater(() -> appendOutput(stripped + "\n", stderr));
            if (observer != null) observer.accept(stripped);
        };
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Scrolling / clearing
    // ──────────────────────────────────────────────────────────────────────────────

    public void clear() {
        outputPane_.setText("");
        find_.clear();
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
        find_.showSearch();
    }
}
