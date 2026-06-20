/*
 * Phase.java
 *
 * Created on November 15, 2002, 3:23 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.app.config.*;

/**
 *
 * @author  Doug Donohoe
 */
public interface Phase {
    
    void init(AppEngine engine, AppContext context, AppPhase phase);
    
    void reinit(AppPhase phase);
    
    void setFromPhase(Phase phase);
        
    void start();
    
    void finish();
    
    AppEngine getAppEngine();
    
    AppPhase getAppPhase();
    
    boolean processButton(AppButton button);

    Object getResult();

    /**
     * Register a one-shot listener fired when this phase's result is set.
     * If the result has already been set (e.g. a modal dialog that has
     * already returned, or a no-show dialog that resolved synchronously),
     * the listener fires immediately on registration.  Used to drive
     * non-modal dialogs asynchronously - see TourController.
     */
    void setResultListener(ResultListener listener);

    /**
     * Listener for {@link #setResultListener}.
     */
    interface ResultListener {
        void resultSet(Phase phase, Object result);
    }
}
