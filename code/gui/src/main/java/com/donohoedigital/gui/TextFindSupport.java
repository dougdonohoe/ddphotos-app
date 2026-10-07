package com.donohoedigital.gui;

import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.config.StylesConfig;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Find (Cmd-F / Ctrl-F) for a read-only text component in a scroll pane. Floats a search bar
 * over the top-right of the scroll pane (iTerm/Chrome style) and highlights every match.
 * Add {@link #getComponent()} to the UI in place of the scroll pane.
 * <p>
 * For a read-only component, owners that append text should call {@link #onTextAppended()} so
 * streamed output is searched live, and owners that replace or clear the text should call
 * {@link #clear()}.  For an editable component, matches are recomputed after each edit.
 */
public class TextFindSupport {

    private static final int OVERLAY_INSET = 6;

    private final JTextComponent text_;
    private final JScrollPane scrollPane_;
    private final JLayeredPane layers_;
    private final SearchBar searchBar_;

    private final Highlighter.HighlightPainter matchPainter_ =
            new DefaultHighlighter.DefaultHighlightPainter(StylesConfig.getColor("Find.match"));
    private final Highlighter.HighlightPainter currentPainter_ =
            new DefaultHighlighter.DefaultHighlightPainter(StylesConfig.getColor("Find.current"));
    private final List<int[]> matchRanges_ = new ArrayList<>();
    private final List<Object> matchTags_ = new ArrayList<>();
    private int current_ = -1;
    // Document offset from which the next incremental match scan resumes, so streamed
    // output can be searched live without rescanning the whole document each append.
    private int searchFrom_;
    // Coalesces the rescans queued by a burst of edits (setText fires a remove and an insert).
    private boolean editRescanPending_;

    /**
     * @param text   the text component to search (normally the scroll pane's view)
     * @param scroll the scroll pane holding {@code text}
     * @param style  style name for the search bar's widgets
     */
    public TextFindSupport(JTextComponent text, JScrollPane scroll, String style) {
        text_ = text;
        scrollPane_ = scroll;

        // A JLayeredPane keeps the scroll pane filling the area while the bar sits above
        // it on the PALETTE layer, so the bar doesn't push the text down.
        layers_ = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();
                scrollPane_.setBounds(0, 0, w, h);
                if (searchBar_.isVisible()) {
                    // Keep the bar clear of the vertical scrollbar on the right.
                    JScrollBar vbar = scrollPane_.getVerticalScrollBar();
                    int rightInset = OVERLAY_INSET + (vbar.isVisible() ? vbar.getWidth() : 0);
                    Dimension pref = searchBar_.getPreferredSize();
                    int bw = Math.min(pref.width, w - OVERLAY_INSET - rightInset);
                    int x = Math.max(OVERLAY_INSET, w - bw - rightInset);
                    searchBar_.setBounds(x, OVERLAY_INSET, bw, pref.height);
                }
            }
        };

        searchBar_ = new SearchBar(style);
        searchBar_.setVisible(false);

        layers_.add(scrollPane_, JLayeredPane.DEFAULT_LAYER);
        layers_.add(searchBar_, JLayeredPane.PALETTE_LAYER);

        installKeyBindings();
        text_.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onEdited(); }
            @Override public void removeUpdate(DocumentEvent e) { onEdited(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
    }

    /** The scroll pane with the floating search bar over it; add this to the UI. */
    public JComponent getComponent() {
        return layers_;
    }

    /** Reveal the floating search bar, focus the query field and (re)run any existing query. */
    public void showSearch() {
        searchBar_.setVisible(true);
        layers_.revalidate();
        layers_.repaint();
        searchBar_.queryField_.requestFocusInWindow();
        searchBar_.queryField_.selectAll();
        recomputeMatches();
    }

    /**
     * Hide the search bar, drop highlights and return focus to the text.  The current match, if
     * any, is left selected so the caret is where the search ended.
     */
    public void hideSearch() {
        int[] current = current_ >= 0 && current_ < matchRanges_.size() ? matchRanges_.get(current_) : null;
        searchBar_.setVisible(false);
        clearMatches();
        if (current != null && current[1] <= text_.getDocument().getLength()) {
            text_.select(current[0], current[1]);
        }
        layers_.revalidate();
        layers_.repaint();
        text_.requestFocusInWindow();
    }

    /**
     * Drop all matches. Call after the text is replaced or cleared. If the search bar is open,
     * the query is rerun against the new text.
     */
    public void clear() {
        clearMatches();
        if (searchBar_.isVisible()) recomputeMatches();
        else searchBar_.updateCount();
    }

    /** Scan newly-appended text for matches when the search bar is open with a query. */
    public void onTextAppended() {
        if (!searchBar_.isVisible()) return;
        String query = searchBar_.queryField_.getText();
        if (query != null && !query.isEmpty()) scanForMatches(false);
    }

    /**
     * The user edited the text, so the stored match offsets may be stale.  Rescan after the
     * document lock is released, keeping the current match near the caret rather than jumping
     * the view back to the first match.  Read-only text changes only through the owner, which
     * calls {@link #onTextAppended()} or {@link #clear()} instead.
     */
    private void onEdited() {
        if (!text_.isEditable() || !searchBar_.isVisible() || editRescanPending_) return;
        editRescanPending_ = true;
        SwingUtilities.invokeLater(() -> {
            editRescanPending_ = false;
            if (!searchBar_.isVisible()) return;
            clearMatches();
            scanForMatches(false);
            int caret = text_.getCaretPosition();
            for (int i = 0; i < matchRanges_.size(); i++) {
                if (matchRanges_.get(i)[1] >= caret) {
                    selectMatch(i, false);
                    break;
                }
            }
        });
    }

    @SuppressWarnings("MagicConstant")
    private void installKeyBindings() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        // WHEN_IN_FOCUSED_WINDOW so Cmd/Ctrl-F works wherever focus sits in the window;
        // only a showing instance reacts (e.g. the visible tab when each tab has its own).
        GuiUtils.addKeyAction(layers_, JComponent.WHEN_IN_FOCUSED_WINDOW, "text-find",
                keyAction(_ -> {
                    if (layers_.isShowing()) showSearch();
                }), KeyEvent.VK_F, menuMask);

        GuiUtils.addKeyAction(text_, JComponent.WHEN_FOCUSED, "text-find-esc",
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
     * Called both for the initial search and incrementally as text is appended, so only the
     * newly-added text is examined. {@code scrollToFirst} scrolls to the first match when one
     * first appears (wanted on an explicit search, not while output streams in).
     */
    private void scanForMatches(boolean scrollToFirst) {
        String query = searchBar_.queryField_.getText();
        if (query == null || query.isEmpty()) return;

        Document doc = text_.getDocument();
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

        Highlighter hl = text_.getHighlighter();
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
        // this append and the next is still found when more text arrives.
        searchFrom_ = Math.max(0, Math.max(consumed, docLen - (needle.length() - 1)));

        if (current_ < 0 && !matchRanges_.isEmpty()) {
            selectMatch(0, scrollToFirst);
        } else {
            searchBar_.updateCount();
        }
    }

    private void selectMatch(int idx, boolean scroll) {
        if (matchRanges_.isEmpty()) return;
        int n = matchRanges_.size();
        idx = ((idx % n) + n) % n; // wrap around in both directions

        Highlighter hl = text_.getHighlighter();
        // Restore the previously-current match to the normal painter.
        if (current_ >= 0 && current_ < n) repaintMatch(hl, current_, matchPainter_);
        current_ = idx;
        repaintMatch(hl, current_, currentPainter_);

        if (scroll) {
            int[] r = matchRanges_.get(current_);
            try {
                Rectangle2D rect = text_.modelToView2D(r[0]);
                if (rect != null) text_.scrollRectToVisible(rect.getBounds());
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
        Highlighter hl = text_.getHighlighter();
        for (Object tag : matchTags_) hl.removeHighlight(tag);
        matchTags_.clear();
        matchRanges_.clear();
        current_ = -1;
        searchFrom_ = 0;
    }

    private static AbstractAction keyAction(Consumer<ActionEvent> body) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                body.accept(e);
            }
        };
    }

    /**
     * Floating find bar: query field, match counter, case toggle and prev/next/close buttons.
     * Lives on the layered pane above the text; styled as an opaque chip so it reads over text.
     */
    private class SearchBar extends DDPanel {
        private final DDTextField queryField_;
        private final DDLabel countLabel_;
        private final DDCheckBox caseToggle_;

        SearchBar(String style) {
            queryField_ = new DDTextField("findquery", style);
            countLabel_ = new DDLabel("findcount", style);
            caseToggle_ = new DDCheckBox("findcase", style);

            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setOpaque(true);
            setBackground(UIManager.getColor("Panel.background"));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(StylesConfig.getColor("Find.border")),
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

            DDButton prevBtn = DDIconButtons.iconButton("findprev", style, DDIconButtons.CHEVRON_UP);
            DDButton nextBtn = DDIconButtons.iconButton("findnext", style, DDIconButtons.CHEVRON_DOWN);
            DDButton closeBtn = DDIconButtons.iconButton("findclose", style, DDIconButtons.CLOSE);
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
            layers_.revalidate();
            layers_.repaint();
        }
    }
}
