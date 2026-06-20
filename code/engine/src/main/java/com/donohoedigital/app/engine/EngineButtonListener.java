/*
 * AppButton.java
 *
 * Created on March 23, 2005, 4:13 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.app.config.*;

import java.awt.event.*;

/**
 *
 * @author  Doug Donohoe
 */
public class EngineButtonListener implements ActionListener
{
    //static Logger logger = LogManager.getLogger(EngineButtonListener.class);

    AppContext context_;
    AppButton button_;
    Phase phase_;
    AppEngine engine_;
    
    public EngineButtonListener(AppContext context, Phase phase, String sButtonName)
    {
        this(context, phase, new AppButton(sButtonName));
    }

    public EngineButtonListener(AppContext context, Phase phase, AppButton button)
    {
        context_ = context;
        phase_ = phase;
        engine_ = phase_.getAppEngine();
        button_ = button;
    }

    /**
     * Called when button pressed - calls phase_.processButton(), which if it returns true
     * then calls appengine_.processPhase(button_.getGotoPhase)
     */
    public void actionPerformed(ActionEvent e) 
    {
        context_.buttonPressed(button_, phase_);
    }

    public AppButton getAppButton()
    {
        return button_;
    }
}
