/*
 * AppButton.java
 *
 * Created on November 15, 2002, 12:49 PM
 */

package com.donohoedigital.app.config;

import com.donohoedigital.base.*;

import java.util.*;

/**
 *
 * @author  Doug Donohoe
 */
public class AppButton extends TypedHashMap
{
    //static Logger logger = LogManager.getLogger(AppButton.class);

    public static final String PARAM_GENERIC = "generic";
    public static final String DELIM = ":";
    
    private final String sName_;
    private String sGotoPhase_;
    private String sParam_;

    /**
     * Creates an app button from the form name:gotoPhase
     */
    public AppButton(String sButtonDefinition)
    {
        StringTokenizer st = new StringTokenizer(sButtonDefinition, DELIM);

        sName_ = st.nextToken();

        if (st.hasMoreElements())
        {
            sGotoPhase_ = st.nextToken();
        }

        if (st.hasMoreElements())
        {
            sParam_ = st.nextToken();
        }
    }
    
    /**
     * Return name of this button
     */
    public String getName()
    {
        return sName_;
    }
   
    /**
     * Get name of phase invoked by this button
     */
    public String getGotoPhase()
    {
        return sGotoPhase_;
    }

    /**
     * Get generic phase
     */
    public String getGenericParam()
    {
        return sParam_;
    }

    /**
     * String representation
     */
    public String toString()
    {
        return "AppButton name="+sName_+", gotophase="+sGotoPhase_;
    }
    
    /**
     * return true if button definition matches the given button name
     * (account for optional colon:phase)
     */
    public static boolean isMatch(String sButtonDefinition, String sButtonName)
    {
        return sButtonDefinition.equals(sButtonName) ||
                sButtonDefinition.startsWith(sButtonName + DELIM);
    }
}
