/*
 * DDLabelBorder.java
 *
 * Created on April 17, 2002, 3:47 PM
 */

package com.donohoedigital.gui;


import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

/**
 *
 * @author  Doug Donohoe
 */
public class DDLabelBorder extends JPanel implements DDHasLabelComponent 
{
    TitledBorder border_;
    String sText_;

    public DDLabelBorder(String sName, String sStyle)
    {
        super();
        init(sName, sStyle);
    }

    private void init(String sName, String sStyle)
    {
        setOpaque(false);
        setLayout(new BorderLayout());
        GuiManager.init(this, sName, sStyle);
        Border border = BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(getBackground().brighter(),getBackground().darker()),
                BorderFactory.createEmptyBorder(0, 2, 1, 2));
        border_ = new TitledBorder(
                border, 
                sText_,
                TitledBorder.LEFT, TitledBorder.TOP,
                getFont(), getForeground());
        setBorder(border_);
    }
        
    public void setEnabled(boolean b)
    {
        // set disabled color
        if (b) border_.setTitleColor(getForeground());
        else border_.setTitleColor(GuiUtils.COLOR_DISABLED_TEXT);

        super.setEnabled(b);
    }

    /**
     * get border title text
     */
    public String getText()
    {
        return sText_;
    }
    
    /**
     * Set text of border title
     */
    public void setText(String s)
    {
        sText_ = s;
        if (border_ != null) border_.setTitle(sText_);
    }

    /**
     * Swing doesn't exactly do semi-transparent correctly unless
     * you start with the highest parent w/ no transparency
     */
    public void repaint()
    {
        if (!GuiUtils.repaint(this)) super.repaint();
    }

    public String getType()
    {
        return "labelborder";
    }

    public void setPreferredHeight(int height) {
        setPreferredSize(new Dimension((int) getPreferredSize().getWidth(), height));
    }

    public void setPreferredWidth(int width) {
        setPreferredSize(new Dimension(width, (int) getPreferredSize().getHeight()));
    }
}
