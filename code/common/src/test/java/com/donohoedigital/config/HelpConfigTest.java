package com.donohoedigital.config;

import junit.framework.*;
import org.apache.logging.log4j.*;
import com.donohoedigital.base.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 7, 2008
 * Time: 9:49:51 AM
 * To change this template use File | Settings | File Templates.
 */
public class HelpConfigTest extends TestCase
{
    public void testLoad()
    {
        String[] modules = {"common", "testapp"};
        new HelpConfig(modules, null);

        for (int i = 1; i <= 3; i++)
        {
            HelpTopic ht = HelpConfig.getHelpTopic("test"+i);
            String contents = ht.getContents();
            assertNotNull(contents);
        }
    }
}