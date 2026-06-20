package com.donohoedigital.gui;

import javax.swing.*;
import java.util.prefs.Preferences;

/**
 * A DDList that saves and restores the selected index to user prefs,
 * using the same node/key convention as DDOption's bIndexPrefs_ mode.
 */
public class OptionList<E> extends DDList<E> {

    private final Preferences prefs_;
    private final String      name_;
    private String            extraKey_;

    public OptionList(DefaultListModel<E> model, String prefNode, String name, String style) {
        super(model, name, style);
        name_  = name;
        prefs_ = DDOption.getOptionPrefs(prefNode);
        addListSelectionListener(_ -> saveToPrefs());
    }

    /** Set an extra key segment; prefs key becomes {@code name.extra.idx} instead of {@code name.idx}. */
    public void setExtraKey(String extra) {
        extraKey_ = extra;
    }

    /** Restore the saved index; call after the model has been populated. */
    public void restoreFromPrefs() {
        int count = getModel().getSize();
        if (count == 0) return;
        int idx = prefs_.getInt(prefsKey(), 0);
        setSelectedIndex(idx >= 0 && idx < count ? idx : 0);
        ensureIndexIsVisible(getSelectedIndex());
    }

    private void saveToPrefs() {
        int idx = getSelectedIndex();
        if (idx >= 0) prefs_.putInt(prefsKey(), idx);
    }

    private String prefsKey() {
        return extraKey_ != null ? name_ + "." + extraKey_ + ".idx" : name_ + ".idx";
    }
}
