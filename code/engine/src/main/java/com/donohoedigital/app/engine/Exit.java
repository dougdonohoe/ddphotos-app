/*
 * Exit.java
 *
 * Created on November 15, 2002, 3:41 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.config.*;

/**
 *
 * @author  Doug Donohoe
 */
public class Exit extends BasePhase
{
    public void start()
    {
        String sMsg = PropertyConfig.getMessage("msg.exit.confirm");
        if (EngineUtils.displayConfirmationDialog(context_, sMsg, "exitconfirm"))
        {
            engine_.exit(0);
        }
    }
}
