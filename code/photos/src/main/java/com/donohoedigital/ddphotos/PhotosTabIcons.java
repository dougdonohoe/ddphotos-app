package com.donohoedigital.ddphotos;

import com.donohoedigital.gui.DDIconButtons;
import com.donohoedigital.gui.DDTabPanel;
import com.donohoedigital.gui.DDTabbedPane;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;
import java.awt.Color;

/**
 * The markers on the main window's tabs.  A command tab carries a status lamp - a green bolt
 * while its command runs (see {@link AbstractRunnerPanel}), a ring the rest of the time - so a
 * long {@code run} or {@code serve}, or a publish sequence stepping from tab to tab, can be seen
 * from whichever tab is open.
 *
 * <p>Config runs nothing, so it has no state to report and gets a pencil instead.  Every tab
 * having an icon is what keeps the labels lined up with each other; a bare Config tab would sit
 * a glyph's width to the left of the rest.
 *
 * <p>The ring and the pencil follow their tab's own text color when that tab is selected and go
 * muted otherwise, so they read as part of the label rather than as decoration.  The bolt keeps
 * its green either way: there the color is the message.
 */
public final class PhotosTabIcons {

    private static final int SIZE = 15;

    // Nudged down: centered on the label's box the glyphs read as sitting high, since the text
    // beside them carries more of its weight below the middle.
    private static final int NUDGE_DOWN = 1;

    // On top of the look and feel's own icon-text gap, which is tight at this size.
    private static final int GAP_RIGHT = 3;

    // Fixed rather than themed: on a running tab the color is the message. Matches the green
    // DockerStatusPanel uses for a running daemon.
    private static final Color RUNNING_GREEN = new Color(46, 160, 67);

    // Unselected lamps, so the tabs you are not on stay quiet.
    private static final String MUTED_COLOR_KEY = "Label.disabledForeground";

    /**
     * A command is running on this tab.  Shared, since it looks the same on every tab - unlike
     * the lamps below, it doesn't depend on which tab is selected.
     */
    public static final Icon RUNNING =
            tabIcon(DDIconButtons.svgIcon(DDIconButtons.ZAP, SIZE, RUNNING_GREEN));

    /** This tab runs a command, and isn't right now. */
    public static Icon idle(DDTabPanel tab) {
        return lamp(DDIconButtons.CIRCLE, tab);
    }

    /** The Config tab, which runs nothing. */
    public static Icon config(DDTabPanel tab) {
        return lamp(DDIconButtons.EDIT, tab);
    }

    /**
     * A lamp bound to one tab.  It has to be per-tab: an icon is told which tabbed pane is
     * painting it, but not which of that pane's tabs, so the panel it belongs to is what can
     * answer whether it is the selected one.  Nothing is cached or listened for - the color is
     * resolved on each paint, and switching tabs repaints them.
     */
    private static Icon lamp(FlatSVGIcon glyph, DDTabPanel tab) {
        return tabIcon(DDIconButtons.svgIcon(glyph, SIZE, () -> lampColor(tab)));
    }

    /** This tab's own text color while it is the selected one, muted otherwise. */
    private static Color lampColor(DDTabPanel tab) {
        Color muted = UIManager.getColor(MUTED_COLOR_KEY);
        DDTabbedPane pane = tab.getTabPane();
        if (pane == null || !tab.isSelectedTab()) return muted;

        // What the look and feel paints this tab's title in: its own foreground, unless that is
        // just inherited from the defaults and the theme names a separate selected color.
        Color fg = pane.getForegroundAt(tab.getTabNum());
        if (fg instanceof UIResource) {
            Color selected = UIManager.getColor("TabbedPane.selectedForeground");
            if (selected != null) return selected;
        }
        return fg != null ? fg : muted;
    }

    private static Icon tabIcon(Icon base) {
        return DDIconButtons.alignedIcon(base, GAP_RIGHT, NUDGE_DOWN);
    }

    private PhotosTabIcons() {
    }
}
