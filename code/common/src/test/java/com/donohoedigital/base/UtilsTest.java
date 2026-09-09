package com.donohoedigital.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 23, 2010
 * Time: 2:15:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class UtilsTest
{
    @Test
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

    /**
     * formatExceptionText writes the stack trace into a byte buffer and reads it back, so
     * both halves have to use the same charset.  The message is written as escapes so the
     * test does not depend on the encoding of this source file.
     */
    @Test
    public void testFormatExceptionText()
    {
        assertEquals("null", Utils.formatExceptionText(null));

        @SuppressWarnings("UnnecessaryUnicodeEscape")
        String message = "caf\u00e9 na\u00efve \u65e5\u672c\u8a9e";
        String text = Utils.formatExceptionText(new IllegalStateException(message));

        assertTrue(text.contains(IllegalStateException.class.getName()), text);
        assertTrue(text.contains(message), text);
        assertTrue(text.contains("testFormatExceptionText"), "should include the stack trace: " + text);
    }
}
