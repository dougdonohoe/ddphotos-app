/*
 * DialogType.java
 *
 * Type of a dialog, used to color its title bar.
 */

package com.donohoedigital.gui;

/**
 * Type of dialog, which drives the title-bar color (looked up in styles.xml).
 *
 * @author Doug Donohoe
 */
public enum DialogType
{
    INFO("modal.title.info"),
    WARN("modal.title.warn"),
    ERROR("modal.title.error");

    private final String styleBase_;

    DialogType(String styleBase)
    {
        styleBase_ = styleBase;
    }

    /**
     * styles.xml color name for the title-bar background
     */
    public String bgColorName()
    {
        return styleBase_ + ".bg";
    }

    /**
     * styles.xml color name for the title-bar foreground
     */
    public String fgColorName()
    {
        return styleBase_ + ".fg";
    }

    /**
     * Parse a type name (case-insensitive), returning def if null or unknown
     */
    public static DialogType fromString(String s, DialogType def)
    {
        if (s == null) return def;
        try
        {
            return valueOf(s.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            return def;
        }
    }
}
