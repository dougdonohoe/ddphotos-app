package com.donohoedigital.gui;

import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * A DDSplitPane that saves and restores the divider location to user prefs,
 * using the same node/key convention as OptionList / OptionTabbedPane.
 *
 * The saved location is applied in the constructor. setDividerLocation(int)
 * sets the UI's "divider location is set" flag, so the first layout positions
 * the divider before the first paint - no visible jump.
 */
public class OptionSplitPane extends DDSplitPane {

    private final Preferences prefs_;
    private final String      name_;

    public OptionSplitPane(String sName, String sStyle,
                           @MagicConstant(intValues = {JSplitPane.HORIZONTAL_SPLIT, JSplitPane.VERTICAL_SPLIT})
                           int newOrientation, Component newLeftComponent, Component newRightComponent,
                           boolean bOpaque, String prefNode) {
        super(sName, sStyle, newOrientation, newLeftComponent, newRightComponent, bOpaque);
        prefs_ = DDOption.getOptionPrefs(prefNode);
        name_  = sName;

        int loc = prefs_.getInt(prefsKey(), -1);
        if (loc > 0) setDividerLocation(loc);

        // Add the save listener after restoring, so the initial set above is not
        // re-saved; every later change (user drag, layout clamp) is a real value.
        addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY, e -> {
            int l = getDividerLocation();
            if (l > 0) prefs_.putInt(prefsKey(), l);
        });
    }

    private String prefsKey() {
        return name_ + ".divider";
    }
}
