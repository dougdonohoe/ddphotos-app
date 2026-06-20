/*
 * DDTextArea.java
 *
 * Created on November 16, 2002, 4:06 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.base.*;

import javax.swing.*;
import javax.swing.FocusManager;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.regex.*;

/**
 * @author Doug Donohoe
 */
public class DDTextArea extends JTextArea implements DDTextVisibleComponent,
                                                     DDText,
                                                     DocumentListener, KeyListener,
                                                     FocusListener, MouseListener,
                                                     DDValidatable
{
    //static Logger logger = LogManager.getLogger(DDTextArea.class);

    private Caret cNormal_;
    private boolean bDisplayOnly_ = false;
    private boolean bTabChangesFocus_ = false;
    private Color bgNormal_;
    private Color bgDisplayOnly_;
    private Color bgDisabled_;
    private Color bgError_ = Color.black;
    private Pattern pattern_;
    private boolean bValid_ = true;
    private JScrollPane scroll_ = null;

    /**
     * Creates a new instance of DDTextField, sets name to sName
     */
    public DDTextArea(String sName, String sStyleName)
    {
        super();
        init(sName, sStyleName);
    }

    /**
     * init colors, borders, etc
     */
    private void init(String sName, String sStyleName)
    {
        GuiManager.init(this, sName, sStyleName);
        setLineWrap(true);
        setWrapStyleWord(true);
        bgNormal_ = getBackground();
        cNormal_ = getCaret();
        addKeyListener(this);
        addMouseListener(this);
        addFocusListener(this);
        getDocument().addDocumentListener(this);

        if (Utils.ISMAC) JTextComponent.loadKeymap(getKeymap(), GuiUtils.MAC_CUT_COPY_PASTE, getActions());
    }

    /**
     * Override to fire prop change
     */
    @Override
    public void setText(String sMsg)
    {
        GuiUtils.requireSwingThread();

        super.setText(sMsg);
        firePropertyChange("value", null, null);
    }

    /**
     * Return our type
     */
    public String getType()
    {
        return "textarea";
    }

    /**
     * Set this text area as a display area that:
     * can't take focus, wraps words/lines, is not opaque,
     * can't drag and draw's with antialiasing.
     */
    public void setDisplayOnly(boolean bDisplayOnly)
    {
        bDisplayOnly_ = bDisplayOnly;
        setFocusable(!bDisplayOnly);
        setOpaque(!bDisplayOnly);
        setEditable(!bDisplayOnly);
        setDragEnabled(!bDisplayOnly);
        if (bDisplayOnly)
        {
            GuiUtils.setDoNothingCaret(this);
        }
        else
        {
            setCaret(cNormal_);
        }
        applyBackground(); // apply the mode-appropriate background
    }

    public boolean isDisplayOnly()
    {
        return bDisplayOnly_;
    }

    /**
     * Background used in display-only mode: the panel background as a plain
     * (non-UIResource) color so it blends into the form.  Mirrors
     * DDComboBox.setDisplayOnly() / DDTextField.
     */
    private Color displayOnlyBg()
    {
        if (bgDisplayOnly_ == null)
        {
            Color bg = UIManager.getColor("Panel.background");
            bgDisplayOnly_ = (bg != null)
                    ? new Color(bg.getRed(), bg.getGreen(), bg.getBlue())
                    : bgNormal_;
        }
        return bgDisplayOnly_;
    }

    /**
     * Background used when disabled: DDTextField.DISABLE_BG composited over the
     * normal background, as an opaque color so FlatLaf's fill renders it (mirrors
     * DDTextField.disabledBg()).
     */
    private Color disabledBg()
    {
        if (bgDisabled_ == null)
        {
            Color base = (bgNormal_ != null) ? bgNormal_ : Color.white;
            Color over = DDTextField.DISABLE_BG;
            float a = over.getAlpha() / 255f;
            int r = Math.round(over.getRed()   * a + base.getRed()   * (1 - a));
            int g = Math.round(over.getGreen() * a + base.getGreen() * (1 - a));
            int b = Math.round(over.getBlue()  * a + base.getBlue()  * (1 - a));
            bgDisabled_ = new Color(r, g, b);
        }
        return bgDisabled_;
    }

    /**
     * Apply the background for the current mode/validity.  The text area's
     * border lives on its enclosing scroll pane, and FlatLaf's scroll-pane UI
     * paints the rounded interior using the view's (this area's) background, so
     * we set our background and repaint the scroll pane to pick up the color.
     */
    private void applyBackground()
    {
        Color bg = !isEnabled() ? disabledBg()
                 : bValid_ ? (bDisplayOnly_ ? displayOnlyBg() : bgNormal_)
                 : bgError_;
        if (getBackground() != bg)
        {
            setBackground(bg);
            if (scroll_ != null) scroll_.repaint();
            repaint();
        }
    }

    /**
     * enabled - update background (gray when disabled)
     */
    @Override
    public void setEnabled(boolean b)
    {
        super.setEnabled(b);
        applyBackground();
    }

    @Override
    public void setForeground(Color c)
    {
        super.setForeground(c);
        this.setCaretColor(c);
    }

    public void setErrorBackground(Color c)
    {
        bgError_ = c;
    }

    public Color getErrorBackground()
    {
        return bgError_;
    }

    /**
     * Set whether tab changes focus
     */
    public void setTabChangesFocus(boolean b)
    {
        bTabChangesFocus_ = b;
    }

    /**
     * Get whether tab changes focus
     */
    public boolean getTabChangesFocus()
    {
        return bTabChangesFocus_;
    }

    /**
     * Set scroll pane used with this
     */
    public void setScrollPane(JScrollPane j)
    {
        scroll_ = j;
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

    ////
    //// Key listener
    ////
    private boolean keypressed = false;

    public void keyPressed(KeyEvent e)
    {

        if (bTabChangesFocus_ && e.getKeyCode() == KeyEvent.VK_TAB)
        {
            if (e.isShiftDown())
            {
                FocusManager.getCurrentManager().focusPreviousComponent();
            }
            else
            {
                FocusManager.getCurrentManager().focusNextComponent();
            }
            e.consume();
        }
        else
        {
            keypressed = true;
        }
    }

    /**
     * EMPTY *
     */
    public void keyTyped(KeyEvent e)
    {
    }

    /**
     * used to fire "value" property change for
     * listeners who want keystroke by keystroke notification.
     * Also triggers default button when enter pressed
     */
    public void keyReleased(KeyEvent e)
    {
        if (!keypressed) return; // ignore spurious released events if we didn't see the press
        // this happens sometimes with enter for default button press
        keypressed = false;

        // notify of value change
        firePropertyChange("value", null, e);
    }

    /**
     * Set length limit on this field
     */
    public void setTextLengthLimit(int nLength)
    {
        getDocument().removeDocumentListener(this);
        setDocument(new LengthLimit(nLength));
        getDocument().addDocumentListener(this);
    }

    /*
    * Class used to Limit length
    */
    private class LengthLimit extends PlainDocument
    {
        private final int limit;

        LengthLimit(int limit)
        {
            super();
            this.limit = limit;
        }

        @Override
        public void insertString(int offset, String str, AttributeSet attr)
                throws BadLocationException
        {
            if (str == null) return;

            if (bTabChangesFocus_ && str.length() == 1 && str.charAt(0) == '\t')
            {
                return;
            }

            if ((getLength() + str.length()) <= limit)
            {
                super.insertString(offset, str, attr);
            }
        }
    }

    /**
     * Set regexp to validate
     */
    public void setRegExp(String sPattern)
    {
        if (sPattern == null)
        {
            pattern_ = null;
        }
        else
        {
            pattern_ = Pattern.compile(sPattern);
        }
        regexpValidate();
    }

    /**
     * validate text
     */
    private void regexpValidate()
    {
        if (pattern_ != null)
        {
            String sNew = getText().trim();
            Matcher m = pattern_.matcher(sNew);
            bValid_ = m.matches();
            applyBackground();
        }
    }

    /**
     * Is this valid w.r.t. the regexp?
     */
    public boolean isValidData()
    {
        return bValid_;
    }

    public void addValidationListener(Runnable onChange)
    {
        addPropertyChangeListener("value", _ -> onChange.run());
    }

    ////
    //// DocumentListener methods
    ////

    /**
     * calls regexpValidate()
     */
    public void changedUpdate(DocumentEvent e)
    {
        regexpValidate();
    }

    /**
     * calls regexpValidate()
     */
    public void insertUpdate(DocumentEvent e)
    {
        regexpValidate();
    }

    /**
     * calls regexpValidate()
     */
    public void removeUpdate(DocumentEvent e)
    {
        regexpValidate();
    }

    /**
     * Invoked when a component gains the keyboard focus.
     */
    public void focusGained(FocusEvent e)
    {
        if (!bMouse_ && !isDisplayOnly() && isEnabled() && isEditable()) selectAll();
    }

    /**
     * Invoked when a component loses the keyboard focus.
     */
    public void focusLost(FocusEvent e)
    {
        // make sure value saved when focus lost
        firePropertyChange("value", null, e);
    }

    boolean bMouse_ = false;

    public void mouseClicked(MouseEvent e)
    {
        bMouse_ = false;
    }

    public void mouseEntered(MouseEvent e)
    {
    }

    public void mouseExited(MouseEvent e)
    {
    }

    public void mousePressed(MouseEvent e)
    {
        bMouse_ = true;
    }

    public void mouseReleased(MouseEvent e)
    {
        bMouse_ = false;
    }
}
