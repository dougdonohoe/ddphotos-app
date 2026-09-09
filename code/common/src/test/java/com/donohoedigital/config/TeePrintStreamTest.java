package com.donohoedigital.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * TeePrintStream buffers what is printed to System.out and reads it back as UTF-8, so the
 * stream it installs has to encode with the same charset.
 */
public class TeePrintStreamTest
{
    /**
     * Written as escapes so the test does not depend on the encoding of this source file.
     */
    @SuppressWarnings("UnnecessaryUnicodeEscape")
    private static final String ACCENTED = "caf\u00e9 na\u00efve";
    @SuppressWarnings("UnnecessaryUnicodeEscape")
    private static final String JAPANESE = "\u65e5\u672c\u8a9e";

    @Test
    public void testCapturesNonAscii()
    {
        TeePrintStream tee = new TeePrintStream();
        String[] lines;
        try
        {
            System.out.println(ACCENTED);
            System.out.println(JAPANESE);
        }
        finally
        {
            lines = tee.getCapturedLines();
            tee.restoreOriginal();
        }

        assertArrayEquals(new String[]{ACCENTED, JAPANESE}, lines);
    }
}
