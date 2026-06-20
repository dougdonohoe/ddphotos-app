/*
 * DDTextField.java
 *
 * Created on November 16, 2002, 4:06 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.base.*;
import com.donohoedigital.config.DebugConfig;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.regex.*;

/**
 * @author Doug Donohoe
 */
public class DDTextField extends JFormattedTextField implements DDTextVisibleComponent, DDText,
        KeyListener, DocumentListener,
        FocusListener, MouseListener,
        DDCustomHelp, DDValidatable
{
    // disable bg overlay
    public static final Color DISABLE_BG = new Color(204, 204, 204, 178);

    // members
    private boolean bDisplayOnly_ = false;
    private Color bgNormal_;
    private Color bgDisplayOnly_;
    private Color bgDisabled_;
    private Color bgError_ = Color.black;
    private Pattern pattern_;
    private boolean bValid_ = true;
    private JButton defaultOverride_ = null;

    /**
     *
     */
    public DDTextField()
    {
        this(GuiManager.DEFAULT, GuiManager.DEFAULT);
    }

    /**
     * new text given a name
     */
    public DDTextField(String sName)
    {
        this(sName, GuiManager.DEFAULT);
    }

    /**
     * new text with name/style
     */
    public DDTextField(String sName, String sStyleName)
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
        initBG();
        setFocusLostBehavior(JFormattedTextField.PERSIST);
        addKeyListener(this);
        addFocusListener(this);
        addMouseListener(this);
        getDocument().addDocumentListener(this);
        setDisabledTextColor(GuiUtils.COLOR_DISABLED_TEXT);
        super.setOpaque(false);

        if (Utils.ISMAC) JTextComponent.loadKeymap(getKeymap(), GuiUtils.MAC_CUT_COPY_PASTE, getActions());
    }

    /**
     * init bgNormal color
     */
    public void initBG()
    {
        bgNormal_ = getBackground();
    }

    /**
     * Background used in display-only mode: the panel background as a plain
     * (non-UIResource) color so FlatLaf's field fill honors it.  Mirrors
     * DDComboBox.setDisplayOnly() so a read-only field blends into the form
     * instead of showing FlatLaf's white text-field background.
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
     * The valid/normal background for the current mode (gray when display-only,
     * white when editable).
     */
    private Color normalBg()
    {
        return bDisplayOnly_ ? displayOnlyBg() : bgNormal_;
    }

    /**
     * Background used when the field is disabled: DISABLE_BG composited over the
     * normal background, as an opaque color so FlatLaf's field fill renders it.
     */
    private Color disabledBg()
    {
        if (bgDisabled_ == null)
        {
            Color base = (bgNormal_ != null) ? bgNormal_ : Color.white;
            float a = DISABLE_BG.getAlpha() / 255f;
            int r = Math.round(DISABLE_BG.getRed()   * a + base.getRed()   * (1 - a));
            int g = Math.round(DISABLE_BG.getGreen() * a + base.getGreen() * (1 - a));
            int b = Math.round(DISABLE_BG.getBlue()  * a + base.getBlue()  * (1 - a));
            bgDisabled_ = new Color(r, g, b);
        }
        return bgDisabled_;
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
        return "textfield";
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
        setValid(isValidData()); // apply the mode-appropriate background
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

    public void setErrorBackground(Color c)
    {
        bgError_ = c;
    }

    public Color getErrorBackground()
    {
        return bgError_;
    }

    /**
     * Set button to trigger when enter pressed in field.  Overrides
     * default button
     */
    public void setDefaultOverride(JButton b)
    {
        defaultOverride_ = b;
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
    private int keypressed = 0;

    /**
     * note a key pressed
     */
    public void keyPressed(KeyEvent e)
    {
        keypressed++;
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
        // ignore spurious released events if we didn't see the press.
        // this happens sometimes with enter for default button press.
        // we use a counter since you can get press-press-release-release.
        // Don't recall issue where we get a keyReleased w/out a key-press,
        // and it might not happen anymore (JDD - 3/14/05)
        if (keypressed == 0)
        {
            return;
        }

        keypressed--;

        // notify of value change
        firePropertyChange("value", null, e);

        // if enter key, activate default button
        if (e.getKeyCode() == KeyEvent.VK_ENTER)
        {
            JButton button = defaultOverride_;
            if (button == null)
            {
                JRootPane root = SwingUtilities.getRootPane(this);
                if (root != null)
                {
                    button = root.getDefaultButton();
                }
            }

            if (button != null && button.isEnabled())
            {
                button.doClick(120);
            }
        }
    }

    /**
     * Set length limit on this field
     */
    public void setTextLengthLimit(int nLength)
    {
        setDocument(new LengthLimit(nLength));
    }

    /**
     * set document, handle listeners
     */
    @Override
    public void setDocument(Document doc)
    {
        Document current = getDocument();
        if (current != null)
        {
            current.removeDocumentListener(this);
        }
        super.setDocument(doc);
        doc.addDocumentListener(this);
    }

    /*
     * Class used to Limit length
     */
    private static class LengthLimit extends PlainDocument
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
        // for testing error handling in modal dialogs ... just type BOOM! in any text field
        if (DebugConfig.isTestingOn() && getText().contains("BOOM!")) {
            throw new RuntimeException("BOOM!");
        }

        if (pattern_ != null)
        {
            String sNew = getText().trim();
            Matcher m = pattern_.matcher(sNew);

            setValid(m.matches() && customValidate());
        }
    }

    /**
     * Called after regexp passes; delegates to customValidator_ if set.
     * Use setCustomValidator() rather than subclassing.
     */
    private boolean customValidate()
    {
        return customValidator_ == null || customValidator_.test(getText().trim());
    }

    private java.util.function.Predicate<String> customValidator_;

    public void setCustomValidator(java.util.function.Predicate<String> validator)
    {
        customValidator_ = validator;
        regexpValidate();
    }

    /**
     * enabled - change bg
     */
    @Override
    public void setEnabled(boolean b)
    {
        super.setEnabled(b);
        setValid(isValidData());
    }

    /**
     * Set valid - for use when patterns not used
     */
    public void setValid(boolean b)
    {
        Color desired;

        if (b)
        {
            bValid_ = true;
            desired = normalBg();
        }
        else
        {
            bValid_ = false;
            desired = bgError_;
        }

        if (!isEnabled()) desired = disabledBg();

        if (getBackground() != desired)
        {
            setBackground(desired);
            repaint();
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

    ////
    //// Custom help
    ////

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
