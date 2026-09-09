package com.donohoedigital.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ConfigUtils reads config through two paths, one taking a File and one taking a URL.
 * Both must decode the same way, so these tests pin that contract rather than relying on
 * the platform default charset.
 */
public class ConfigUtilsTest
{
    /**
     * Accented Latin, a non-Latin script and an emoji.  All are multibyte in UTF-8, and
     * the emoji is a surrogate pair in Java.  Written as escapes so the test does not
     * depend on the encoding of this source file.
     */
    @SuppressWarnings("UnnecessaryUnicodeEscape")
    private static final String NON_ASCII = "caf\u00e9 na\u00efve \u65e5\u672c\u8a9e \ud83d\udcf7";

    @TempDir
    Path dir;

    /**
     * Both readers return the same text for the same UTF-8 file.  readFile and readURL
     * append a newline to every line, including the last.
     */
    @Test
    public void testReadersAgreeOnUtf8() throws IOException
    {
        File file = write("utf8.txt", NON_ASCII.getBytes(StandardCharsets.UTF_8));

        assertEquals(NON_ASCII + "\n", ConfigUtils.readFile(file));
        assertEquals(NON_ASCII + "\n", ConfigUtils.readURL(file.toURI().toURL()));
    }

    /**
     * Utils.newDecoder() replaces bad input instead of throwing, so a truncated or
     * mis-encoded config file still loads.  Both readers must do the same.
     */
    @Test
    public void testReadersReplaceMalformedBytes() throws IOException
    {
        // 0xC3 opens a two byte sequence that 'b' does not continue
        File file = write("malformed.txt", new byte[]{'a', (byte) 0xC3, 'b'});

        String expected = "a\ufffdb\n";
        assertEquals(expected, ConfigUtils.readFile(file));
        assertEquals(expected, ConfigUtils.readURL(file.toURI().toURL()));
    }

    private File write(String name, byte[] bytes) throws IOException
    {
        Path path = dir.resolve(name);
        Files.write(path, bytes);
        return path.toFile();
    }
}
