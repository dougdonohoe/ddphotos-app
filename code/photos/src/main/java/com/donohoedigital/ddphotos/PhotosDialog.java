package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.DialogPhase;
import com.donohoedigital.gui.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.donohoedigital.app.engine.EngineUtils.STANDARD_BORDER_GAP;

abstract class PhotosDialog extends DialogPhase
{
    protected final List<DDValidatable> validatables_ = new ArrayList<>();

    protected abstract void checkButtons();

    protected DDPanel wrapWithInstructions(String instrName, String instrText, DDPanel form, int preferredWidth)
    {
        DDHtmlArea instructions = new DDHtmlArea(instrName, STYLE);
        instructions.setText(instrText);
        instructions.setDisplayOnly(true);
        instructions.setBorder(BorderFactory.createEmptyBorder(STANDARD_BORDER_GAP, STANDARD_BORDER_GAP, 5, STANDARD_BORDER_GAP));

        // Measure (and lock) the editor at the width it will actually be displayed at - it fills
        // the wrapper's NORTH region, whose width is preferredWidth. Measuring at a narrower width
        // wraps the text to extra lines, leaving a gap below it once laid out wider; measuring
        // wider clips it. Locking the preferred size also keeps DialogBackground.applyMaxWidth a
        // no-op (reported width stays <= dialog-maxwidth).
        instructions.setSize(preferredWidth, Short.MAX_VALUE);
        int instrHeight = instructions.getPreferredSize().height;
        instructions.setPreferredSize(new Dimension(preferredWidth, instrHeight));
        int formHeight  = form.getPreferredSize().height;

        DDPanel wrapper = new DDPanel();
        wrapper.setLayout(new BorderLayout(0, 0));
        wrapper.add(instructions, BorderLayout.NORTH);
        wrapper.add(form,         BorderLayout.CENTER);
        wrapper.setPreferredSize(new Dimension(preferredWidth, instrHeight + formHeight));

        GuiUtils.getValidatables(form, validatables_);
        validatables_.forEach(v -> v.addValidationListener(this::checkButtons));

        return wrapper;
    }

    protected int addFieldRow(DDPanel form, String name, DDTextField field, JButton btn, int row)
    {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = row;
        lc.anchor = GridBagConstraints.EAST;
        lc.insets = new Insets(4, 4, 4, 8);
        form.add(new DDLabel(name, STYLE), lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.gridy = row;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(4, 0, 4, 2);
        form.add(field, fc);

        if (btn != null) {
            GridBagConstraints bc = new GridBagConstraints();
            bc.gridx = 2; bc.gridy = row;
            bc.insets = new Insets(4, 2, 4, 4);
            form.add(btn, bc);
        }
        return row + 1;
    }
}
