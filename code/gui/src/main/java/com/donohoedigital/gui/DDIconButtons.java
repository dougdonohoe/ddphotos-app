package com.donohoedigital.gui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

/**
 * Icon buttons from <a href="https://lucide.dev/icons/">lucide.dev/icons</a>
 */
public class DDIconButtons
{
    public static final FlatSVGIcon PLUS          = svgIcon("icons/plus.svg");
    public static final FlatSVGIcon EDIT          = svgIcon("icons/pencil.svg");
    public static final FlatSVGIcon TRASH         = svgIcon("icons/trash-2.svg");
    public static final FlatSVGIcon CHEVRON_UP    = svgIcon("icons/chevron-up.svg");
    public static final FlatSVGIcon CHEVRON_DOWN  = svgIcon("icons/chevron-down.svg");
    public static final FlatSVGIcon CAMERA_OFF    = svgIcon("icons/camera-off.svg");
    public static final FlatSVGIcon PLAY          = svgIcon("icons/play.svg");
    public static final FlatSVGIcon STOP_SQUARE   = svgIcon("icons/square.svg");
    public static final FlatSVGIcon KILL          = svgIcon("icons/circle-x.svg");
    public static final FlatSVGIcon SCROLL_TOP    = svgIcon("icons/arrow-up-to-line.svg");
    public static final FlatSVGIcon SCROLL_BOTTOM = svgIcon("icons/arrow-down-to-line.svg");
    public static final FlatSVGIcon ERASER        = svgIcon("icons/eraser.svg");
    public static final FlatSVGIcon FOLDER_OPEN   = svgIcon("icons/folder-open.svg");
    public static final FlatSVGIcon ARROW_LEFT    = svgIcon("icons/arrow-left.svg");
    public static final FlatSVGIcon ARROW_RIGHT   = svgIcon("icons/arrow-right.svg");
    public static final FlatSVGIcon SEARCH        = svgIcon("icons/search.svg");
    public static final FlatSVGIcon CLOSE         = svgIcon("icons/x.svg");
    public static final FlatSVGIcon LOCK          = svgIcon("icons/lock.svg");
    public static final FlatSVGIcon UNLOCK        = svgIcon("icons/lock-open.svg");
    public static final FlatSVGIcon EXTERNAL_LINK = svgIcon("icons/external-link.svg");
    public static final FlatSVGIcon ZAP           = svgIcon("icons/zap.svg");
    public static final FlatSVGIcon CIRCLE        = svgIcon("icons/circle.svg");

    public static DDButton iconButton(String name, String style, Icon icon)
    {
        DDButton btn = new DDButton(name, style);
        btn.setIcon(icon);
        btn.setText(null);
        btn.setPreferredSize(new Dimension(28, 28));
        return btn;
    }

    public static void makeFolderIcon(DDButton btn) {
        btn.setIcon(FOLDER_OPEN);
        btn.setText(null);
        btn.setPreferredSize(new Dimension(28, 28));
    }

    private static FlatSVGIcon svgIcon(String path)
    {
        return svgIcon(path, 16, "Label.foreground");
    }

    public static FlatSVGIcon svgIcon(String path, int size, String uiColorKey)
    {
        FlatSVGIcon icon = new FlatSVGIcon(path, size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(_ -> UIManager.getColor(uiColorKey)));
        return icon;
    }

    public static FlatSVGIcon svgIcon(FlatSVGIcon base, int size, String uiColorKey)
    {
        FlatSVGIcon icon = base.derive(size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(_ -> UIManager.getColor(uiColorKey)));
        return icon;
    }

    /**
     * Same, in a fixed color rather than one that follows the look and feel - for an icon whose
     * color is the message (a green marker, a red one) rather than decoration.
     */
    public static FlatSVGIcon svgIcon(FlatSVGIcon base, int size, Color color)
    {
        FlatSVGIcon icon = base.derive(size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(_ -> color));
        return icon;
    }

    /**
     * Same, in a color resolved on every paint rather than once - for an icon that has to follow
     * something changing underneath it, such as whether the row it sits in is selected.  The
     * color filter runs per paint, so the supplier is free to look at live state.
     */
    public static FlatSVGIcon svgIcon(FlatSVGIcon base, int size, Supplier<Color> color)
    {
        FlatSVGIcon icon = base.derive(size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(_ -> color.get()));
        return icon;
    }

    /**
     * Wraps an icon for optical alignment beside a label: {@code gapRight} pixels of empty space
     * after the glyph, on top of whatever icon-text gap the look and feel already applies, and
     * {@code nudgeDown} pixels of downward shift.  Centering an icon on a label's box tends to
     * leave it looking high, since text carries more of its weight below the middle.
     *
     * <p>The shift is paid for in the reported height rather than taken out of the container:
     * the box grows by twice the nudge and the glyph paints at the bottom of it, which - once
     * the container centers that taller box - lands the glyph exactly {@code nudgeDown} lower
     * while keeping it inside its own bounds.  Painting below the reported height instead would
     * be cheaper, but anything that clips to the icon's rectangle (a tab, a narrow row) shaves
     * the bottom off the glyph.
     */
    public static Icon alignedIcon(Icon base, int gapRight, int nudgeDown)
    {
        return new Icon()
        {
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                base.paintIcon(c, g, x, y + 2 * nudgeDown);
            }

            public int getIconWidth()
            {
                return base.getIconWidth() + gapRight;
            }

            public int getIconHeight()
            {
                return base.getIconHeight() + 2 * nudgeDown;
            }
        };
    }
}
