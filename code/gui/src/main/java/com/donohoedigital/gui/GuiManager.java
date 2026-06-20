/*
 * GuiManager.java
 *
 * Created on November 17, 2002, 3:04 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.config.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * @author Doug Donohoe
 */
public class GuiManager implements MouseListener
{
    //static Logger logger = LogManager.getLogger(GuiManager.class);

    private static final GuiManager manager_ = new GuiManager();
    public static final String DEFAULT = "default";
    private static final String EMPTY = "";

    /**
     * Creates a new instance of GuiManager
     */
    private GuiManager()
    {
    }

    /**
     * init component with colors and fonts
     */
    public static void init(DDComponent dd, String sName, String sStyleName)
    {
        String sType = dd.getType();
        dd.setName(sName);

        setDefaultTooltip(dd);

        if (!(dd instanceof DDImageButton) &&
            !(dd instanceof DDScrollPane))
        {
            dd.setBackground(StylesConfig.getColor(sStyleName + "." + sType + ".bg", dd.getBackground()));

            if (!(dd instanceof DDSplitPane))
            {
                dd.setForeground(StylesConfig.getColor(sStyleName + "." + sType + ".fg", dd.getForeground()));
            }
        }

        if (dd instanceof DDTextVisibleComponent text && !(dd instanceof DDImageButton))
        {
            text.setFont(StylesConfig.getFont(sStyleName + "." + sType, text.getFont()));
        }

        if (dd instanceof DDTextField text)
        {
            text.setErrorBackground(StylesConfig.getColor(sStyleName + "." + sType + ".error.bg",
                                                          text.getErrorBackground()));
        }

        if (dd instanceof DDTextArea text)
        {
            text.setErrorBackground(StylesConfig.getColor(sStyleName + "." + sType + ".error.bg",
                                                          text.getErrorBackground()));
        }

        setLabel(dd, sName);

        if (dd instanceof DDHasLabelComponent label && !(dd instanceof DDImageButton))
        {
            label.setText(PropertyConfig.getStringProperty(sType + "." + sName + ".label", EMPTY));
        }

        if (dd instanceof DDExtendedComponent extended && !(dd instanceof DDImageButton))
        {
            extended.setMouseOverForeground(StylesConfig.getColor(sStyleName + "." + sType + ".mouseover.fg",
                                                                  extended.getMouseOverForeground()));
        }

        if (dd instanceof DDListComponent list)
        {
            list.setSelectionForeground(StylesConfig.getColor(sStyleName + "." + sType + ".selection.fg",
                                                              list.getSelectionForeground()));
            list.setSelectionBackground(StylesConfig.getColor(sStyleName + "." + sType + ".selection.bg",
                                                              list.getSelectionBackground()));
        }

        if (dd instanceof DDCheckBox box)
        {
            box.setCheckBoxColor(StylesConfig.getColor(sStyleName + "." + sType + ".check", box.getForeground()));
        }

        if (dd instanceof DDRadioButton radio)
        {
            radio.setDotColor(StylesConfig.getColor(sStyleName + "." + sType + ".dot", radio.getForeground()));
        }

        if (dd instanceof DDSplitPane sp)
        {
            sp.setThumbFocusColor(StylesConfig.getColor(sStyleName + "." + sType + ".focus", null));
        }

        if (dd instanceof DDSlider sl)
        {
            sl.setThumbFocusColor(StylesConfig.getColor(sStyleName + "." + sType + ".thumb.focus", null));
            sl.setThumbBackgroundColor(StylesConfig.getColor(sStyleName + "." + sType + ".thumb.bg", null));
        }

        if (dd instanceof DDScrollPane scroll)
        {
            JViewport viewport = scroll.getViewport();
            viewport.setOpaque(true);
            viewport.setBackground(StylesConfig.getColor(sStyleName + "." + sType + ".bg", viewport.getBackground()));
        }

        if (dd instanceof DDTabbedPane tab)
        {
            Color color = StylesConfig.getColor(sStyleName + "." + sType + ".selected");
            if (color != null) tab.setSelectedTabColor(color);
        }

        dd.removeMouseListener(manager_); // in case of multiple calls to init, remove and re-add
        dd.addMouseListener(manager_);
    }

    /**
     * Reset the label using given params.  Assumes label definition has {0} ... {N} components.
     */
    public static void setLabelAsMessage(DDHasLabelComponent label, Object... params)
    {
        label.setText(PropertyConfig.getMessage(label.getType() + "." + label.getName() + ".label", params));
    }

    /**
     * set label
     */
    private static void setLabel(DDComponent dd, String sName)
    {
        String sType = dd.getType();
        if (dd instanceof DDHasLabelComponent label && !(dd instanceof DDImageButton))
        {
            label.setText(PropertyConfig.getStringProperty(sType + "." + sName + ".label", EMPTY));
        }

        if (dd instanceof AbstractButton button)
        {
            String sMnemonic = PropertyConfig.getStringProperty(sType + "." + sName + ".mnemonic", null, false);
            if (sMnemonic != null && !sMnemonic.isEmpty())
            {
                button.setMnemonic(sMnemonic.charAt(0));
            }
        }
    }

    /**
     * rename and update label
     */
    public static void rename(DDComponent dd, String sName)
    {
        dd.setName(sName);
        setLabel(dd, sName);
    }

    // small perf improvement for below
    private static final StringBuilder sbHelpName = new StringBuilder();

    /**
     * Get default help message
     */
    public static String getDefaultHelp(DDComponent source)
    {
        String sHelp;
        sbHelpName.setLength(0);
        sbHelpName.append(source.getType());
        sbHelpName.append(".");
        sbHelpName.append(source.getName());
        sbHelpName.append(".help");
        sHelp = PropertyConfig.getStringProperty(sbHelpName.toString(),
                                                 null, false);
        return sHelp;
    }

    /**
     * set tooltip message
     */
    private static void setDefaultTooltip(DDComponent source)
    {
        String sHelp;
        sbHelpName.setLength(0);
        sbHelpName.append(source.getType());
        sbHelpName.append(".");
        sbHelpName.append(source.getName());
        sbHelpName.append(".tooltip");
        sHelp = PropertyConfig.getStringProperty(sbHelpName.toString(),
                                                 null, false);
        if (sHelp != null)
        {
            source.setToolTipText(sHelp);
        }
    }

    ////
    //// MouseListener
    ////

    /**
     * when get mouse entered, set help text
     */
    public void mouseEntered(MouseEvent e)
    {
        DDComponent source = GuiUtils.getDDComponent(e.getSource());

        if (source instanceof Component)
        {
            DDWindow window = GuiUtils.getHelpManager(((Component) source));
            if (window != null)
            {
                window.showHelp(source);
            }
        }
    }

    /**
     * Empty
     */
    public void mouseExited(MouseEvent e)
    {
    }

    /**
     * Empty
     */
    public void mouseClicked(MouseEvent e)
    {
    }

    /**
     * Empty
     */
    public void mousePressed(MouseEvent e)
    {
    }

    /**
     * Empty
     */
    public void mouseReleased(MouseEvent e)
    {
    }

}
