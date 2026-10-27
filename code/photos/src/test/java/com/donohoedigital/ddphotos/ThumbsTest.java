package com.donohoedigital.ddphotos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the thumbnail decode and cache helpers, run against a {@link TempDir}
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
    void subsamplingKeepsTwiceTheThumbnailSize() {
        // 8064x6048 (48 MP) to fit 300x300: scale 0.0372, so every 13th pixel gives 620x465.
        assertEquals(13, Thumbs.subsampling(8064, 6048, 300, 300, null));
        // A crop fills the width, so the height does not matter.
        assertEquals(13, Thumbs.subsampling(8064, 6048, 300, 50, "center"));
        // A thumbnail more than half the source size decodes at full size.
        assertEquals(1, Thumbs.subsampling(1000, 800, 600, 600, null));
        assertEquals(1, Thumbs.subsampling(100, 80, 600, 600, null));
    }

    @Test
    void decodeSubsamples() throws IOException {
        Path jpg = tmp.resolve("big.jpg");
        ImageIO.write(new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "JPEG", jpg.toFile());

        // 4000 / (200 * 2) = every 10th pixel.
        BufferedImage img = Thumbs.decode(jpg, 200, 200, null);
        assertNotNull(img);
        assertEquals(400, img.getWidth());
        assertEquals(300, img.getHeight());
    }

    @Test
    void decodeUnreadable() throws IOException {
        Path bogus = tmp.resolve("bogus.jpg");
        Files.write(bogus, new byte[]{1, 2, 3, 4});
        assertNull(Thumbs.decode(bogus, 200, 200, null));
    }

    @Test
    void missingDirectory() {
        Path missing = tmp.resolve("missing");
        assertEquals(0, Thumbs.cacheSize(missing));
        assertEquals(0, Thumbs.clearCache(missing));
        assertFalse(Files.exists(missing));
    }
}
