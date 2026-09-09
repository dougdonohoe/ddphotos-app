package com.donohoedigital.config;

import com.donohoedigital.base.Utils;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 7, 2008
 * Time: 9:49:51 AM
 * To change this template use File | Settings | File Templates.
 */
public class ImageConfigTest
{
    //private static Logger logger = LogManager.getLogger(ImageConfigTest.class);

    @Test
    public void testLoad()
    {
        String[] modules = {"common", "testapp"};
        new ImageConfig(modules);

        ImageDef def = ImageConfig.getImageDef("icon");
        assertNotNull(def);

        if (!Utils.ISMAC) // doesn't play nicely on Mac
        {
            BufferedImage img = def.getBufferedImage();
            assertNotNull(img);
        }
    }
}