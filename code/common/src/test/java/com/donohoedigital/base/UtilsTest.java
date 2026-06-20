package com.donohoedigital.base;

import junit.framework.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 23, 2010
 * Time: 2:15:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class UtilsTest extends TestCase
{
    public void testIsOs()
    {
        assertTrue(Utils.isLinux("linux"));
        assertTrue(Utils.isLinux("LINUX"));
        assertTrue(Utils.isMacOS("mac os x"));
        assertTrue(Utils.isMacOS("MAC OS X"));
        assertTrue(Utils.isWindows("Windows"));
        assertTrue(Utils.isWindows("windows"));

        assertFalse(Utils.isLinux("mac os x"));
        assertFalse(Utils.isLinux("windows"));
        assertFalse(Utils.isMacOS("linux"));
        assertFalse(Utils.isMacOS("windows"));
        assertFalse(Utils.isWindows("linux"));
        assertFalse(Utils.isWindows("mac os x"));
    }
}
