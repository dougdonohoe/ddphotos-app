package com.donohoedigital.config;

import org.junit.jupiter.api.Test;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 6, 2008
 * Time: 2:53:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class ConfigManagerTest
{
    @Test
    public void testCreate()
    {
        new ConfigManager("testapp", ApplicationType.COMMAND_LINE);
    }
}
