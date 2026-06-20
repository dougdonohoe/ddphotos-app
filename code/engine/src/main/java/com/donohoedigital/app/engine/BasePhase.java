/*
 * BasePhase.java
 *
 * Created on November 15, 2002, 3:40 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.config.Perf;
import com.donohoedigital.app.config.*;

/**
 *
 * @author  Doug Donohoe
 */
public abstract class BasePhase implements Phase 
{
    protected AppEngine engine_;
    protected AppContext context_;
    protected AppPhase phase_;
    protected Object oResult_;

    // async result notification (see setResultListener)
    private boolean resultSet_;
    private ResultListener resultListener_;

    /** 
     * Creates a new instance of BasePhase 
     */
    public BasePhase() {
        if (Perf.JPROFILER) Perf.construct(this, null);
    }

    /**
     * Init phase, storing engine and phase
     */
    public void init(AppEngine engine, AppContext context, AppPhase phase)
    {
        engine_ = engine;
        context_ = context;
        phase_ = phase;
    }
    
    /**
     * Reinit phase for use, called before reset(),
     * used to store new phase data
     */
    public void reinit(AppPhase phase)
    {
        phase_ = phase;
    }
    
    /**
     * Called by engine - passes the last non-transient phase
     * invoked before this phase (i.e., the phase that launched
     * this phase).  BasePhase ignores this (doesn't store what
     * is passed in) to avoid keeping pointers to old phases.
     * Subclasses can override this if they need to know from
     * whence they came.
     */
    public void setFromPhase(Phase phase)
    {
    }
    
    /**
     * Must declare - logic of phase goes in here
     */
    abstract public void start();
    
    /**
     * Called when a phase is removed as the main component (
     * when using engine.setMainUIComponent()) or when
     * a DialogPhase's dialog is closed.  Other phases that 
     * don't use a UI are finished
     * when their start() method is done, so any cleanup can be
     * done then.
     */
    public void finish()
    {
        resultListener_ = null;
    }
    
    /**
     * Returns AppEngine provided during init()
     */
    public AppEngine getAppEngine()
    {
        return engine_;
    }
    
    /**
     * Returns AppPhase provided during init()
     */
    public AppPhase getAppPhase()
    {
        return phase_;
    }
    
    /**
     * Returns true
     */
    public boolean processButton(AppButton button)
    {
        return true;
    }
    
    /**
     * Used in modal phases to return a result
     */
    public Object getResult()
    {
        return oResult_;
    }
    
    /**
     * Set the result
     */
    public void setResult(Object o)
    {
        oResult_ = o;
        resultSet_ = true;
        fireResult();
    }

    /**
     * Register a one-shot listener for when the result is set.  Fires
     * immediately if the result was already set before registration.
     */
    public void setResultListener(ResultListener listener)
    {
        resultListener_ = listener;
        fireResult();
    }

    /**
     * Notify the listener once the result is set, then drop it (one-shot,
     * so it can never fire twice regardless of registration order).
     */
    private void fireResult()
    {
        if (resultSet_ && resultListener_ != null)
        {
            ResultListener listener = resultListener_;
            resultListener_ = null;
            listener.resultSet(this, oResult_);
        }
    }

    /**
     * For debugging, returns phase name followed by this' class name
     */
    @Override
    public String toString()
    {
        return phase_.getName() + ": " + this.getClass().getName();
    }

}
