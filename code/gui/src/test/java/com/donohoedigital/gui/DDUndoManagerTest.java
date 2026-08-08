package com.donohoedigital.gui;

import org.junit.Before;
import org.junit.Test;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers {@link DDUndoManager}'s grouping rules - the part that turns Swing's one-edit-per-keystroke
 * stream into undo steps a person would recognize.
 *
 * <p>Uses a bare {@link JTextArea} and the test constructor rather than
 * {@link DDUndoManager#install}, so nothing here needs the styles config a real DD widget loads.
 * Edits are made straight against the document, which is what typing ends up doing anyway.
 *
 * <p>Grouping is driven by a fake clock the test advances by hand, never by sleeping.  Real
 * elapsed time would make every "these collapse into one undo" assertion a race: the edits have
 * to land within 700ms of each other (100ms for the replace case), and a GC pause or a busy
 * machine is enough to blow that and split the run.  With the clock under test control the rules
 * are asserted exactly, and the suite does not spend seconds asleep.
 */
public class DDUndoManagerTest
{
    /** Longer than DDUndoManager's 700ms group pause. */
    private static final long PAUSE = 800;

    /** Longer than its 100ms replace window, but well short of the group pause. */
    private static final long BLIP = 200;

    private JTextArea text_;
    private Document doc_;
    private DDUndoManager undo_;

    /** "Now" as far as the manager under test is concerned; only ever moved by {@link #advance}. */
    private long now_;

    @Before
    public void setUp()
    {
        text_ = new JTextArea();
        doc_ = text_.getDocument();
        now_ = 0;
        undo_ = new DDUndoManager(text_, () -> now_);
    }

    private void type(String s) throws BadLocationException
    {
        doc_.insertString(doc_.getLength(), s, null);
    }

    /** Move the clock forward, standing in for the user pausing before their next keystroke. */
    private void advance(long millis)
    {
        now_ += millis;
    }

    @Test
    public void typingRunIsOneUndo() throws BadLocationException
    {
        type("S");
        type("u");
        type("n");

        assertTrue(undo_.canUndo());
        undo_.undo();
        assertEquals("", text_.getText());
        assertFalse(undo_.canUndo());
    }

    @Test
    public void pauseStartsANewUndo() throws BadLocationException
    {
        type("Sunset");
        advance(PAUSE);
        type(" over Lake");

        undo_.undo();
        assertEquals("Sunset", text_.getText());
        undo_.undo();
        assertEquals("", text_.getText());
    }

    @Test
    public void nonAdjacentInsertStartsANewUndo() throws BadLocationException
    {
        type("Lake");
        doc_.insertString(0, "The ", null); // jumped to the front

        undo_.undo();
        assertEquals("Lake", text_.getText());
        undo_.undo();
        assertEquals("", text_.getText());
    }

    @Test
    public void backspaceRunIsOneUndo() throws BadLocationException
    {
        type("Sunset");
        advance(PAUSE);

        // backspace three times: each removal walks the caret back one
        doc_.remove(5, 1);
        doc_.remove(4, 1);
        doc_.remove(3, 1);
        assertEquals("Sun", text_.getText());

        undo_.undo();
        assertEquals("Sunset", text_.getText());
    }

    @Test
    public void deletingThenTypingLaterAreSeparateUndos() throws BadLocationException
    {
        type("Sunset");
        advance(PAUSE);
        doc_.remove(3, 3);
        advance(BLIP); // past the replace-selection window, so this is a deliberate second action
        type("day");
        assertEquals("Sunday", text_.getText());

        undo_.undo();
        assertEquals("Sun", text_.getText());
        undo_.undo();
        assertEquals("Sunset", text_.getText());
    }

    @Test
    public void typingOverASelectionIsOneUndo() throws BadLocationException
    {
        type("Sunset");
        advance(PAUSE);

        // what AbstractDocument.replace() does for replaceSelection/paste: remove then insert
        doc_.remove(3, 3);
        doc_.insertString(3, "day", null);
        assertEquals("Sunday", text_.getText());

        undo_.undo();
        assertEquals("Sunset", text_.getText());
    }

    @Test
    public void redoRestoresAnUndoneRun() throws BadLocationException
    {
        type("Sunset");
        advance(PAUSE);
        type(" over Lake");

        undo_.undo();
        assertEquals("Sunset", text_.getText());

        assertTrue(undo_.canRedo());
        undo_.redo();
        assertEquals("Sunset over Lake", text_.getText());
        assertFalse(undo_.canRedo());
    }

    @Test
    public void runSilentRecordsNothingAndClearsHistory() throws BadLocationException
    {
        type("typed by hand");
        assertTrue(undo_.canUndo());

        undo_.runSilent(() -> text_.setText("loaded from a record"));

        assertFalse(undo_.canUndo());
        assertFalse(undo_.canRedo());
        assertEquals("loaded from a record", text_.getText());
    }

    @Test
    public void attachToFollowsANewDocumentAndDropsHistory() throws BadLocationException
    {
        type("typed by hand");
        assertTrue(undo_.canUndo());

        // what the widgets' setDocument override does for setTextLengthLimit
        Document old = doc_;
        text_.setDocument(new javax.swing.text.PlainDocument());
        undo_.attachTo(old, text_.getDocument());
        assertFalse(undo_.canUndo());

        doc_ = text_.getDocument();
        type("in the new document");
        assertTrue(undo_.canUndo());
        undo_.undo();
        assertEquals("", text_.getText());
    }
}
