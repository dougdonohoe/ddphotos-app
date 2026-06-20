/*
 * AppPhases.java
 *
 * Created on October 28, 2002, 4:48 PM
 */

package com.donohoedigital.app.config;

import com.donohoedigital.base.*;
import com.donohoedigital.config.*;
import org.jdom2.*;

import java.util.*;

/**
 *
 * @author  Doug Donohoe
 */
public class AppPhases extends HashMap<String, AppPhase> {

    /**
     * Creates a new instance of AppPhases
     */
    public AppPhases(Element root, Namespace ns)
                    throws ApplicationError
    {
        AppPhase.setAppPhases(this);
        // get <phase> children
        List<Element> children = XMLConfigFileLoader.getChildren(root, "phase", ns, false, null);
        int nSize = children.size();
        
        if (nSize != 0) 
        {
            String sAttrErrorDesc;
            Element phase;
            AppPhase appPhase;
            for (int i = 0; i < nSize; i++)
            {
                sAttrErrorDesc = "Phase #" +(i+1)+" in " + AppdefConfig.APPDEF_CONFIG;
                phase = children.get(i);
                appPhase = new AppPhase(phase, ns, sAttrErrorDesc);
                put(appPhase.getName(), appPhase);
            }
        }    
    }
}
