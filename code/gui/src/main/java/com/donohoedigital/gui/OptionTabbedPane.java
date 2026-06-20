package com.donohoedigital.gui;

import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.util.prefs.Preferences;

/**
 * A DDTabbedPane that saves and restores the selected tab index to user prefs,
 * using the same node/key convention as OptionList.
 */
public class OptionTabbedPane extends DDTabbedPane {

    private final Preferences prefs_;
    private final String      name_;
    private boolean           active_ = false;

    public OptionTabbedPane(String sStyle,
                            @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.BOTTOM,
                                                        SwingConstants.LEFT, SwingConstants.RIGHT})
                            int nPlacement,
                            String prefNode, String name) {
        super(sStyle, nPlacement);
        prefs_ = DDOption.getOptionPrefs(prefNode);
        name_  = name;
    }

    /**
     * Restore the saved tab index; call after all tabs have been added.
     * Enables auto-save for all subsequent tab changes.
     */
    public void restoreFromPrefs() {
        int count = getTabCount();
        if (count == 0) return;
        int idx = prefs_.getInt(prefsKey(), 0);
        setSelectedIndex(idx >= 0 && idx < count ? idx : 0);
        active_ = true;
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        super.stateChanged(e);
        if (active_) {
            int idx = getSelectedIndex();
            if (idx >= 0) prefs_.putInt(prefsKey(), idx);
        }
    }

    private String prefsKey() {
        return name_ + ".idx";
    }
}
