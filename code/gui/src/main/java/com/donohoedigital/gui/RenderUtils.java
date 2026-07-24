/*
 * RenderUtils.java
 */
package com.donohoedigital.gui;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;

/**
 * Rendering-hint helpers.
 *
 * <p>Deliberately free of static state: these are called from {@link ImageComponent#paintComponent},
 * which paints the splash screen before config files are loaded.  Anything with a static initializer
 * that touches StylesConfig/PropertyConfig (such as {@link GuiUtils}) blows up if class-loaded that
 * early, so these cannot live there.
 *
 * @author Doug Donohoe
 */
public final class RenderUtils
{
    private RenderUtils() {}

    /**
     * Apply an interpolation hint, returning the previous value (possibly null) so the caller
     * can restore it with {@link #restoreInterpolation}.  Java 2D defaults to nearest-neighbor,
     * which visibly stair-steps images whenever they are drawn at a size other than their
     * natural size -- including on scaled displays (Windows at 125%/150%, HiDPI), where the
     * device transform scales even images drawn 1:1 in logical pixels.
     */
    public static Object applyInterpolation(Graphics2D g, Object interpolation)
    {
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        return old;
    }

    /**
     * Restore the interpolation hint saved by {@link #applyInterpolation}.  A null old value
     * means the hint was unset, which Java 2D treats as nearest-neighbor.
     */
    public static void restoreInterpolation(Graphics2D g, Object old)
    {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           old == null ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : old);
    }

    /**
     * Device pixels per logical pixel for the screen showing {@code c} (1.0 on an unscaled
     * display, 1.25 for Windows at 125%, 2.0 for a Retina-class screen).  Falls back to the
     * default screen when {@code c} is null or not yet realized, and to 1.0 in a headless
     * environment.
     *
     * <p>Multiply by this when generating a bitmap that will be drawn into a logical-pixel box
     * (thumbnails, scaled artwork): generating at logical size and letting the device transform
     * upscale is what makes such images look soft.
     */
    public static double getDisplayScale(Component c)
    {
        try
        {
            GraphicsConfiguration gc = (c == null) ? null : c.getGraphicsConfiguration();
            if (gc == null)
            {
                if (GraphicsEnvironment.isHeadless()) return 1.0d;
                gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                        .getDefaultScreenDevice().getDefaultConfiguration();
            }
            double scale = gc.getDefaultTransform().getScaleX();
            return (scale > 0) ? scale : 1.0d;
        }
        catch (Exception e)
        {
            return 1.0d;
        }
    }
}
