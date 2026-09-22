/*
 * OptionTextArea.java
 *
 * Created on April 16, 2003, 2:01 PM
 */

package com.donohoedigital.gui;

import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.PropertyConfig;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * A multi-line text option, in one of two modes:
 *
 * <ul>
 * <li><b>Row</b> (the default): a single read-only row showing the text, cut off with an ellipsis
 *     when it doesn't fit, beside a pencil button.  The button hands the option to the
 *     {@link #setEditHandler edit handler}, which is expected to open a larger editor and call
 *     {@link #setText} with the result.  The full text is in the row's tooltip.</li>
 * <li><b>Inline</b>: a wrapping text area edited in place, for places that show many of these at
 *     once (the photogen caption grid).</li>
 * </ul>
 *
 * @author  donohoe
 */
public class OptionTextArea extends DDOption implements PropertyChangeListener
{
    //static Logger logger = LogManager.getLogger(OptionTextArea.class);

    /** Width of the row's tooltip, beyond which its text wraps. */
    private static final int TOOLTIP_WIDTH = 400;

    private final DDLabel label_;
    private final String sDefault_;
    private final int nLengthLimit_;
    private final String sRegExp_;

    // inline mode
    private final DDTextArea text_;

    // row mode
    private final DisplayField field_;
    private final DDButton editBtn_;
    private final Pattern pattern_;
    private String value_ = "";
    private boolean bDisplayOnly_;
    private Consumer<OptionTextArea> editHandler_;

    /**
     * Creates a new instance of OptionTextArea in row mode.  {@code nRows} is ignored in this mode.
     */
    public OptionTextArea(String sPrefNode, String sName, String sStyle,
                          String sBevelStyle, TypedHashMap map, int nLengthLimit, String sRegExp, int nRows, int nWidth)
    {
        this(sPrefNode, sName, sStyle, sBevelStyle, map, nLengthLimit, sRegExp, nRows, nWidth, false);
    }

    /**
     * Creates a new instance of OptionTextArea, inline or in row mode
     */
    public OptionTextArea(String sPrefNode, String sName, String sStyle,
                          String sBevelStyle, TypedHashMap map, int nLengthLimit, String sRegExp, int nRows, int nWidth,
                          boolean bInline)
    {
        super(sPrefNode, sName, sStyle, map);
        sDefault_ = PropertyConfig.getRequiredStringProperty(getDefaultKey());
        nLengthLimit_ = nLengthLimit;
        sRegExp_ = sRegExp;

        // base
        setBorderLayoutGap(0, 8);

        // label
        label_ = new DDLabel(GuiManager.DEFAULT, STYLE);
        label_.setText(getLabel());
        label_.addMouseListener(this);

        if (bInline)
        {
            field_ = null;
            editBtn_ = null;
            pattern_ = null;
            text_ = createInline(sBevelStyle, nRows, nWidth);
            label_.setVerticalAlignment(SwingConstants.TOP);
        }
        else
        {
            text_ = null;
            pattern_ = sRegExp != null ? Pattern.compile(sRegExp) : null;
            field_ = new DisplayField(STYLE);
            editBtn_ = DDIconButtons.iconButton("edittextarea", STYLE, DDIconButtons.EDIT);
            add(createRow(nWidth), BorderLayout.CENTER);

            // set text, save to map
            resetToPrefs();
            saveToMap();
        }

        add(label_, BorderLayout.WEST);
    }

    /**
     * Inline mode: a wrapping text area in a scroll pane
     */
    private DDTextArea createInline(String sBevelStyle, int nRows, int nWidth)
    {
        DDTextArea text = new DDTextArea(GuiManager.DEFAULT, STYLE);
        text.setRows(nRows);
        text.setTextLengthLimit(nLengthLimit_);
        if (sRegExp_ != null) text.setRegExp(sRegExp_);
        Dimension pref = text.getPreferredSize(); // get size before set text

        // set text, save to map
        text.setText(prefs_.get(sName_, sDefault_));
        map_.setString(sName_, text.getText().trim());

        // create scroll and add listeners
        JScrollPane scroll = new DDScrollPane(text, STYLE, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(nWidth,pref.height+4));
        scroll.setOpaque(false);
        // FlatLaf draws a text area's border (and focus ring) from its scroll
        // pane, not the area; DDScrollPane clears that border, so restore it here
        // for parity with DDTextField.  Scoped to this option (shared DDScrollPane
        // left unchanged).
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        text.setScrollPane(scroll);
        text.setTabChangesFocus(true);
        if (sBevelStyle != null) text.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
        text.addPropertyChangeListener("value", this);
        text.addMouseListener(this);
        text.setWrapStyleWord(true);
        text.setLineWrap(true);
        text.setCaretPosition(0);

        add(scroll, BorderLayout.CENTER);
        return text;
    }

    /**
     * Row mode: the display field and its edit button, together {@code nWidth} wide so the row
     * lines up with the text fields around it.
     */
    private JComponent createRow(int nWidth)
    {
        int gap = 4;
        GuiUtils.setPreferredWidth(field_, nWidth - editBtn_.getPreferredSize().width - gap);
        field_.addPropertyChangeListener("value", this);
        field_.addMouseListener(this);
        field_.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (isEditable()) edit();
            }
        });
        // the ellipsis depends on the width, so redo it whenever that changes
        field_.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                updateDisplay();
            }
        });
        editBtn_.addActionListener(_ -> edit());

        DDPanel row = new DDPanel();
        row.setBorderLayoutGap(0, gap);
        row.add(field_, BorderLayout.CENTER);
        row.add(editBtn_, BorderLayout.EAST);
        return row;
    }

    /**
     * Get the text area.  Inline mode only: row mode has no text area, so asking for one there
     * is a programming error.
     */
    public DDTextArea getTextArea()
    {
        if (text_ == null) throw new IllegalStateException("getTextArea() is only available in inline mode");
        return text_;
    }

    /**
     * Get the full text
     */
    public String getText()
    {
        return text_ != null ? text_.getText() : value_;
    }

    /**
     * Set the full text
     */
    public void setText(String s)
    {
        if (text_ != null)
        {
            text_.setText(s);
            return;
        }
        value_ = s != null ? s : "";
        updateDisplay(); // fires "value" on the field, which lands in propertyChange()
    }

    /**
     * Sets what the edit button does in row mode, typically opening an editor that calls
     * {@link #setText} on save.
     */
    public void setEditHandler(Consumer<OptionTextArea> handler)
    {
        editHandler_ = handler;
    }

    /** Maximum length of the text */
    public int getLengthLimit()
    {
        return nLengthLimit_;
    }

    /** Regular expression the trimmed text must match, or null */
    public String getRegExp()
    {
        return sRegExp_;
    }

    /**
     * Get the label
     */
    public JComponent getLabelComponent()
    {
        return label_;
    }

    /**
     * Is valid?
     */
    public boolean isValidData()
    {
        return text_ != null ? text_.isValidData() : field_.isValidData();
    }

    /**
     * Set display only
     */
    public void setDisplayOnly(boolean b)
    {
        if (text_ != null)
        {
            text_.setDisplayOnly(b);
            return;
        }
        bDisplayOnly_ = b;
        field_.setDisplayOnly(b);
        updateEditable();
    }

    /**
     * set disabled
     */
    public void setEnabled(boolean b)
    {
        setEnabledEmbedded(b);
        label_.setEnabled(b);
    }

    /**
     * Is enabled?
     */
    public boolean isEnabled()
    {
        return text_ != null ? text_.isEnabled() : field_.isEnabled();
    }

    /**
     * Only disabled spinner
     */
    public void setEnabledEmbedded(boolean b)
    {
        if (text_ != null)
        {
            text_.setEnabled(b);
            return;
        }
        field_.setEnabled(b);
        updateEditable();
    }

    /**
     * text field change
     */
    public void propertyChange(PropertyChangeEvent evt)
    {
        fireStateChanged(); // for validation listeners
        if (!isValidData()) return;
        prefs_.put(sName_, getText().trim());
        saveToMap();
    }


    /**
     * Save value to map
     */
    public void saveToMap()
    {
        map_.setString(sName_, getText().trim());
    }

    /** reset to default value
     *
     */
    public void resetToDefault()
    {
        setText(sDefault_);
    }

    public void resetToPrefs()
    {
        setText(prefs_.get(sName_, sDefault_));
    }

    /**
     * reset to value in map
     */
    public void resetToMap()
    {
        setText(map_.getString(sName_, sDefault_));
    }

    //
    // Row mode
    //

    private boolean isEditable()
    {
        return !bDisplayOnly_ && field_.isEnabled();
    }

    private void edit()
    {
        if (isEditable() && editHandler_ != null) editHandler_.accept(this);
    }

    private void updateEditable()
    {
        boolean editable = isEditable();
        editBtn_.setEnabled(editable);
        field_.setCursor(editable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }

    /**
     * Shows the value on one line, cut to fit with an ellipsis, and puts the whole of it in the
     * tooltip.  Validity is set before the text so listeners woken by the text see it.
     */
    private void updateDisplay()
    {
        String flat = value_.strip().replaceAll("\\s*\\R\\s*", " ");
        Insets in = field_.getInsets();
        int avail = field_.getWidth() - in.left - in.right;
        String shown = avail > 0 ? GuiUtils.elideRight(flat, field_, avail) : flat;

        field_.setValid(pattern_ == null || pattern_.matcher(value_.trim()).matches());
        field_.setText(shown);
        field_.setCaretPosition(0);
        field_.setToolTipText(value_.isBlank() ? null : tooltipHtml(value_.strip()));
    }

    /** The full text as wrapping HTML, escaped since the value may itself be HTML. */
    private static String tooltipHtml(String s)
    {
        String escaped = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                          .replaceAll("\\R", "<br>");
        return "<html><body style='width: " + TOOLTIP_WIDTH + "px'>" + escaped + "</body></html>";
    }

    /**
     * The row's text: never editable, never focused and without a caret or selection - clicks go
     * to the editor instead.  Display-only still switches between the panel color and white, so
     * the row reads the same as the text fields around it.
     */
    private static class DisplayField extends DDTextField
    {
        DisplayField(String sStyle)
        {
            super(GuiManager.DEFAULT, sStyle);
            // FlatLaf paints a non-editable field in its "inactive" color unless the background
            // is a plain Color rather than a UIResource, so swap in a plain copy of the normal one.
            Color bg = getBackground();
            if (bg != null)
            {
                setBackground(new Color(bg.getRGB(), true));
                initBG();
            }
            GuiUtils.setDoNothingCaret(this);
            setHighlighter(null);
            lock();
        }

        @Override
        public void setDisplayOnly(boolean b)
        {
            super.setDisplayOnly(b);
            lock();
        }

        private void lock()
        {
            setEditable(false);
            setFocusable(false);
            setDragEnabled(false);
        }
    }
}
