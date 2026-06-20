/*
 * DDInternalFrameTitlePane.java
 *
 * Created on August 21, 2003, 3:32 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.config.*;

import javax.swing.plaf.metal.*;
import javax.swing.*;
import java.awt.*;

/**
 * Title pane for our internal dialogs.  Draws a Metal-style "bumpy" title bar
 * (see {@link DDMetalBumps}) with a single close button.  Colors come from
 * styles.xml:
 * <ul>
 *   <li>selected modal dialog - the info/warn/error color for its {@link DialogType}</li>
 *   <li>selected non-modal dialog - {@code window.title.active.bg}</li>
 *   <li>unselected dialog - {@code window.title.bg}</li>
 * </ul>
 *
 * @author  Doug Donohoe
 */
public class DDInternalFrameTitlePane extends MetalInternalFrameTitlePane
{
    private final Color selectedBackground_;
    private final Color selectedForeground_;
    private final Color unselectedBackground_;
    private final Color unselectedForeground_;

    /** Creates a new instance of DDInternalFrameTitlePane */
    public DDInternalFrameTitlePane(InternalDialog f)
    {
        super(f);

        // a selected modal dialog is colored by its type; a selected non-modal
        // dialog uses the generic active color
        if (f.isModal())
        {
            DialogType type = f.getDialogType();
            selectedBackground_ = StylesConfig.getColor(type.bgColorName());
            selectedForeground_ = StylesConfig.getColor(type.fgColorName());
        }
        else
        {
            selectedBackground_ = StylesConfig.getColor("window.title.active.bg");
            selectedForeground_ = StylesConfig.getColor("window.title.fg");
        }
        unselectedBackground_ = StylesConfig.getColor("window.title.bg");
        unselectedForeground_ = StylesConfig.getColor("window.title.fg");
    }

    public void paintComponent(Graphics g)
    {
        boolean isSelected = frame.isSelected();
        int width = getWidth();
        int height = getHeight();

        Color background = isSelected ? selectedBackground_ : unselectedBackground_;
        Color foreground = isSelected ? selectedForeground_ : unselectedForeground_;

        g.setColor(background);
        g.fillRect(0, 0, width, height);

        int xOffset = 5;

        Icon icon = frame.getFrameIcon();
        if (icon != null) {
            int iconY = (height / 2) - (icon.getIconHeight() / 2);
            icon.paintIcon(frame, g, xOffset, iconY);
            xOffset += icon.getIconWidth() + 5;
        }

        String frameTitle = frame.getTitle();
        if (frameTitle != null) {
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(foreground);

            int yOffset = ((height - fm.getHeight()) / 2) + fm.getAscent();

            // right edge of the title = left edge of the close button (our only button)
            Rectangle rect = closeButton.getBounds();
            if (rect.x == 0) {
                rect.x = frame.getWidth() - frame.getInsets().right - 2;
            }
            frameTitle = getTitle(frameTitle, fm, rect.x - xOffset - 4);

            g.drawString(frameTitle, xOffset, yOffset);
            xOffset += SwingUtilities.computeStringWidth(fm, frameTitle) + 5;
        }

        // fill the remaining space (up to the close button) with bumps
        int buttonsWidth = closeButton.getIcon().getIconWidth();
        int bumpYOffset = 3;
        DDMetalBumps bumps = new DDMetalBumps(10, 10,
                background.brighter(), background.darker(), background);
        bumps.setBumpArea(width - buttonsWidth - xOffset - 5, height - (2 * bumpYOffset));
        bumps.paintIcon(this, g, xOffset, bumpYOffset);
    }

    protected void installDefaults()
    {
        super.installDefaults();
        closeIcon = DDIconButtons.CLOSE;
    }
}
