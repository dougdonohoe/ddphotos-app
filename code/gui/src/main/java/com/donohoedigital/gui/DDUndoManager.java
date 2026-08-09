/*
 * DDUndoManager.java
 *
 * Created on July 27, 2026
 */

package com.donohoedigital.gui;

import com.donohoedigital.base.Utils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.LongSupplier;

/**
 * Undo/redo for a single text widget.  Swing gives us the raw machinery - every
 * {@link AbstractDocument} fires an edit per insert/remove and {@link UndoManager} stacks them -
 * but nothing above it, so this class adds the three things that make undo usable:
 *
 * <ul>
 * <li>a run of typing collapses into one undo step, rather than one step per character</li>
 * <li>a programmatic {@link JTextComponent#setText} resets the history instead of landing on it,
 *     so loading a record into a form can't be undone back to the previous record's text</li>
 * <li>Cmd-Z / Ctrl-Z bindings, which {@code DefaultEditorKit} does not supply</li>
 * </ul>
 *
 * One instance per widget: undo in a field rewinds that field only.  Created by
 * {@link #install} from {@link DDTextField} and {@link DDTextArea}; the manager is stashed on the
 * widget as a client property so a menu can find the focused widget's stack (see
 * {@link #forFocusOwner}).
 *
 * @author Doug Donohoe
 */
public class DDUndoManager extends UndoManager implements UndoableEditListener
{
    /** Where a widget's manager hangs off the widget itself - see {@link #get} */
    private static final String CLIENT_PROPERTY = "dd.undo";

    /** Typing keeps extending the same undo group until a pause this long */
    private static final long GROUP_PAUSE_MILLIS = 700;

    /**
     * Typing over a selection arrives as a remove immediately followed by an insert (see
     * AbstractDocument.replace), which is one action to the user.  A direction flip this close
     * together is treated as part of the same group so one undo takes back the whole paste.
     */
    private static final long REPLACE_MILLIS = 100;

    /** Roughly 250 typing bursts, comfortably more than the default 100 for a whole-file editor */
    private static final int EDIT_LIMIT = 250;

    private static final String ACTION_UNDO = "dd-undo";
    private static final String ACTION_REDO = "dd-redo";

    private final JTextComponent text_;

    /**
     * Where the grouping rules read "now" from, in milliseconds.  Monotonic rather than
     * {@link System#currentTimeMillis} so a wall-clock adjustment - an NTP step, or the guest
     * clock resyncing after a host suspend, which is routine under WSL - cannot make a typing
     * run look like it paused and split it in two.  Only differences are ever used, so the
     * arbitrary origin of {@link System#nanoTime} does not matter.
     */
    private final LongSupplier clock_;

    // the group being accumulated, and what would let the next edit join it
    private CompoundEdit group_;
    private boolean groupInsert_;
    private int groupEnd_;
    private long groupMillis_;

    // set while a programmatic setText runs, and while we are applying an undo/redo
    private boolean suspended_;
    private boolean applying_;

    /**
     * Listen to the widget's current document.  Deliberately free of any {@link GuiUtils} use so
     * this can be built in a test without the styles config a real widget needs - {@link #install}
     * is the entry point widgets use.
     */
    public DDUndoManager(JTextComponent text)
    {
        this(text, () -> System.nanoTime() / 1_000_000L);
    }

    /**
     * Visible for testing: drives the grouping rules from the supplied clock instead of elapsed
     * real time, so a test can assert what does and does not collapse into one undo step by
     * advancing the clock rather than sleeping.
     */
    DDUndoManager(JTextComponent text, LongSupplier clock)
    {
        text_ = text;
        clock_ = clock;
        setLimit(EDIT_LIMIT);

        Document doc = text.getDocument();
        if (doc != null) doc.addUndoableEditListener(this);

        text.putClientProperty(CLIENT_PROPERTY, this);
    }

    /**
     * Attach a manager to a widget and bind the platform undo/redo keys to it.
     */
    public static DDUndoManager install(JTextComponent text)
    {
        DDUndoManager mgr = new DDUndoManager(text);

        GuiUtils.addKeyAction(text, JComponent.WHEN_FOCUSED, ACTION_UNDO, mgr.undoAction_,
                              KeyEvent.VK_Z, GuiUtils.MENU_SHORTCUT_MASK);

        GuiUtils.addKeyAction(text, JComponent.WHEN_FOCUSED, ACTION_REDO, mgr.redoAction_,
                              KeyEvent.VK_Z, GuiUtils.MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK);

        // Windows/Linux users reach for Ctrl-Y as well as Ctrl-Shift-Z
        if (!Utils.ISMAC)
        {
            GuiUtils.addKeyAction(text, JComponent.WHEN_FOCUSED, ACTION_REDO, mgr.redoAction_,
                                  KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
        }

        return mgr;
    }

    /**
     * The manager for a widget, or null if it has none.
     */
    public static DDUndoManager get(Component c)
    {
        return (c instanceof JComponent j && j.getClientProperty(CLIENT_PROPERTY) instanceof DDUndoManager mgr)
                ? mgr : null;
    }

    /**
     * The manager for whichever text widget the user is typing in, or null if that isn't one.
     * Permanent focus owner rather than current: a pulled-down menu holds focus temporarily, and
     * this is called from menu actions.
     */
    public static DDUndoManager forFocusOwner()
    {
        return get(KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner());
    }

    /**
     * Follow the widget to a new document, dropping the history - a new document means new
     * content, so there is nothing meaningful to rewind to.  Called from the widgets'
     * setDocument overrides, which is what keeps undo working after setTextLengthLimit.
     */
    public void attachTo(Document old, Document neu)
    {
        if (old != null) old.removeUndoableEditListener(this);
        if (neu != null) neu.addUndoableEditListener(this);
        discardAllEdits();
    }

    /**
     * Run a programmatic change without recording it, then start over from the result.  Used by
     * setText: it means "this is the value now", so undoing back past it would rewind to a record
     * the user is no longer looking at.
     */
    public void runSilent(Runnable r)
    {
        endGroup();
        suspended_ = true;
        try
        {
            r.run();
        }
        finally
        {
            suspended_ = false;
            discardAllEdits();
        }
    }

    ////
    //// Recording
    ////

    public void undoableEditHappened(UndoableEditEvent e)
    {
        if (suspended_ || applying_) return;

        UndoableEdit edit = e.getEdit();

        if (edit instanceof AbstractDocument.DefaultDocumentEvent dde)
        {
            // An attribute-only change (styled documents) moves no text of its own, so on its own
            // it would be an undo step that appears to do nothing.  Fold it into the open group.
            if (dde.getType() == DocumentEvent.EventType.CHANGE)
            {
                if (group_ != null) group_.addEdit(edit);
                else super.addEdit(edit);
                return;
            }

            boolean insert = dde.getType() == DocumentEvent.EventType.INSERT;
            int offset = dde.getOffset();
            int length = dde.getLength();

            if (!continuesGroup(insert, offset, length))
            {
                endGroup();
                group_ = new CompoundEdit();
            }
            group_.addEdit(edit);

            groupInsert_ = insert;
            groupEnd_ = insert ? offset + length : offset; // where this edit left the caret
            groupMillis_ = clock_.getAsLong();
            return;
        }

        // anything we can't classify stands alone
        endGroup();
        super.addEdit(edit);
    }

    /**
     * Whether an edit is a continuation of the typing run already being accumulated: it has to be
     * going the same direction, pick up where the last one left off, and be soon enough after it.
     */
    private boolean continuesGroup(boolean insert, int offset, int length)
    {
        if (group_ == null) return false;

        long elapsed = clock_.getAsLong() - groupMillis_;
        if (elapsed >= GROUP_PAUSE_MILLIS) return false;

        if (groupInsert_ != insert)
        {
            // only the remove-then-insert of typing/pasting over a selection may flip direction
            return !groupInsert_ && offset == groupEnd_ && elapsed < REPLACE_MILLIS;
        }

        // typing extends the run at its end; backspace (offset + length) and forward delete
        // (offset) both leave the caret where the previous removal left it
        return insert ? offset == groupEnd_
                      : offset == groupEnd_ || offset + length == groupEnd_;
    }

    /**
     * Close the open typing run and hand it to the manager as a single undoable step.
     */
    private void endGroup()
    {
        if (group_ == null) return;

        CompoundEdit group = group_;
        group_ = null;
        group.end();
        if (group.isSignificant()) super.addEdit(group);
    }

    ////
    //// UndoManager
    ////

    /**
     * The run still being typed is undoable even though it hasn't been handed over yet.
     */
    @Override
    public synchronized boolean canUndo()
    {
        return group_ != null || super.canUndo();
    }

    @Override
    public synchronized void undo()
    {
        endGroup();
        applying_ = true;
        try
        {
            super.undo();
        }
        finally
        {
            applying_ = false;
        }
    }

    @Override
    public synchronized void redo()
    {
        endGroup();
        applying_ = true;
        try
        {
            super.redo();
        }
        finally
        {
            applying_ = false;
        }
    }

    @Override
    public synchronized void discardAllEdits()
    {
        group_ = null;
        super.discardAllEdits();
    }

    ////
    //// Actions
    ////

    private final Action undoAction_ = new AbstractAction()
    {
        public void actionPerformed(ActionEvent e)
        {
            if (!isEditable()) return;

            if (canUndo()) undo();
            else UIManager.getLookAndFeel().provideErrorFeedback(text_);
        }
    };

    private final Action redoAction_ = new AbstractAction()
    {
        public void actionPerformed(ActionEvent e)
        {
            if (!isEditable()) return;

            if (canRedo()) redo();
            else UIManager.getLookAndFeel().provideErrorFeedback(text_);
        }
    };

    /**
     * Undo the focused widget's last change - for menu items as well as the key binding.
     */
    public Action getUndoAction()
    {
        return undoAction_;
    }

    /**
     * Redo the focused widget's last undone change.
     */
    public Action getRedoAction()
    {
        return redoAction_;
    }

    /**
     * A read-only or disabled widget has nothing to undo into.
     */
    private boolean isEditable()
    {
        return text_.isEditable() && text_.isEnabled();
    }
}
