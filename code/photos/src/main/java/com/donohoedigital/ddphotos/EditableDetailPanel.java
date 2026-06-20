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

    /** Combo key for the "no base" entry; its display text comes from {@code combobox.base.none}. */
    protected static final String NONE_BASE = "";

    protected List<DDValidatable> validatables_;
    protected DDButton editBtn_, saveBtn_, cancelBtn_;

    private Runnable onEditModeChanged_;

    /**
     * Notified whenever editing starts, is saved, or is canceled (i.e. whenever
     * {@link #isEditing()} may have changed), so callers can lock other UI
     * while an edit is in progress.
     */
    public void setOnEditModeChanged(Runnable listener) {
        onEditModeChanged_ = listener;
    }

    protected void fireEditModeChanged() {
        if (onEditModeChanged_ != null) onEditModeChanged_.run();
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

        editBtn_   = new DDButton("editdetails", STYLE);
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

    public abstract boolean isEditing();

    // -------------------------------------------------------------------------
    // Shared layout helpers
    // -------------------------------------------------------------------------

    protected static DDLabelBorder gridSection(String name) {
        DDLabelBorder panel = new DDLabelBorder(name, STYLE);
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                panel.getBorder(),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return panel;
    }

    protected static void initBaseCombo(DDComboBox<String> combo) {
        combo.setRequired(false);
        combo.resetValues();
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

    protected static int addRow(JPanel panel, String labelName, JComponent field, JButton btn, int row) {
        return addRow(panel, new DDLabel(labelName, STYLE), field, btn, row);
    }

    protected static int addRow(JPanel panel, JComponent label, JComponent field, JButton btn, int row) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = row;
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(3, 0, 3, 8);
        panel.add(label, lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.gridy = row;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.gridwidth = btn != null ? 1 : 2;
        fc.insets = new Insets(3, 0, 3, btn != null ? 2 : 0);
        panel.add(field, fc);

        if (btn != null) {
            GridBagConstraints bc = new GridBagConstraints();
            bc.gridx = 2; bc.gridy = row;
            bc.insets = new Insets(3, 0, 3, 0);
            panel.add(btn, bc);
        }
        return row + 1;
    }

    protected static int addSpanRow(JPanel panel, JComponent comp, int row) {
        GridBagConstraints wc = new GridBagConstraints();
        wc.gridx = 0; wc.gridy = row;
        wc.gridwidth = 3;
        wc.fill = GridBagConstraints.HORIZONTAL;
        wc.weightx = 1.0;
        wc.insets = new Insets(2, 0, 2, 0);
        panel.add(comp, wc);
        return row + 1;
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
