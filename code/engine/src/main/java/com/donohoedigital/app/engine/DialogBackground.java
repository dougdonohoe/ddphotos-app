/*
 * DialogBackground.java
 *
 * Created on November 24, 2002, 9:11 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.app.config.*;
import com.donohoedigital.gui.*;

import javax.swing.*;
import java.awt.*;

/**
 *
 * @author  Doug Donohoe
 */
public class DialogBackground extends DDPanel 
{
    //static Logger logger = LogManager.getLogger(DialogBackground.class);
    
    DDPanel dialogbox_;
    ButtonBox buttonbox_;
    DDCheckBox checkbox_;
    DDPanel buttonbase_;

    // Max width applied to wrapping text contents (e.g. DDHtmlArea messages) so long
    // lines wrap instead of stretching the dialog. 0 disables. See setCenterContents().
    private final int maxWidth_;

    /**
     * Creates a new instance of DialogBackground
     */
    public DialogBackground(AppContext context, AppPhase appPhase, Phase phase,
                            boolean bNoShowOption, String sNoShowCheckboxName)
    {
        this(context, appPhase, phase, bNoShowOption, sNoShowCheckboxName, null, 0);
    }

    /** 
     * Creates a new instance of DialogBackground 
     */
    public DialogBackground(AppContext context, AppPhase appPhase, Phase phase,
                            boolean bNoShowOption, String sNoShowCheckboxName,
                            String sButtonStyle, int minWidthOverride)
    {
        String sDialogStyle = appPhase.getString("dialog-style", "default");
        String sDefaultHelpName = appPhase.getString("dialog-help-name", "welcome");
        
        // background image
        JComponent parent = this;
        String sImageName = appPhase.getString("dialog-background-image");
        if (sImageName != null)
        {       
            // do stuff here
            ImageComponent ic = new ImageComponent(sImageName, 1.0);
            ic.setPreferredSize(null); // reset preferred size so we don't affect layout
            add(ic, BorderLayout.CENTER);
            ic.setLayout(new BorderLayout());
            ic.setScaleToFit(false);
            ic.setTile(true);
            parent = ic;
        }
        
        dialogbox_ = new DDPanel(sDefaultHelpName, sDialogStyle);
        
        // dialog buttons
        if (phase != null)
        {
            buttonbase_ = new DDPanel();
            dialogbox_.add(buttonbase_, BorderLayout.SOUTH);

            buttonbox_ = new ButtonBox(context, appPhase, phase, "empty", false,
                    sButtonStyle);
            buttonbase_.add(buttonbox_, BorderLayout.CENTER);

            if (bNoShowOption)
            {
                checkbox_ = new DDCheckBox(sNoShowCheckboxName, sDialogStyle);
                DDPanel checkbase = new DDPanel();
                checkbase.add(checkbox_, BorderLayout.WEST);
                checkbase.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
                buttonbase_.add(checkbase, BorderLayout.SOUTH);
            }

            // spacer to ensure minimum size
            DDPanel spacer = new DDPanel();

            int nMinWidth = appPhase.getInteger("dialog-minwidth", 300);
            spacer.setPreferredSize(new Dimension(Math.max(minWidthOverride, nMinWidth), 0));
            dialogbox_.add(spacer, BorderLayout.NORTH);
        }
        maxWidth_ = appPhase.getInteger("dialog-maxwidth", 750);
        parent.add(dialogbox_, BorderLayout.CENTER);
    }

    public void setCenterContents(JComponent c)
    {
        applyMaxWidth(c);
        dialogbox_.add(c, BorderLayout.CENTER);
    }

    /**
     * Bound the width of any wrapping text component (JEditorPane, e.g. DDHtmlArea) inside the
     * contents so long lines wrap at dialog-maxwidth instead of stretching the dialog. The
     * component's preferred height is recomputed for the bounded width so the dialog packs tall
     * enough. No-op when dialog-maxwidth is 0 or the content is already narrower.
     */
    private void applyMaxWidth(JComponent c)
    {
        if (maxWidth_ <= 0) return;
        JEditorPane editor = findEditorPane(c);
        if (editor == null) return;
        // Wrap at the narrower of maxWidth_ and any width the contents already established
        // (e.g. PhotosDialog.wrapWithInstructions pre-sizes the editor). Honoring that width
        // keeps the recomputed height in sync with the width the editor is actually displayed
        // at - otherwise the height is computed for fewer, wider lines and the text is clipped.
        int width = editor.getWidth() > 0 ? Math.min(editor.getWidth(), maxWidth_) : maxWidth_;
        if (editor.getPreferredSize().width <= width) return;
        editor.setSize(width, Short.MAX_VALUE);
        int height = editor.getPreferredSize().height;
        editor.setPreferredSize(new Dimension(width, height));
    }

    private static JEditorPane findEditorPane(Component c)
    {
        if (c instanceof JEditorPane editor) return editor;
        if (c instanceof Container container)
        {
            for (Component child : container.getComponents())
            {
                JEditorPane found = findEditorPane(child);
                if (found != null) return found;
            }
        }
        return null;
    }
    
    public ButtonBox getButtonBox()
    {
        return buttonbox_;
    }
    
    public DDCheckBox getNoShowCheckBox()
    {
        return checkbox_;
    }
    
    /**
     * Return panel holding buttons - can use to put something in any
     * area besides `BorderLayout.CENTER`
     */
    public DDPanel getButtonBase()
    {
        return buttonbase_;
    }
}
