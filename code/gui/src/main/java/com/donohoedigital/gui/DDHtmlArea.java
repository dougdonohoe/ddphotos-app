/*
 * DDHtmlArea.java
 *
 * Created on November 16, 2002, 4:06 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.base.*;
import org.apache.logging.log4j.*;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.text.*;
import javax.swing.text.html.*;
import java.awt.*;

/**
 * @author Doug Donohoe
 */
public class DDHtmlArea extends JEditorPane implements DDTextVisibleComponent
{
    static Logger logger = LogManager.getLogger(DDHtmlArea.class);

    private DDHtmlEditorKit htmlKit_;
    private boolean bDisplayOnly_ = false;

    /**
     * Creates a new instance of DDTextField, sets name to sName
     */
    public DDHtmlArea(String sName, String sStyleName)
    {
        init(sName, sStyleName,null);
    }

    /**
     * Create an HTML border with given gaps
     */
    public static Border createHtmlAreaBorder(int top, int left, int bottom, int right) {
        Border bb = BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.lightGray, Color.gray);
        return BorderFactory.createCompoundBorder(
                bb,
                BorderFactory.createEmptyBorder(top, left, bottom, right)
        );
    }

    /**
     * init colors, borders, etc
     */
    private void init(String sName, String sStyleName, DDHtmlArea styleSheetProto)
    {
        StyleSheet proto = null;
        if (styleSheetProto != null)
        {
            proto = styleSheetProto.htmlKit_.getStyleSheet();
        }
        htmlKit_ = new DDHtmlEditorKit(proto);
        setEditorKit(htmlKit_);
        GuiManager.init(this, sName, sStyleName);
        setDisplayOnly(true);
        setBorder(createHtmlAreaBorder(0, 2, 0, 2));
        if (proto == null)
        {
            setStyles();
        }

        if (Utils.ISMAC) JTextComponent.loadKeymap(getKeymap(), GuiUtils.MAC_CUT_COPY_PASTE, getActions());
    }

    public HTMLDocument getHtmlDocument()
    {
        return (HTMLDocument) getDocument();
    }

    /**
     * Set font/color from this widget into HTML style sheet
     */
    private void setStyles()
    {
        StyleSheet sheet = htmlKit_.getStyleSheet();
        Font font = getFont();
        Color bg = getForeground();

        // create rule like body { font-family: Lucida Sans Regular; font-size: 12pt; color: #ffffff}
        // which sets font for entire HTML body
        StringBuilder sb = new StringBuilder();
        sb.append("body {");
        sb.append("font-family: ");
        sb.append(font.getFamily());
        sb.append("; ");
        sb.append("font-size: ");
        sb.append(font.getSize());
        sb.append("; color: ");
        sb.append(Utils.getHtmlColor(bg));
        sb.append("}");
        // BUG 256 - catch NPE to handle bad fonts (typically on Mac)
        // need to see if there is a way to do HTML font without using
        // style sheets.  Sigh.
        try
        {
            sheet.addRule(sb.toString());
        }
        catch (NullPointerException npe)
        {
            logger.warn("Caught NPE trying to add rule: {}", sb.toString());
            logger.warn(Utils.formatExceptionText(npe));
        }
    }

    /**
     * Return our type
     */
    public String getType()
    {
        return "htmlarea";
    }

    @Override
    public void setText(String s)
    {
        GuiUtils.requireSwingThread();

        super.setText(s);
    }

    /**
     * Set this HTML area as a display area that:
     * can't take focus, is not opaque,
     * can't drag and draw's with antialiasing.
     * Set to true by default.
     */
    public void setDisplayOnly(boolean bDisplayOnly)
    {
        bDisplayOnly_ = bDisplayOnly;
        setFocusable(!bDisplayOnly);
        setOpaque(!bDisplayOnly);
        setEditable(!bDisplayOnly);
        setDragEnabled(!bDisplayOnly);
    }

    public boolean isDisplayOnly()
    {
        return bDisplayOnly_;
    }

    @Override
    public void setForeground(Color c)
    {
        super.setForeground(c);
        this.setCaretColor(c);
    }

    /**
     * Swing doesn't exactly do semi-transparent correctly unless
     * you start with the highest parent w/ no transparency
     */
    @Override
    public void repaint()
    {
        if (!GuiUtils.repaint(this)) super.repaint();
    }
}
