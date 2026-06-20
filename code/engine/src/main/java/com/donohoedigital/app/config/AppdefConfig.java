/*
 * AppdefConfig.java
 *
 * Created on October 11, 2002, 6:02 PM
 */

package com.donohoedigital.app.config;

import com.donohoedigital.base.*;
import com.donohoedigital.config.*;
import org.jdom2.*;

import java.net.*;

/**
 *
 * @author  donohoe
 */
public class AppdefConfig extends XMLConfigFileLoader
{
    static final String APPDEF_CONFIG = "appdef.xml";

    private String sStartPhase_;
    private AppPhases phases_;

    /** 
     * Load AppdefConfig (appdef.xml) from the given module
     */
    public AppdefConfig(String sModule) throws ApplicationError
    {
        init(sModule);
    }
    
    /**
     * Read in config file from given module
     */
    private void init(String sModule) throws ApplicationError
    {
        // get appdef url, throws exception if missing
        URL url = new MatchingResources("classpath*:config/" + sModule + "/" + APPDEF_CONFIG).getSingleRequiredResourceURL();
        
        Document doc = this.loadXMLUrl(url, "appdef.xsd");
        init(doc);
    }
    
    /**
     * Initialize from JDOM doc
     */
    private void init(Document doc) throws ApplicationError
    {
        Element root = doc.getRootElement();
        
        // startphase
        sStartPhase_ = getChildStringValueTrimmed(root, "startphase", ns_, true, APPDEF_CONFIG);
        
        // phases
        phases_ = new AppPhases(root, ns_);
        
        // verify startphase exists
        AppPhase phase = phases_.get(sStartPhase_);
        if (phase == null)
        {
            throw new ApplicationError(ErrorCodes.ERROR_VALIDATION,
                                        "Start phase " + sStartPhase_ + " not found in list of phases",
                                        "Make sure start phase is spelled correctly and exists as an actual phase");
        }
    }

    /**
     * get starting phase
     */
    public String getStartPhaseName()
    {
        return sStartPhase_;
    }
    
    /**
     * Get phases
     */
    public AppPhases getAppPhases()
    {
        return phases_;
    }
}
