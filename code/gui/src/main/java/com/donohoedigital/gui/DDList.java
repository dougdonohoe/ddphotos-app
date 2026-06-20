/*
 * DDList.java
 */

package com.donohoedigital.gui;

import javax.swing.*;

/**
 * A JList that participates in the DD styling/help framework.  Colors, font and
 * selection colors are driven by styles.xml (type "list"), and hover help is
 * supported via {@code list.{name}.help} or {@link #setHelpText}.
 *
 * @author Doug Donohoe
 */
public class DDList<E> extends JList<E> implements DDTextVisibleComponent, DDListComponent, DDCustomHelp
{
    /**
     * Creates a new instance of DDList, sets name to sName
     */
    public DDList(String sName, String sStyleName)
    {
        super();
        GuiManager.init(this, sName, sStyleName);
    }

    /**
     * Creates a new instance of DDList backed by the given model
     */
    public DDList(ListModel<E> model, String sName, String sStyleName)
    {
        super(model);
        GuiManager.init(this, sName, sStyleName);
    }

    public String getType() {
        return "list";
    }

    ///
    /// Custom help
    ///

    private String sHelp_;

    public String getHelpText()
    {
        return sHelp_;
    }

    public void setHelpText(String s)
    {
        sHelp_ = s;
    }
}
