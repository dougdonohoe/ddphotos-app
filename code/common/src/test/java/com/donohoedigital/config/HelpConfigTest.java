package com.donohoedigital.config;

import org.apache.logging.log4j.*;
import com.donohoedigital.base.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 7, 2008
 * Time: 9:49:51 AM
 * To change this template use File | Settings | File Templates.
 */
public class HelpConfigTest
{
    @Test
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