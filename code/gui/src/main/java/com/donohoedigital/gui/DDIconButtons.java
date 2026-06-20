package com.donohoedigital.gui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;

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
        btn.setPreferredSize(new Dimension(22, 22));
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
}
