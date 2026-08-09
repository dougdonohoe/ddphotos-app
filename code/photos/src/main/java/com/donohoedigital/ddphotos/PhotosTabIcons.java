package com.donohoedigital.ddphotos;

import com.donohoedigital.gui.DDIconButtons;

import javax.swing.Icon;
import java.awt.Color;

/**
 * The markers on the main window's tabs.  A command tab carries a status lamp - a green bolt
 * while its command runs (see {@link AbstractRunnerPanel}), a grayed ring the rest of the time -
 * so a long {@code run} or {@code serve}, or a publish sequence stepping from tab to tab, can be
 * seen from whichever tab is open.
 *
 * <p>Config runs nothing, so it has no state to report and gets a pencil instead.  Every tab
 * having an icon is what keeps the labels lined up with each other; a bare Config tab would sit
 * a glyph's width to the left of the rest.
 *
 * <p>All three are built through {@link #tabIcon} so they sit identically in the strip.
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

    // Muted, so nine idle tabs stay quiet and the one running tab is what catches the eye.
    private static final String IDLE_COLOR_KEY = "Label.disabledForeground";

    /** A command is running on this tab. */
    public static final Icon RUNNING =
            tabIcon(DDIconButtons.svgIcon(DDIconButtons.ZAP, SIZE, RUNNING_GREEN));

    /** This tab runs a command, and isn't right now. */
    public static final Icon IDLE =
            tabIcon(DDIconButtons.svgIcon(DDIconButtons.CIRCLE, SIZE, IDLE_COLOR_KEY));

    /** The Config tab, which runs nothing. */
    public static final Icon CONFIG =
            tabIcon(DDIconButtons.svgIcon(DDIconButtons.EDIT, SIZE, IDLE_COLOR_KEY));

    private static Icon tabIcon(Icon base) {
        return DDIconButtons.alignedIcon(base, GAP_RIGHT, NUDGE_DOWN);
    }

    private PhotosTabIcons() {
    }
}
