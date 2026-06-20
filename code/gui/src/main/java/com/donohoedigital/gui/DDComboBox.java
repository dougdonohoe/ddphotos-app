/*
 * DDComboBox.java
 *
 * Created on November 12, 2002, 4:03 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.config.DataElement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

/*    
*
* @author  Doug Donohoe
*/
public class DDComboBox<E> extends JComboBox<E> implements
            DDTextVisibleComponent,DDExtendedComponent,DDListComponent
{
    private final DataElement<E> elem_;
    DefaultComboBoxModel<E> model_;
    Color cMouseOverForeground_;
    Color cSelectionForeground_;
    Color cSelectionBackground_;
    private boolean bRequired_ = true;
    private boolean bDisplayOnly_ = false;
    private MouseListener[] savedArrowListeners_;
    private Color savedBackground_;

    /**
     * Creates a new instance of DDTextField, sets name to sName
     */
    public DDComboBox(DataElement<E> element, String sStyleName)
    {
        super();
        elem_ = element;

        List<E> values = element.getListValues();
        if (values != null && !values.isEmpty()) {
            resetValues();
        }

        init(element.getName(), sStyleName);
    }

    /**
     * init gui
     */
    private void init(String sName, String sStyleName)
    {
        setRenderer(new DDComboBoxRenderer(this));
        GuiManager.init(this, sName, sStyleName);
        setOpaque(false);
    }

    /**
     *  Set whether this is required (a selection must be made)
     */
    public void setRequired(boolean b)
    {
        bRequired_ = b;
    }

    /**
     * is required? (true by default)
     */
    public boolean isRequired()
    {
        return bRequired_;
    }

    /**
     * Display-only mode: the combo looks like a normal (enabled) combo, but is
     * not interactive - no popup, no focus, no selection change.  This mirrors
     * the display-only behavior of DDTextField / DDCheckBox so read-only combos
     * are not greyed out.
     */
    public void setDisplayOnly(boolean b)
    {
        bDisplayOnly_ = b;
        setFocusable(!b);
        setArrowInteractive(!b);

        // FlatLaf paints the combo's field AND arrow-button area with an opaque
        // white background, which a non-opaque setting won't suppress.  Re-color
        // both to the panel background so a read-only combo blends in like a
        // display-only text field instead of showing a white box:
        //  - the field uses comboBox.getBackground() (when it's not a UIResource)
        //  - the arrow area uses the UI's "buttonBackground" (a FlatLaf style key)
        // Both no-op under non-FlatLaf look & feels.
        if (b)
        {
            Color bg = UIManager.getColor("Panel.background");
            if (bg != null)
            {
                if (savedBackground_ == null) savedBackground_ = getBackground();
                // plain (non-UIResource) color so FlatLaf's field fill honors it
                setBackground(new Color(bg.getRed(), bg.getGreen(), bg.getBlue()));
                putClientProperty("FlatLaf.style",
                        String.format("buttonBackground: #%06x", bg.getRGB() & 0xFFFFFF));
            }
        }
        else
        {
            if (savedBackground_ != null)
            {
                setBackground(savedBackground_);
                savedBackground_ = null;
            }
            putClientProperty("FlatLaf.style", null);
        }
    }

    /**
     * Is this combo display-only?
     */
    public boolean isDisplayOnly()
    {
        return bDisplayOnly_;
    }

    /**
     * Suppress mouse interaction (popup toggle) when display-only, while still
     * letting enter/exit through so help-text listeners keep firing.
     */
    @Override
    protected void processMouseEvent(MouseEvent e)
    {
        if (bDisplayOnly_)
        {
            switch (e.getID())
            {
                case MouseEvent.MOUSE_ENTERED:
                case MouseEvent.MOUSE_EXITED:
                    break; // allow these through so help text listeners still fire
                default:
                    return; // suppress press/release/click/etc.
            }
        }
        super.processMouseEvent(e);
    }

    /**
     * Block the keyboard path that opens the popup
     */
    @Override
    public void setPopupVisible(boolean v)
    {
        if (bDisplayOnly_ && v) return;
        super.setPopupVisible(v);
    }

    /**
     * The arrow button is a child component with its own popup mouse listeners,
     * so suppressing mouse events on the combo body isn't enough.  Remove (and
     * later restore) those listeners so the arrow won't open the popup - without
     * disabling the button, which would grey it out.
     */
    private void setArrowInteractive(boolean interactive)
    {
        JButton arrow = findArrowButton();
        if (arrow == null) return;
        if (!interactive)
        {
            if (savedArrowListeners_ == null)
            {
                savedArrowListeners_ = arrow.getMouseListeners();
                for (MouseListener ml : savedArrowListeners_) arrow.removeMouseListener(ml);
            }
        }
        else if (savedArrowListeners_ != null)
        {
            for (MouseListener ml : savedArrowListeners_) arrow.addMouseListener(ml);
            savedArrowListeners_ = null;
        }
    }

    /**
     * Find the combo's arrow button child (created by the look and feel)
     */
    private JButton findArrowButton()
    {
        for (Component c : getComponents())
        {
            if (c instanceof JButton) return (JButton) c;
        }
        return null;
    }

    /**
     * Set foreground color to use when mouse over button
     */
    public void setMouseOverForeground(Color c)
    {
        cMouseOverForeground_ = c;
    }
    
    /**
     * Get foreground color to use when mouse over button
     */
    public Color getMouseOverForeground()
    {
        return cMouseOverForeground_;
    }
    
    /**
     * Set background used when an item is selected
     */
    public Color getSelectionBackground() {
        return cSelectionBackground_;
    }
    
    /**
     * Set foreground used when an item is selected
     */
    public Color getSelectionForeground() {
        return cSelectionForeground_;
    }
    
    /**
     * Get background for selected items
     */
    public void setSelectionBackground(Color c) {
        cSelectionBackground_ = c;
    }
    
    /**
     * Get foreground for selected items
     */
    public void setSelectionForeground(Color c) {
        cSelectionForeground_ = c;
    }
    
    
    /**
     * Override to remember last value before changing
     */
    public void setSelectedItem(Object o)
    {
        rememberLast();
        super.setSelectedItem(o);
    }
    
    /**
     * remember last selection
     */
    private Object oLast_ = null;
    private void rememberLast()
    {
        oLast_ = getSelectedItem();
    }

    /**
     * Get type
     */
    public String getType() {
        return "combobox";
    }

    /**
     * Set values for use in the combo box
     */
    public void setValues(List<? extends E> values)
    {
        boolean sortable = true;

        DDVector<E> values_ = new DDVector<>(values.size());
        for (E value : values) {
            if (!(value instanceof Comparable)) {
                sortable = false;
            }
            values_.addElement(value);
        }
        if (sortable)
        {
            values_.sort();
        }
        model_ = new DefaultComboBoxModel<>(values_);
        setModel(model_);
    }

    /**
     * is valid?  Returns true if an item is selected and
     * this is required.  If not required, always returns true.
     */
    public boolean isValidData()
    {
        if (bRequired_) return getSelectedItem() != null;
        return true;
    }


    /**
     * Return display value for given object
     */
    public String getDisplayValue(Object oValue)
    {
        if (oValue == null) return "";
        return elem_.getDisplayValue(oValue);
    }

    /**
     * Return selected display value
     */
    public String getSelectedDisplayValue()
    {
        Object selected = getSelectedItem();
        return elem_.getDisplayValue(selected);
    }
    
    /**
     * Return last selected value
     */
    public Object getLastSelectedValue()
    {
        return oLast_;
    }

    /**
     * Return values used by data element
     */
    public List<E> getDefaultValues()
    {
        return elem_.getListValues();
    }
    
    /**
     * Reset values to original data element values
     */
    public void resetValues()
    {
        setValues(getDefaultValues());
    }

    /**
     * Swing doesn't exactly do semi-transparent correctly unless
     * you start with the highest parent w/ no transparency
     */
    public void repaint(long tm, int x, int y, int width, int height)
    {
        if (!GuiUtils.repaint(this, x, y, width, height)) super.repaint(tm, x, y, width, height);
    }
    
    /**
     * Add mouse listener to this and children
     */
    public void addMouseListener(MouseListener listener)
    {
        if (listener instanceof GuiManager || listener instanceof OptionCombo)
        {
            GuiUtils.addMouseListenerChildren(this, listener);
        }
        super.addMouseListener(listener);
    }   
    
    /**
     * Remove mouse listener from this and children
     */
    public void removeMouseListener(MouseListener listener)
    {
        if (listener instanceof GuiManager || listener instanceof OptionCombo)
        {
            GuiUtils.removeMouseListenerChildren(this, listener);
        }
        super.removeMouseListener(listener);
    } 
    
    /**
     * Sortable vector
     */
    private static class DDVector<E> extends Vector<E>
    {
        DDVector(int n)
        {
            super(n);
        }
        
        public void sort()
        {
            Object[] data = toArray();
            Arrays.sort(data);
            elementData = data;
            elementCount = data.length;
        }
    }
}
