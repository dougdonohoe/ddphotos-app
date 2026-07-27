package com.donohoedigital.gui;

import javax.swing.*;
import java.awt.*;

/**
 * The standard DD Photos window layout, shared by the main window, the photogen.txt editor and the
 * Help / Support windows: the app logo top left, an optional component to its right (a title, a
 * combo, a widget row), an optional component at the far right of that strip, the window's main
 * contents below, and an optional hover-help strip along the bottom.
 * <p>
 * The panel is transparent, so the logo strip picks up the white painted by the engine's base panel
 * (the {@code engine.basepanel} colour).  Contents that want the standard grey background ask for it
 * via {@link #setCenterBackground}; the center component itself never has to worry about its own
 * outer borders - {@link #setContentInsets} owns those.
 */
public class LogoWindowPanel extends DDPanel
{
    /** Gap between the window edge and the contents. */
    private static final int GAP = GuiUtils.STANDARD_BORDER_GAP;

    /**
     * Insets of the component sitting to the right of the logo, relative to the logo strip.  The
     * trailing gap lives here rather than on the strip so that a top-right component can sit flush
     * against the window edge and supply its own inset (see {@link #setTopRightComponent}).
     */
    private static final Insets TOP_COMPONENT_INSETS = new Insets(11, 20, 0, GAP);

    /** Insets of the logo strip itself. */
    private static final Insets TOP_BAR_INSETS = new Insets(4, GAP, 0, 0);

    private final DDImageButton logo_;
    private final DDPanel topBar_;
    private final DDPanel content_;
    private final DDHtmlArea help_;

    private JComponent topHolder_;
    private JComponent topRightHolder_;
    private JComponent center_;

    /**
     * @param iconName image button name for the logo (e.g. {@code "icon48"})
     * @param helpStyle style used for the bottom hover-help strip, or null for no help strip
     */
    public LogoWindowPanel(String iconName, String helpStyle)
    {
        topBar_ = new DDPanel();
        topBar_.setBorder(BorderFactory.createEmptyBorder(TOP_BAR_INSETS.top, TOP_BAR_INSETS.left,
                                                          TOP_BAR_INSETS.bottom, TOP_BAR_INSETS.right));
        logo_ = new DDImageButton(iconName);
        topBar_.add(logo_, BorderLayout.WEST);
        add(topBar_, BorderLayout.NORTH);

        content_ = new DDPanel();
        setContentInsets(0, GAP, 0, GAP);
        add(content_, BorderLayout.CENTER);

        help_ = helpStyle == null ? null : GuiUtils.createHelpText(helpStyle);
        if (help_ != null) add(help_, BorderLayout.SOUTH);
    }

    /**
     * Set the component shown to the right of the logo.  It is top-aligned and stretches to the
     * width of the window, so a caller wanting its widgets to stay left should end its row with
     * {@link Box#createHorizontalGlue}.
     */
    public void setTopComponent(JComponent c)
    {
        if (topHolder_ != null) topBar_.remove(topHolder_);
        topHolder_ = null;
        if (c != null)
        {
            // wrap rather than setting a border on the caller's component
            DDPanel holder = new DDPanel();
            holder.setBorder(BorderFactory.createEmptyBorder(
                    TOP_COMPONENT_INSETS.top, TOP_COMPONENT_INSETS.left,
                    TOP_COMPONENT_INSETS.bottom, TOP_COMPONENT_INSETS.right));
            holder.add(c, BorderLayout.CENTER);
            topHolder_ = GuiUtils.NORTH(holder);
            topBar_.add(topHolder_, BorderLayout.CENTER);
        }
        revalidate();
    }

    /**
     * Set the component pinned to the far right of the logo strip (e.g. nav buttons, status).  It
     * sits flush against the window edge, so it carries its own trailing inset.
     */
    public void setTopRightComponent(JComponent c)
    {
        if (topRightHolder_ != null) topBar_.remove(topRightHolder_);
        topRightHolder_ = null;
        if (c != null)
        {
            topRightHolder_ = GuiUtils.NORTH(c);
            topBar_.add(topRightHolder_, BorderLayout.EAST);
        }
        revalidate();
    }

    /**
     * Set the window's main contents.  The insets around it are this panel's business, not the
     * component's - see {@link #setContentInsets}.
     */
    public void setCenterComponent(JComponent c)
    {
        if (center_ != null) content_.remove(center_);
        center_ = c;
        if (c != null) content_.add(c, BorderLayout.CENTER);
        revalidate();
    }

    /**
     * Background painted behind the contents - typically {@code app.panel.bg}.  Null (the default)
     * leaves the area transparent, so it shows the window's white background.
     */
    public void setCenterBackground(Color c)
    {
        content_.setOpaque(c != null);
        content_.setBackground(c);
    }

    /**
     * Gap between the contents and the surrounding window.  Defaults to the standard 10px on the
     * left and right, flush against the logo strip and whatever is below.
     */
    public void setContentInsets(int top, int left, int bottom, int right)
    {
        content_.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
    }

    /**
     * Hide the logo strip - for screens like the setup wizard that fill the window on their own.
     */
    public void setTopBarVisible(boolean b)
    {
        topBar_.setVisible(b);
    }

    /**
     * The logo, e.g. to seed the window's initial help message.
     */
    public DDImageButton getLogoComponent()
    {
        return logo_;
    }

    /**
     * The hover-help strip, or null if this window was created without one.  Register it with
     * {@code context.getWindow().setHelpTextWidget(...)} so the window's hover-help renders here.
     */
    public DDHtmlArea getHelpText()
    {
        return help_;
    }
}
