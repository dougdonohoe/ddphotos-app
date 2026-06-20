package com.donohoedigital.zydeco;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

public class ZydecoApp
{
    static void main()
    {
        // macOS: use the screen menu bar and set the app name
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Zydeco");

        FlatLightLaf.setup();

        SwingUtilities.invokeLater(() -> {
            ZydecoFrame frame = new ZydecoFrame();
            frame.setVisible(true);
        });
    }
}
