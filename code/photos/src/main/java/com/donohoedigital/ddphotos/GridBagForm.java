package com.donohoedigital.ddphotos;

import com.donohoedigital.gui.DDLabel;
import com.donohoedigital.gui.DDPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Builds the three-column [leading] [field] [trailing] rows used by the detail panels and
 * the dialogs, tracking the row index so callers don't have to.
 *
 * <p>The two styles are intentionally distinct - they are what the panels and the dialogs
 * each looked like before this class existed:
 *
 * <ul>
 *   <li>{@link #detail} - label anchored WEST, tighter insets, and the field spans columns
 *       1-2 when there is no trailing widget so it lines up with {@link #span} rows.</li>
 *   <li>{@link #dialog} - label anchored EAST, and the field always stops at column 1 so
 *       rows without a trailing widget stay aligned with the rows that have one.</li>
 * </ul>
 */
final class GridBagForm {

    /** The bits that differ between a detail-panel row and a dialog row. */
    private record RowStyle(int labelAnchor, Insets label, Insets field,
                            Insets fieldNoTrailing, Insets trailing,
                            boolean spanFieldWhenNoTrailing) {}

    private static final RowStyle DETAIL = new RowStyle(GridBagConstraints.WEST,
            new Insets(3, 0, 3, 8), new Insets(3, 0, 3, 2),
            new Insets(3, 0, 3, 0), new Insets(3, 0, 3, 0), true);

    private static final RowStyle DIALOG = new RowStyle(GridBagConstraints.EAST,
            new Insets(4, 4, 4, 8), new Insets(4, 0, 4, 2),
            new Insets(4, 0, 4, 2), new Insets(4, 2, 4, 4), false);

    private final JPanel panel_;
    private final String style_;
    private final RowStyle rowStyle_;
    private int row_;

    private GridBagForm(JPanel panel, String style, RowStyle rowStyle) {
        panel_ = panel;
        style_ = style;
        rowStyle_ = rowStyle;
    }

    /** Adds rows to an existing section panel, e.g. from {@code EditableDetailPanel.gridSection}. */
    static GridBagForm detail(JPanel panel, String style) {
        return new GridBagForm(panel, style, DETAIL);
    }

    /** Creates a dialog's form panel, ready for rows. */
    static GridBagForm dialog(String style) {
        DDPanel form = new DDPanel();
        form.setLayout(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 8, 4, 8));
        return new GridBagForm(form, style, DIALOG);
    }

    JPanel panel() {
        return panel_;
    }

    /** @param trailing optional widget in the third column (browse button, checkbox, ...) */
    GridBagForm row(String labelName, JComponent field, JComponent trailing) {
        return row(new DDLabel(labelName, style_), field, trailing);
    }

    /**
     * @param leading  first column - usually a label, but any component (a checkbox, say) works
     * @param trailing optional widget in the third column (browse button, checkbox, ...)
     */
    GridBagForm row(JComponent leading, JComponent field, JComponent trailing) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = row_;
        lc.anchor = rowStyle_.labelAnchor();
        lc.insets = rowStyle_.label();
        panel_.add(leading, lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.gridy = row_;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.gridwidth = (trailing == null && rowStyle_.spanFieldWhenNoTrailing()) ? 2 : 1;
        fc.insets = trailing != null ? rowStyle_.field() : rowStyle_.fieldNoTrailing();
        panel_.add(field, fc);

        if (trailing != null) {
            GridBagConstraints bc = new GridBagConstraints();
            bc.gridx = 2; bc.gridy = row_;
            bc.insets = rowStyle_.trailing();
            panel_.add(trailing, bc);
        }
        row_++;
        return this;
    }

    /** A component occupying the full width of the form. */
    GridBagForm span(JComponent comp) {
        GridBagConstraints wc = new GridBagConstraints();
        wc.gridx = 0; wc.gridy = row_;
        wc.gridwidth = 3;
        wc.fill = GridBagConstraints.HORIZONTAL;
        wc.weightx = 1.0;
        wc.insets = new Insets(2, 0, 2, 0);
        panel_.add(comp, wc);
        row_++;
        return this;
    }

    /**
     * Absorbs any slack vertical space at the bottom so the GridBag keeps its rows
     * top-aligned instead of centering them (which opens a gap above the first row and
     * pushes the last one out of view when the panel is tall and narrow).
     */
    GridBagForm glue() {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = row_; gc.gridwidth = 3;
        gc.weighty = 1.0;
        gc.fill = GridBagConstraints.BOTH;
        panel_.add(Box.createGlue(), gc);
        row_++;
        return this;
    }
}
