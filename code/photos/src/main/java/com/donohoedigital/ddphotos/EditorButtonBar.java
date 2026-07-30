package com.donohoedigital.ddphotos;

import com.donohoedigital.gui.DDButton;
import com.donohoedigital.gui.DDPanel;

import javax.swing.*;
import java.awt.*;

/**
 * The Cancel / Save / Save &amp; Close / Close row shared by the standalone editor windows.  The
 * buttons sit in the EAST slot, leaving WEST free for whatever else the window needs down there.
 *
 * <p>Enablement comes from {@link #setDirty}: exactly one of Cancel / Close is ever live - Cancel
 * discards pending edits (and is pointless without them), Close simply leaves (and must not be the
 * button that loses them).
 */
class EditorButtonBar extends DDPanel {

    private final DDButton cancelBtn_;
    private final DDButton saveBtn_;
    private final DDButton saveCloseBtn_;
    private final DDButton closeBtn_;

    EditorButtonBar(String style, Runnable onCancel, Runnable onSave, Runnable onSaveClose, Runnable onClose) {
        setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        cancelBtn_    = button("cancel",    style, onCancel);
        saveBtn_      = button("save",      style, onSave);
        saveCloseBtn_ = button("saveclose", style, onSaveClose);
        closeBtn_     = button("close",     style, onClose);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        actions.add(cancelBtn_);
        actions.add(saveBtn_);
        actions.add(saveCloseBtn_);
        actions.add(closeBtn_);
        add(actions, BorderLayout.EAST);
    }

    /** Points the buttons at the window's pending-changes state. */
    void setDirty(boolean dirty) {
        cancelBtn_.setEnabled(dirty);
        saveBtn_.setEnabled(dirty);
        saveCloseBtn_.setEnabled(dirty);
        closeBtn_.setEnabled(!dirty);
    }

    private static DDButton button(String name, String style, Runnable action) {
        DDButton btn = new DDButton(name, style);
        btn.addActionListener(_ -> action.run());
        return btn;
    }
}
