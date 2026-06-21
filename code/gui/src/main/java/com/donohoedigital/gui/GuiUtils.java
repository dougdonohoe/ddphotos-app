package com.donohoedigital.gui;

import com.donohoedigital.base.ApplicationError;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.StylesConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intellij.lang.annotations.MagicConstant;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Arrays;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class GuiUtils
{
    private static final Logger logger = LogManager.getLogger(GuiUtils.class);

    public static final Color COLOR_DISABLED_TEXT = StylesConfig.getColor("gui.text.disabled.fg");

    /** Thin border drawn around screenshots (see {@link #printToImage}). */
    private static final Color SCREENSHOT_BORDER = new Color(0xB0, 0xB0, 0xB0);

    static final JTextComponent.KeyBinding[] MAC_CUT_COPY_PASTE = {
            new JTextComponent.KeyBinding(
                    KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK),
                    DefaultEditorKit.copyAction),
            new JTextComponent.KeyBinding(
                    KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK),
                    DefaultEditorKit.pasteAction),
            new JTextComponent.KeyBinding(
                    KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.META_DOWN_MASK),
                    DefaultEditorKit.cutAction),
    };

    /**
     * Copy to clipboard
     */
    public static void copyToClipboard(String sText)
    {
        StringSelection value = new StringSelection(sText);
        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        clip.setContents(value, null);
    }

    /**
     * Return DDComponent from this object.
     */
    public static DDComponent getDDComponent(Object o)
    {
        if (o instanceof DDComponent) return (DDComponent) o;

        if (o instanceof Component c)
        {
            c = c.getParent();
            while (c != null)
            {
                if (c instanceof DDComponent) return (DDComponent) c;
                c = c.getParent();
            }
        }

        return null;
    }

    /**
     * Return component (or its nearest ancestor) that is opaque and
     * has a completely opaque background color
     */
    public static Component getSolidRepaintComponent(Component c)
    {
        Color color;
        while (c != null)
        {
            if (c.isOpaque())
            {
                color = c.getBackground();
                // if color is null, most likely repainting before everything 
                // is initialized, so just return this component
                if (color == null || color.getTransparency() == Transparency.OPAQUE)
                {
                    return c;
                }
            }

            c = c.getParent();
        }

        return null;
    }

    public static boolean repaint(Component c)
    {
        return repaint(c, 0, 0, c.getWidth(), c.getHeight());
    }

    public static boolean repaint(Component c, int x, int y, int width, int height)
    {
        Component foo = getSolidRepaintComponent(c);
        if (foo != null && foo != c)
        {
            Point pRepaint = SwingUtilities.convertPoint(c, x, y, foo);
            foo.repaint(pRepaint.x, pRepaint.y, width, height);
            return true;
        }
        return false;
    }

    /**
     * Set preferred height of a component, keeping preferred width
     */
    public static void setPreferredHeight(JComponent c, int height)
    {
        c.setPreferredSize(new Dimension((int) c.getPreferredSize().getWidth(), height));
    }

    /**
     * Set preferred width of a component, keeping preferred height
     */
    public static void setPreferredWidth(JComponent c, int width)
    {
        c.setPreferredSize(new Dimension(width, (int) c.getPreferredSize().getHeight()));
    }

    private static String indent(int nIndent)
    {
        return "    ".repeat(Math.max(0, nIndent));
    }

    /**
     * Sets background of all children of container to color (container itself is
     * not set to prevent infinite loops if called from setBackground itself)
     */
    public static void setBackgroundChildren(Container container, Color color)
    {
        Component[] children = container.getComponents();
        for (Component aChildren : children)
        {
            aChildren.setBackground(color);
            if (aChildren instanceof Container)
            {
                setBackgroundChildren((Container) aChildren, color);
            }
        }
    }

    /**
     * Install a caret on the given text component that supports selection
     * (e.g., for copy via setFocusable(true)) but never paints, so no
     * blinking text cursor is shown.
     */
    public static void setDoNothingCaret(JTextComponent comp)
    {
        comp.setCaret(new DefaultCaret()
        {
            @Override
            public void paint(Graphics g)
            {
                // no-op: hide caret but keep selection/click positioning
            }
        });
    }

    /**
     * add mouse listener to all children components
     */
    public static void addMouseListenerChildren(Container container, MouseListener mouse)
    {
        Component[] children = container.getComponents();
        for (Component aChildren : children)
        {
            aChildren.addMouseListener(mouse);
            if (aChildren instanceof Container)
            {
                addMouseListenerChildren((Container) aChildren, mouse);
            }
        }
    }

    /**
     * remove mouse listener from all children components
     */
    public static void removeMouseListenerChildren(Container container, MouseListener mouse)
    {
        Component[] children = container.getComponents();
        for (Component aChildren : children)
        {
            aChildren.removeMouseListener(mouse);
            if (aChildren instanceof Container)
            {
                removeMouseListenerChildren((Container) aChildren, mouse);
            }
        }
    }

    /**
     * fill array with all DDOptions in the hierarchy
     */
    public static void getDDOptions(Container container, List<DDOption> options)
    {
        Component[] children = container.getComponents();
        for (Component aChildren : children)
        {
            if (aChildren instanceof DDOption dd)
            {
                if (!dd.isIgnored())
                {
                    options.add(dd);
                }
            }
            if (aChildren instanceof Container)
            {
                getDDOptions((Container) aChildren, options);
            }
        }
    }

    /**
     * Collect all DDValidatable widgets in the component hierarchy.
     */
    public static void getValidatables(Container container, List<DDValidatable> result)
    {
        for (Component c : container.getComponents())
        {
            if (c instanceof DDValidatable v) result.add(v);
            if (c instanceof Container cont) getValidatables(cont, result);
        }
    }

    /**
     * Set all DDOption labels under this container to the same width plus padding; returns that width.
     */
    public static int setDDOptionLabelWidths(Container container, int padding)
    {
        List<DDOption> options = new ArrayList<>();
        getDDOptions(container, options);
        int maxWidth = 0;
        for (DDOption opt : options) {
            JComponent lbl = opt.getLabelComponent();
            if (lbl != null) maxWidth = Math.max(maxWidth, lbl.getPreferredSize().width);
        }
        maxWidth += padding;
        for (DDOption opt : options) {
            JComponent lbl = opt.getLabelComponent();
            if (lbl == null) continue;
            Dimension pref = lbl.getPreferredSize();
            pref.width = maxWidth;
            lbl.setPreferredSize(pref);
        }
        return maxWidth;
    }

    /**
     * invoke SwingIt logic later (regardless if current thread is swing thread)
     */
    public static void invokeLater(Runnable it)
    {
        SwingUtilities.invokeLater(it);
    }

    /**
     * Method to add a key action to a component using the ActionMap/InputMap
     * way.
     */
    public static void addKeyAction(JComponent comp,
                                    @MagicConstant(intValues = {JComponent.WHEN_IN_FOCUSED_WINDOW,
                                            JComponent.WHEN_FOCUSED,
                                            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT})

                                    int nWhen,
                                    String sActionName, Action action,
                                    int key,
                                    @MagicConstant(flags = {InputEvent.CTRL_DOWN_MASK,
                                            InputEvent.SHIFT_DOWN_MASK, InputEvent.ALT_DOWN_MASK, InputEvent.META_DOWN_MASK,
                                            InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON2_DOWN_MASK, InputEvent.BUTTON3_DOWN_MASK})
                                    int key_mods) {
        comp.getActionMap().put(sActionName, action);
        comp.getInputMap(nWhen).put(KeyStroke.getKeyStroke(key, key_mods), sActionName);
    }

    /**
     * Remove a key action
     */
    public static void removeKeyAction(JComponent comp,
                                       @MagicConstant(intValues = {JComponent.WHEN_IN_FOCUSED_WINDOW,
                                               JComponent.WHEN_FOCUSED,
                                               JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT})
                                       int nWhen,
                                       String sActionName,
                                       int key,
                                       @MagicConstant(flags = {InputEvent.CTRL_DOWN_MASK,
                                               InputEvent.SHIFT_DOWN_MASK, InputEvent.ALT_DOWN_MASK, InputEvent.META_DOWN_MASK,
                                               InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON2_DOWN_MASK, InputEvent.BUTTON3_DOWN_MASK})
                                       int key_mods) {
        comp.getActionMap().remove(sActionName);
        comp.getInputMap(nWhen).remove(KeyStroke.getKeyStroke(key, key_mods));
    }

    /**
     * Hyperlink handler
     */
    public static final HyperlinkListener HYPERLINK_HANDLER = new HyperLinkHandler();

    /**
     * hyperlink implementation
     */
    private static class HyperLinkHandler implements HyperlinkListener
    {
        public void hyperlinkUpdate(HyperlinkEvent e)
        {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            String sURL = e.getDescription();
            Utils.openURL(sURL);
        }
    }

    /**
     * Action for invoking a button
     */
    public static class InvokeButton extends AbstractAction
    {
        JButton button_;

        public InvokeButton(JButton button)
        {
            button_ = button;
        }

        public void actionPerformed(ActionEvent e)
        {
            if (button_ != null && button_.isEnabled())
            {
                button_.doClick(120);
            }
        }
    }

    ////
    //// Layout Helpers
    ////

    // debugging borders
    public static final Border REDBORDER = BorderFactory.createLineBorder(Color.red);
    public static final Border GREENBORDER = BorderFactory.createLineBorder(Color.green);
    public static final Border CYANBORDER = BorderFactory.createLineBorder(Color.cyan);
    public static final Border BLUEBORDER = BorderFactory.createLineBorder(Color.blue);
    public static final Border BLACKBORDER = BorderFactory.createLineBorder(Color.black);
    public static final Border GRAYBORDER = BorderFactory.createLineBorder(Color.darkGray);

    public static JComponent CENTER(JComponent c)
    {
        DDPanel center = DDPanel.CENTER();
        center.add(c);
        return center;
    }

    public static JComponent NORTH(JComponent c)
    {
        DDPanel north = new DDPanel();
        north.add(c, BorderLayout.NORTH);
        return north;
    }

    public static JComponent SOUTH(JComponent c)
    {
        DDPanel south = new DDPanel();
        south.add(c, BorderLayout.SOUTH);
        return south;
    }

    public static JComponent WEST(JComponent c)
    {
        DDPanel west = new DDPanel();
        west.add(c, BorderLayout.WEST);
        return west;
    }

    public static JComponent WEST_SOUTH(JComponent west, JComponent south, int vgap)
    {
        DDPanel swest = new DDPanel();
        swest.add(west, BorderLayout.WEST);
        swest.add(south, BorderLayout.SOUTH);
        BorderLayout layout = (BorderLayout) swest.getLayout();
        layout.setVgap(vgap);
        return swest;
    }

    public static JComponent EAST(JComponent c)
    {
        DDPanel east = new DDPanel();
        east.add(c, BorderLayout.EAST);
        return east;
    }

    /**
     * return true if swing thread
     */
    public static boolean isSwingThread()
    {
        return (SwingUtilities.isEventDispatchThread() || Thread.currentThread() == BaseApp.mainThread_);
    }

    /**
     * Throw exception if not swing thread
     */
    public static void requireSwingThread()
    {
        if (!isSwingThread())
            throw new ApplicationError("Updating from non-swing thread: " + Thread.currentThread().getName());
    }

    /**
     * get first DDWindow parent of this component
     * (will find InternalWindow before the parent Frame)
     */
    public static DDWindow getHelpManager(Component c)
    {
        if (c == null) return null;

        Container p = c.getParent();
        while (p != null && !(p instanceof DDWindow))
        {
            p = p.getParent();
        }
        return (DDWindow) p;
    }

    /**
     * get InternalDialog this component is in
     */
    public static InternalDialog getInternalDialog(Component c)
    {
        if (c == null) return null;

        Container p = c.getParent();
        while (p != null && !(p instanceof InternalDialog))
        {
            p = p.getParent();
        }
        return (InternalDialog) p;
    }

    /**
     * Apply the DD flat icon style shared by DDCheckBox / DDRadioButton to the
     * given component via a 'FlatLaf.style' client property: the box/circle is
     * filled with the component's background (so the parent shows through) and the
     * border + checkmark/dot are drawn in iconColor (the component foreground), so
     * it reads like the surrounding text with no opaque white or accent fill.
     *
     * In display-only mode the icon is pinned to its resting colors so it does not
     * react to hover/press; otherwise FlatLaf's normal hover highlight applies.  The
     * selected indicator (radio dot / checkmark) is also dimmed toward the background
     * so a read-only selection reads as slightly softened without looking disabled (the
     * border stays full strength).  No-op under non-FlatLaf look and feels.
     */
    public static void applyFlatIconStyle(JComponent c, Color iconColor, boolean displayOnly)
    {
        Color iconC = iconColor != null ? iconColor : Color.black;
        String fg = hexColor(iconC);
        String bg = hexColor(c.getBackground());
        // Dot/checkmark color: full strength normally, dimmed toward the background when display-only.
        String mark = displayOnly ? hexColor(blend(iconC, c.getBackground(), 0.4f)) : fg;

        List<String> style = new ArrayList<>(List.of(
                "icon.background:" + bg,
                "icon.borderColor: " + fg,
                "icon.selectedBorderColor: " + fg,
                "icon.checkmarkColor: " + mark,
                "icon.disabledCheckmarkColor: " + mark));

        if (displayOnly)
        {
            style.add("icon.hoverBackground:" + bg);
            style.add("icon.hoverSelectedBackground:" + bg);
            style.add("icon.pressedBackground:" + bg);
            style.add("icon.pressedSelectedBackground:" + bg);
            style.add("icon.selectedBackground:" + bg);
            style.add("icon.hoverBorderColor: " + fg);
            style.add("icon.hoverSelectedBorderColor: " + fg);
        }

        c.putClientProperty("FlatLaf.style", String.join(";", style));
    }

    /** format a color as a FlatLaf #rrggbb hex string */
    public static String hexColor(Color color)
    {
        return String.format("#%06x", color.getRGB() & 0xFFFFFF);
    }

    /** Blend {@code from} toward {@code to} by {@code frac} (0 = from, 1 = to). */
    public static Color blend(Color from, Color to, float frac)
    {
        float f = Math.clamp(frac, 0f, 1f);
        int r = Math.round(from.getRed()   + (to.getRed()   - from.getRed())   * f);
        int g = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * f);
        int b = Math.round(from.getBlue()  + (to.getBlue()  - from.getBlue())  * f);
        return new Color(r, g, b);
    }

    /** Same as {@link #printToImage(JComponent, int, int, boolean)} with no drop shadow. */
    public static BufferedImage printToImage(JComponent c, int width, int height)
    {
        return printToImage(c, width, height, false);
    }

    /**
     * Render a Swing component to an image, scaled down (never up) to fit within width x height
     * while preserving aspect ratio. Uses {@link JComponent#print} rather than {@code paint} so the
     * component renders as it would for printing (no caret blink, correct double-buffer handling),
     * and applies the scale to the Graphics transform so painting happens directly at the target
     * size - text and lines stay crisp instead of being downsampled from a full-size raster.
     *
     * <p>When {@code withShadow} is true the shot is matted on a white background with a soft drop
     * shadow (see {@link #withDropShadow}); the returned image is then larger than width x height by
     * the shadow margin, though the shot itself still fits within width x height.
     */
    public static BufferedImage printToImage(JComponent c, int width, int height, boolean withShadow)
    {
        double w = (double) width / Math.max((double) c.getWidth(), width);
        double h = (double) height / Math.max((double) c.getHeight(), height);
        double dScale = Math.min(w, h);

        BufferedImage image = new BufferedImage(Math.max(1, (int) (c.getWidth() * dScale)),
                Math.max(1, (int) (c.getHeight() * dScale)), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(dScale, dScale);
            c.print(g);

            // Thin gray border around the edge. Reset the transform first so the line is exactly
            // one device pixel wide and flush to the image bounds regardless of the scale above.
            g.setTransform(new AffineTransform());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(SCREENSHOT_BORDER);
            g.drawRect(0, 0, image.getWidth() - 1, image.getHeight() - 1);
        }
        finally
        {
            g.dispose();
        }
        return withShadow ? withDropShadow(image) : image;
    }

    // Drop-shadow tuning (device pixels). MARGIN must exceed BLUR + OFFSET so the blurred
    // shadow fades out fully inside the matted canvas.
    private static final int SHADOW_MARGIN   = 24;
    private static final int SHADOW_BLUR     = 10;
    private static final int SHADOW_OFFSET_X = 0;
    private static final int SHADOW_OFFSET_Y = 6;
    private static final int SHADOW_ALPHA    = 90;

    /**
     * Matte {@code content} on a white background with a soft drop shadow. The shadow is a
     * translucent black rectangle the size of the shot, offset down/right and box-blurred to soften
     * its edges, drawn under the shot. White is needed so the semi-transparent shadow has something
     * to blend against (a transparent canvas would just leave the shadow as faint stray pixels).
     */
    private static BufferedImage withDropShadow(BufferedImage content)
    {
        int cw = content.getWidth();
        int ch = content.getHeight();
        int w = cw + SHADOW_MARGIN * 2;
        int h = ch + SHADOW_MARGIN * 2;

        BufferedImage shadow = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = shadow.createGraphics();
        sg.setColor(new Color(0, 0, 0, SHADOW_ALPHA));
        sg.fillRect(SHADOW_MARGIN + SHADOW_OFFSET_X, SHADOW_MARGIN + SHADOW_OFFSET_Y, cw, ch);
        sg.dispose();
        shadow = boxBlur(shadow, SHADOW_BLUR);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try
        {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(shadow, 0, 0, null);
            g.drawImage(content, SHADOW_MARGIN, SHADOW_MARGIN, null);
        }
        finally
        {
            g.dispose();
        }
        return out;
    }

    /** Box-blur an ARGB image with a (2*radius+1) square kernel; EDGE_NO_OP leaves the clear margin alone. */
    private static BufferedImage boxBlur(BufferedImage src, int radius)
    {
        if (radius < 1) return src;
        int size = radius * 2 + 1;
        float[] kernel = new float[size * size];
        Arrays.fill(kernel, 1f / (size * size));
        ConvolveOp op = new ConvolveOp(new Kernel(size, size, kernel), ConvolveOp.EDGE_NO_OP, null);
        return op.filter(src, null);
    }

    /**
     * Write a BufferedImage to a file, picking the format from the file extension (defaults to png).
     * png is preferred for UI screenshots: it is lossless, so text and thin lines stay sharp, where
     * jpg leaves ringing artifacts around them; jpg is still honored (at high quality) if requested.
     */
    public static void printImageToFile(BufferedImage image, File file)
    {
        String format = formatFor(file);
        try (FileOutputStream out = new FileOutputStream(file))
        {
            if ("jpg".equals(format) || "jpeg".equals(format))
            {
                writeJpeg(image, out, 0.95f);
            }
            else if (!ImageIO.write(image, format, out))
            {
                logger.error("No image writer found for format '{}' ({})", format, file);
            }
        }
        catch (IOException ioe)
        {
            logger.error(Utils.formatExceptionText(ioe));
        }
    }

    /** Lower-case extension of the file, or "png" if it has none. */
    private static String formatFor(File file)
    {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase() : "png";
    }

    /** Write a JPEG at an explicit quality (0..1); JPEG can't store alpha, so callers pass RGB images. */
    private static void writeJpeg(BufferedImage image, FileOutputStream out, float quality) throws IOException
    {
        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(out))
        {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        }
        finally
        {
            writer.dispose();
        }
    }
}
