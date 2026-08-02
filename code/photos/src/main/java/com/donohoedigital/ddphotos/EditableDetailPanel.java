package com.donohoedigital.ddphotos;

import com.donohoedigital.config.DataElement;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.gui.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

abstract class EditableDetailPanel extends DDPanel {

    protected static final String STYLE = "Options";
    protected static final String STYLE_ERROR = "OptionsError";

    private final String sEditButtonName;

    public EditableDetailPanel(String sEditButtonName) {
        this.sEditButtonName = sEditButtonName;
    }

    /** Combo key for the "no base" entry; its display text comes from {@code combobox.base.none}. */
    protected static final String NONE_BASE = "";

    protected List<DDValidatable> validatables_;
    protected DDButton editBtn_, saveBtn_, cancelBtn_;

    /** Created by {@link #buildPasswordRow}; null until then. */
    protected DDButton passwordBtn_;
    protected DDLabel  lockLabel_;

    /** The fields {@link #setReadOnly} drives, in registration order - see {@link #editable}. */
    private final List<DDDisplayOnly> editFields_ = new ArrayList<>();

    private boolean editing_;
    private Runnable onEditModeChanged_;

    /**
     * Registers a field with the read-only toggle and hands it back, so subclasses can
     * wrap it where it's built rather than repeating the list in a separate method:
     *
     * <pre>siteName_ = editable(new OptionText(null, "sitename", STYLE, ...));</pre>
     *
     * Anything not registered - labels, previews, warning areas - is left alone by
     * {@link #setReadOnly}.
     */
    protected <T extends DDDisplayOnly> T editable(T field) {
        editFields_.add(field);
        return field;
    }

    /**
     * Notified whenever editing starts, is saved, or is canceled (i.e. whenever
     * {@link #isEditing()} has changed), so callers can lock other UI
     * while an edit is in progress.
     */
    public void setOnEditModeChanged(Runnable listener) {
        onEditModeChanged_ = listener;
    }

    public final boolean isEditing() {
        return editing_;
    }

    /**
     * The single point at which edit mode changes.  Subclasses must never touch the
     * flag directly: routing every transition (edit / save / cancel / reload) through
     * here keeps the read-only fields, the buttons and anything else derived from the
     * mode in sync, and fires the listener exactly once per actual change.
     */
    protected final void setEditing(boolean editing) {
        boolean changed = (editing_ != editing);
        editing_ = editing;
        setReadOnly(!editing);
        checkButtons();
        if (changed && onEditModeChanged_ != null) onEditModeChanged_.run();
    }

    protected void finishBuildUI(JPanel form) {
        finishBuildUI(form, null);
    }

    protected void finishBuildUI(JPanel form, Component extraSouth) {
        validatables_ = new ArrayList<>();
        GuiUtils.getValidatables(form, validatables_);
        validatables_.forEach(v -> v.addValidationListener(this::checkButtons));

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        editBtn_   = new DDButton(sEditButtonName, STYLE);
        saveBtn_   = new DDButton("save",        STYLE);
        cancelBtn_ = new DDButton("cancel",      STYLE);
        editBtn_.addActionListener(_   -> enterEditMode());
        saveBtn_.addActionListener(_   -> applyAndSave());
        cancelBtn_.addActionListener(_ -> cancelEdit());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        buttonRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        buttonRow.add(editBtn_);
        buttonRow.add(saveBtn_);
        buttonRow.add(cancelBtn_);

        if (extraSouth != null) {
            JPanel centerPanel = new JPanel(new BorderLayout());
            centerPanel.add(scroll,     BorderLayout.CENTER);
            centerPanel.add(buttonRow,  BorderLayout.SOUTH);
            add(centerPanel,  BorderLayout.CENTER);
            add(extraSouth,   BorderLayout.SOUTH);
        } else {
            add(scroll,     BorderLayout.CENTER);
            add(buttonRow,  BorderLayout.SOUTH);
        }
    }

    protected abstract void checkButtons();
    protected abstract void enterEditMode();
    protected abstract void applyAndSave();
    protected abstract void cancelEdit();

    /** Puts the registered fields (and the edit/save/cancel buttons) into read-only or editable state. */
    private void setReadOnly(boolean readOnly) {
        editFields_.forEach(f -> f.setDisplayOnly(readOnly));

        editBtn_.setVisible(readOnly);
        saveBtn_.setVisible(!readOnly);
        cancelBtn_.setVisible(!readOnly);
    }

    /** Opens {@link PasswordDialog} for whatever this panel is editing. */
    protected abstract void openPasswordDialog();

    // -------------------------------------------------------------------------
    // Shared layout helpers
    // -------------------------------------------------------------------------

    /** A titled section whose children stack vertically, one per row. */
    protected static DDLabelBorder section(String name) {
        return section(name, new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 4, VerticalFlowLayout.FILL));
    }

    /** A titled section laid out as a grid - see {@link GridBagForm#detail}. */
    protected static DDLabelBorder gridSection(String name) {
        return section(name, new GridBagLayout());
    }

    private static DDLabelBorder section(String name, LayoutManager layout) {
        DDLabelBorder panel = new DDLabelBorder(name, STYLE);
        panel.setLayout(layout);
        panel.setBorder(BorderFactory.createCompoundBorder(
                panel.getBorder(),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return panel;
    }

    /**
     * A row pairing a fixed-width leading component with whatever should sit beside it:
     * the leading component keeps its preferred width in WEST, the rest of the row goes
     * to the component in CENTER.
     */
    protected static DDPanel westCenterRow(JComponent west, JComponent center) {
        DDPanel row = new DDPanel();
        row.setBorderLayoutGap(0, 8);
        row.add(west,   BorderLayout.WEST);
        row.add(center, BorderLayout.CENTER);
        return row;
    }

    /**
     * The given field with the password controls beside it: a button that opens
     * {@link PasswordDialog} and a lock icon reporting whether the thing being edited
     * is protected.  Both controls are kept in {@link #passwordBtn_} / {@link #lockLabel_}
     * for the subclass' own {@code updatePasswordUi()} to drive.
     */
    protected JComponent buildPasswordRow(JComponent field, String buttonName, String lockName) {
        passwordBtn_ = new DDButton(buttonName, STYLE);
        passwordBtn_.addActionListener(_ -> openPasswordDialog());

        lockLabel_ = new DDLabel(lockName, STYLE);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.add(passwordBtn_);
        controls.add(lockLabel_);

        return westCenterRow(field, controls);
    }

    /** Points the lock icon at its locked/unlocked state and explains it in the tooltip. */
    protected void setLockState(boolean locked, String tooltipKey) {
        lockLabel_.setIcon(locked ? DDIconButtons.LOCK : DDIconButtons.UNLOCK);
        lockLabel_.setToolTipText(PropertyConfig.getMessage(tooltipKey));
    }

    /** Widest a base combo may get before it stops growing with its content. */
    private static final int BASE_COMBO_MAX_WIDTH = 380;
    /** Narrowest a base combo may be squeezed to; below this the path text is unreadable. */
    private static final int BASE_COMBO_MIN_WIDTH = 200;

    /**
     * Creates a base-chooser combo over the given element.
     *
     * <p>The base display text is a full filesystem path, so the combo's natural
     * min/preferred width is huge.  Bound it: it stretches to fill via GridBag when
     * there's room and shrinks to a readable min otherwise, rather than forcing the
     * whole section wider than the viewport.
     */
    protected static DDComboBox<String> createBaseCombo(DataElement<String> element) {
        DDComboBox<String> combo = new DDComboBox<>(element, STYLE);
        combo.setRequired(false);
        combo.resetValues();

        Dimension size = combo.getPreferredSize();
        combo.setPreferredSize(new Dimension(Math.min(size.width, BASE_COMBO_MAX_WIDTH), size.height));
        combo.setMinimumSize(new Dimension(BASE_COMBO_MIN_WIDTH, size.height));
        return combo;
    }

    /**
     * Creates the DataElement backing a base-chooser combo, seeded with the "None"
     * entry. It is backed by the given mutable lists so later {@link #populateBaseList}
     * + {@code resetValues()} calls pick up site changes.
     */
    protected static DataElement<String> createBaseElement(String name, List<String> keys, List<String> displays) {
        populateBaseList(keys, displays, null);
        return new DataElement<>(name, keys, displays);
    }

    /** Returns the combo's selected base key, or null when "None" is selected. */
    protected static String selectedBaseKey(DDComboBox<String> combo) {
        Object val = combo.getSelectedItem();
        return (val instanceof String s && !NONE_BASE.equals(s)) ? s : null;
    }

    protected static void populateBaseList(List<String> keys, List<String> displays, AlbumsFile af) {
        keys.clear();
        displays.clear();
        keys.add(NONE_BASE);
        displays.add(PropertyConfig.getMessage("combobox.base.none"));
        if (af != null) {
            for (Map.Entry<String, String> e : af.getBases().entrySet()) {
                keys.add(e.getKey());
                displays.add(e.getKey() + " (" + e.getValue() + ")");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Path validation rendering
    //
    // The rule engine lives in PathValidation (Swing-free, unit-tested).  Here we
    // only resolve a PathStatus' message key to localized text and place it in the
    // warning area.  INFO (docker note) renders blue; WARN renders as-is (the area
    // is already styled red).
    // -------------------------------------------------------------------------

    /** Resolves a status' message key to localized text: INFO blue, WARN as-is. */
    protected static String render(PathValidation.PathStatus s) {
        String msg = PropertyConfig.getMessage(s.messageKey(), s.args());
        return s.severity() == PathValidation.Severity.INFO
                ? "<font color='blue'>" + msg + "</font>"
                : msg;
    }

    /** Shows the messages of the given statuses (filtering those with no message) in the area. */
    protected static void applyStatuses(DDHtmlArea area, List<PathValidation.PathStatus> statuses, int width) {
        List<String> msgs = new ArrayList<>();
        for (PathValidation.PathStatus s : statuses) {
            if (s.hasMessage()) msgs.add(render(s));
        }
        applyWarnings(area, msgs, width);
    }

    protected static void applyWarnings(DDHtmlArea area, List<String> msgs, int width) {
        if (msgs.isEmpty()) {
            area.setVisible(false);
            area.revalidate();
        } else {
            area.setText("<html><body style='margin:0;padding:0'>" + String.join("<br>", msgs) + "</body></html>");
            area.setSize(width, 10_000);
            area.setPreferredSize(new Dimension(width, area.getPreferredSize().height));
            area.setVisible(true);
            area.revalidate();
        }
    }
}
