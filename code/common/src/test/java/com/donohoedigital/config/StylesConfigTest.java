package com.donohoedigital.config;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;

import static com.donohoedigital.config.StylesConfig.USE_DEFAULT_COLOR;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 7, 2008
 * Time: 9:49:51 AM
 * To change this template use File | Settings | File Templates.
 */
public class StylesConfigTest
{
    //private static Logger logger = LogManager.getLogger(StylesConfigTest.class);

    @Test
    public void testLoad()
    {
        String[] modules = {"common", "testapp"};
        new StylesConfig(modules);

        Color c= StylesConfig.getColor("black");
        assertNotNull(c);

        c = StylesConfig.getColor("white");
        assertNotNull(c);

        c = StylesConfig.getColor("fallback");
        assertEquals(USE_DEFAULT_COLOR, c);

        c = StylesConfig.getColor("missing");
        assertNull(c);

        Font f = StylesConfig.getFont("lcd");
        assertNotNull(f);

        f = StylesConfig.getFont("lucida");
        assertNotNull(f);
    }
}