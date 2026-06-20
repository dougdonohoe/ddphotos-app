/*
 * ButtonBox.java
 *
 * Created on November 17, 2002, 4:46 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.base.*;
import com.donohoedigital.app.config.*;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.*;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 *
 * @author Doug Donohoe
 */
public class ButtonBox extends DDPanel implements AncestorListener {
    static Logger logger = LogManager.getLogger(ButtonBox.class);

    public static final String PARAM_DEFAULT_BUTTON = "default-button";
    public static final String PARAM_DEFAULT_NO_SHOW_BUTTON = "default-no-show-button";
    private final HashMap<String, DDButton> buttons_ = new HashMap<>();
    private JButton defaultButton_;

    /**
     * Defaults nGap to 5 (gap between buttons) and borderSpace to
     * 8 if vertical arrangement, 5 otherwise.
     */
    public ButtonBox(AppContext context, AppPhase appPhase, Phase phase, String sName,
                     boolean bVertical, String sButtonStyle) throws ApplicationError {
        this(context, appPhase, phase, sName, 8,
                (bVertical ? 8 : 4), bVertical, sButtonStyle);
    }

    /**
     * Creates a new instance of ButtonBox
     */
    public ButtonBox(AppContext context, AppPhase appPhase, Phase phase, String sName,
                     int nGap, int nBorderSpace,
                     boolean bVertical, String sButtonStyle) throws ApplicationError {
        super(sName);

        addAncestorListener(this);

        List<?> buttons = appPhase.getList("buttons");
        String sEnter = appPhase.getString(PARAM_DEFAULT_BUTTON);
        if (sEnter != null && sEnter.equals("NONE")) sEnter = null;

        DDPanel buttonPanel = new DDPanel();
        setBorder(BorderFactory.createEmptyBorder(bVertical ? nBorderSpace : 1, nBorderSpace, nBorderSpace, nBorderSpace));

        if (bVertical) {
            buttonPanel.setLayout(new GridLayout(0, 1, 0, nGap));
        } else {
            buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, nGap, 0));
        }

        // track default button
        boolean bDefaultSet = false;

        // loop through each button and add it to buttonbox
        if (buttons != null) {
            String sButtonName;
            DDButton button;
            EngineButtonListener listener;
            for (Object o : buttons) {
                sButtonName = (String) o;
                listener = new EngineButtonListener(context, phase, sButtonName);
                button = new DDButton(listener.getAppButton().getName(), sButtonStyle);
                button.addActionListener(listener);
                buttonPanel.add(button);
                if (sEnter != null && button.getName().equals(sEnter)) {
                    defaultButton_ = button;
                    bDefaultSet = true;
                }
                buttons_.put(button.getName(), button);
            }
        }

        if (sEnter != null && !sEnter.isEmpty() && !bDefaultSet) {
            logger.warn("Unable to set enter button {} (no matching button found)", sEnter);
        }

        if (bVertical) {
            add(buttonPanel, BorderLayout.NORTH);
        } else {
            DDPanel align = new DDPanel();
            align.setLayout(new CenterLayout());
            align.add(buttonPanel, BorderLayout.CENTER);
            add(align, BorderLayout.SOUTH);
        }
    }

    /**
     * Return button with given name
     */
    public DDButton getButton(String sName) {
        return buttons_.get(sName);
    }

    /**
     * Return list of DDButton's, keyed by name
     */
    public HashMap<String, DDButton> getButtons() {
        return buttons_;
    }

    /**
     * Get button associated with the enter key
     */
    public DDButton getDefaultButton() {
        return (DDButton) defaultButton_;
    }

    /**
     * When added to hierarchy, set default button
     */
    public void ancestorAdded(AncestorEvent event) {
        if (defaultButton_ != null)
        {
            JRootPane root = SwingUtilities.getRootPane(this);
            root.setDefaultButton(defaultButton_);
            //logger.debug("BUTTONBOX ROOT " + root.getName() +" Setting default button to " + ((defaultButton_ == null) ? "null" : defaultButton_.getName()));
        }
    }

    public void ancestorMoved(AncestorEvent event) {
    }

    /**
     * When removed from hierarchy, remove default button
     */
    public void ancestorRemoved(AncestorEvent event) {
        if (defaultButton_ != null)
        {
            JRootPane root = SwingUtilities.getRootPane(this);
            root.setDefaultButton(null);
            //logger.debug("BUTTONBOX ROOT " + root.getName() +" removing default button " + ((defaultButton_ == null) ? "null" : defaultButton_.getName()));
        }
    }
}
