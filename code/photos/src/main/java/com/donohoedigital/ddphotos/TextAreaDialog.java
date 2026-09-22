package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.gui.DDButton;
import com.donohoedigital.gui.DDPanel;
import com.donohoedigital.gui.DDScrollPane;
import com.donohoedigital.gui.DDTextArea;
import com.donohoedigital.gui.OptionTextArea;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Edits the text of an {@link OptionTextArea} in a large, resizable dialog - the editor behind the
 * option's pencil button.  Save writes the text back to the option; Cancel leaves it alone.
 */
public class TextAreaDialog extends PhotosDialog
{
    private static final String PHASE_NAME = "TextAreaDialog";
    private static final String PARAM_OPTION = "option";

    private OptionTextArea option_;
    private String original_;
    private DDTextArea text_;
    private DDButton saveBtn_;

    /** Opens the dialog for the given option; returns once it is closed. */
    public static void open(AppContext context, OptionTextArea option)
    {
        if (context == null || option == null) return;
        TypedHashMap params = new TypedHashMap();
        params.setObject(PARAM_OPTION, option);
        context.processPhaseNow(PHASE_NAME, params);
    }

    // -------------------------------------------------------------------------
    // DialogPhase API
    // -------------------------------------------------------------------------

    @Override
    public JComponent createDialogContents()
    {
        option_   = (OptionTextArea) phase_.getObject(PARAM_OPTION);
        original_ = option_.getText();

        getDialog().setTitle(PropertyConfig.getMessage("msg.windowtitle.TextAreaDialog",
                option_.getLabel().trim()));
        getDialog().setResizable(true);
        getDialog().setMinimumSize(new Dimension(400, 250));

        text_ = new DDTextArea("textareaeditor", STYLE);
        text_.setTextLengthLimit(option_.getLengthLimit());
        if (option_.getRegExp() != null) text_.setRegExp(option_.getRegExp());
        text_.setText(original_);
        text_.setCaretPosition(0);
        // Tab moves on to the buttons, and arriving doesn't select the whole text.
        text_.setTabChangesFocus(true);
        text_.setSelectAllOnFocus(false);
        // Deferred: document listeners run newest first, so the area's own validation of this
        // same edit hasn't happened yet.
        text_.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(TextAreaDialog.this::checkButtons); }
            public void removeUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(TextAreaDialog.this::checkButtons); }
            public void changedUpdate(DocumentEvent e) { SwingUtilities.invokeLater(TextAreaDialog.this::checkButtons); }
        });

        DDScrollPane scroll = new DDScrollPane(text_, STYLE, DDScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                DDScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // FlatLaf draws a text area's border (and focus ring) from its scroll pane, not the area;
        // DDScrollPane clears that border, so restore it here.
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        text_.setScrollPane(scroll);

        DDPanel main = new DDPanel();
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        main.setPreferredSize(contentSize());
        main.add(scroll, BorderLayout.CENTER);

        saveBtn_ = getMatchingButton("save");
        return main;
    }

    /** Sized from the app window: roomy, but always inside it. */
    private Dimension contentSize()
    {
        Dimension frame = context_.getFrame().getSize();
        int w = Math.clamp(frame.width * 6L / 10, 500, 900);
        int h = Math.clamp(frame.height / 2, 300, 700);
        return new Dimension(w, h);
    }

    @Override
    protected void opened()
    {
        super.opened();
        checkButtons();
    }

    @Override
    protected Component getFocusComponent()
    {
        return text_;
    }

    @Override
    public boolean processButton(AppButton button)
    {
        if ("save".equals(button.getName())) {
            option_.setText(text_.getText());
        }
        removeDialog();
        return true;
    }

    // -------------------------------------------------------------------------
    // Button state
    // -------------------------------------------------------------------------

    /** Save needs valid text that differs from what the dialog opened with. */
    @Override
    protected void checkButtons()
    {
        if (saveBtn_ == null) return;
        saveBtn_.setEnabled(text_.isValidData() && !text_.getText().equals(original_));
    }
}
