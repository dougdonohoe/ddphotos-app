package com.donohoedigital.zydeco;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;

public class Icons
{
    private static final Color FOLDER_COLOR = new Color(0x5B9BD5);

    public static final Icon FOLDER      = svgIcon("icons/folder.svg",      FOLDER_COLOR);
    public static final Icon FOLDER_OPEN = svgIcon("icons/folder-open.svg", FOLDER_COLOR);
    public static final Icon FILE        = svgIcon("icons/file.svg",         null);

    private static FlatSVGIcon svgIcon(String path, Color color)
    {
        FlatSVGIcon icon = new FlatSVGIcon(path, 16, 16);
        // Lambda is evaluated at paint time, so UIManager reflects the current LAF (light or dark)
        if (color != null) {
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(_ -> color));
        } else {
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(_ -> UIManager.getColor("Label.foreground")));
        }
        return icon;
    }
}
