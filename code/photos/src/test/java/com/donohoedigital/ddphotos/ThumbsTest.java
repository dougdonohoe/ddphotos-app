package com.donohoedigital.ddphotos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the thumbnail cache size and clear helpers, run against a {@link TempDir}
 * rather than the real user cache.
 */
public class ThumbsTest {

    @TempDir
    Path tmp;

    @Test
    void sizeAndClear() throws IOException {
        Files.write(tmp.resolve("a.jpg"), new byte[100]);
        Files.write(tmp.resolve("b.jpg"), new byte[250]);
        Path sub = Files.createDirectory(tmp.resolve("sub"));

        assertEquals(350, Thumbs.cacheSize(tmp));
        assertEquals(350, Thumbs.clearCache(tmp));
        assertEquals(0, Thumbs.cacheSize(tmp));
        assertTrue(Files.isDirectory(tmp), "the cache directory itself stays");
        assertTrue(Files.isDirectory(sub), "only files are removed");
    }

    @Test
    void missingDirectory() {
        Path missing = tmp.resolve("missing");
        assertEquals(0, Thumbs.cacheSize(missing));
        assertEquals(0, Thumbs.clearCache(missing));
        assertFalse(Files.exists(missing));
    }
}
