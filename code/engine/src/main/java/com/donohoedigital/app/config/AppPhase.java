/*
 * AppPhase.java
 *
 * Created on October 28, 2002, 4:49 PM
 */

package com.donohoedigital.app.config;

import com.donohoedigital.base.*;
import com.donohoedigital.config.*;
import com.donohoedigital.app.engine.Phase;
import org.apache.logging.log4j.*;
import org.jdom2.*;

/**
 *
 * @author  Doug Donohoe
 */
public class AppPhase extends TypedHashMap
{
    static Logger logger = LogManager.getLogger(AppPhase.class);
   
    private String sClassname_;	
    private Class<? extends Phase> class_;
    private String sName_;
    private String sExtends_;
    private Boolean bCache_;
    private Boolean bHistory_;
    private Boolean bTransient_;
    private String sWindow_;

    private static AppPhases allPhases_ = null;
    static void setAppPhases(AppPhases phases)
    {
        allPhases_ = phases;
    }
    
    /**
     * Return copy
     */
    public final Object clone() {
        AppPhase result = (AppPhase)super.clone();
        result.sClassname_ = sClassname_;
        result.class_ = class_;
        result.sName_ = sName_;
        result.sExtends_ = sExtends_;
        result.bCache_ = bCache_;
        result.bHistory_ = bHistory_;
        result.bTransient_ = bTransient_;
        result.sWindow_ = sWindow_;
        return result;
    }
    
    /**
     * New AppPhase from XML element
     */
    public AppPhase(Element phase, Namespace ns, String sAttrErrorDesc)
                throws ApplicationError
    {
        sName_ = XMLConfigFileLoader.getStringAttributeValue(phase, "name", true, sAttrErrorDesc);
        sClassname_ = XMLConfigFileLoader.getStringAttributeValue(phase, "class", false, sAttrErrorDesc, null);
        sExtends_ = XMLConfigFileLoader.getStringAttributeValue(phase, "extends", false, sAttrErrorDesc, null);
        bCache_ = XMLConfigFileLoader.getBooleanAttributeValue(phase, "cache", false, sAttrErrorDesc, null);
        bHistory_ = XMLConfigFileLoader.getBooleanAttributeValue(phase, "history", false, sAttrErrorDesc, null);
        bTransient_ = XMLConfigFileLoader.getBooleanAttributeValue(phase, "transient", false, sAttrErrorDesc, null);
        sWindow_ = XMLConfigFileLoader.getStringAttributeValue(phase, "window", false, sAttrErrorDesc, null);

        if (sExtends_ != null)
        {
            AppPhase extend = allPhases_.get(sExtends_);
            if (extend == null)
            {
                String sMsg = sName_ + " extends " + sExtends_ + " but that wasn't found.";
                logger.error(sMsg);
                throw new ApplicationError(ErrorCodes.ERROR_VALIDATION, sMsg,
                                "Make sure order is correct in " + AppdefConfig.APPDEF_CONFIG +
                                "(" + sExtends_ + " must appear before " + sName_);
            }
            else
            {
                // copy class and params from phase we extend
                if (sClassname_ == null) sClassname_ = extend.sClassname_;
                if (bCache_ == null) bCache_ = extend.bCache_;
                if (bHistory_ == null) bHistory_ = extend.bHistory_;
                if (bTransient_ == null) bTransient_ = extend.bTransient_;
                if (sWindow_ == null) sWindow_ = extend.sWindow_;
                putAll(extend);
            }
                
        }
        
        if (bCache_ == null) bCache_ = Boolean.FALSE;
        if (bHistory_ == null) bHistory_ = Boolean.FALSE;
        if (bTransient_ == null) bTransient_ = Boolean.FALSE;

        if (sClassname_ == null)
        {
            String sMsg = "Class not defined for " + sName_;
            logger.error(sMsg);
            throw new ApplicationError(ErrorCodes.ERROR_VALIDATION, sMsg, "Define a class for this phase");
        }

        //noinspection unchecked
        class_ = (Class<? extends Phase>) ConfigUtils.getClass(sClassname_, false);
        
        // params and paramlist in phase
        XMLConfigFileLoader.loadParams(phase, ns, this, false, false,
                            "Phase " + sName_ + " in " + AppdefConfig.APPDEF_CONFIG);
    }
    
    /**
     * Return name of this phase
     */
    public String getName()
    {
        return sName_;
    }
    
    /**
     * Get classname used by this phase
     */
    public String getClassName()
    {
        return sClassname_;
    }
    
    /**
     * Get class used by this phase
     */
    public Class<? extends Phase> getClassObject()
    {
        return class_;
    }
    
    /**
     * Should this phase be cached?
     */
    public boolean isCached()
    {
        return bCache_;
    }
    
    /**
     * Should this phase be saved in history?
     */
    public boolean isHistory()
    {
        return bHistory_;
    }
    
    /**
     * If a phase is transient, it isn't recorded as the
     * current phase.  Default is false
     */
    public boolean isTransient()
    {
        return bTransient_;
    }

    /**
     * If a phase is a window, it is opened in a new EngineWindow.
     * Default is false.
     */
    public boolean isWindow()
    {
        return sWindow_ != null;
    }

    /**
     * Get window name
     */
    public String getWindowName()
    {
        return sWindow_;
    }
    
    /**
     * String for debugging
     */
    public String toString()
    {
        return "Phase " + sName_ + " cached: " + isCached() + " history: " + isHistory() + " params: " + super.toString();
    }
}
