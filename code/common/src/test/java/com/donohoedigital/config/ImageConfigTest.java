package com.donohoedigital.config;

import com.donohoedigital.base.*;
import junit.framework.*;

import java.awt.image.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 7, 2008
 * Time: 9:49:51 AM
 * To change this template use File | Settings | File Templates.
 */
public class ImageConfigTest extends TestCase
{
    //private static Logger logger = LogManager.getLogger(ImageConfigTest.class);

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