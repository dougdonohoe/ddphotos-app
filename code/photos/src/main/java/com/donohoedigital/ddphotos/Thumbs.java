package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppConfigUtils;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.ImageDef;
import com.donohoedigital.gui.DDIconButtons;
import com.donohoedigital.gui.RenderUtils;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Disk-cached thumbnail loading and scaling, shared by all thumbnail consumers
 * (the cover preview in {@link PhotoPreviewPanel} and the caption editor rows).
 *
 * <p>Thumbnails are cached under {@code <cache>/thumbs}, keyed by the source path, target size,
 * and crop, with the file's mtime baked into the key so edits invalidate automatically.
 */
public final class Thumbs {

    private static final Logger logger = LogManager.getLogger(Thumbs.class);

    /** Disk cache directory for generated thumbnails. */
    private static final Path THUMB_CACHE_DIR = AppConfigUtils.getCacheDir().toPath().resolve("thumbs");

    // Shared, capped pool for all asynchronous thumbnail decoding (see loadAsync).  A small, fixed
    // size bounds the memory burst from concurrent full-resolution decodes and keeps thumbnail work
    // off SwingWorker's app-wide pool.  Daemon threads so it never blocks JVM exit.
    private static final int LOADER_THREADS = 3;
    private static final AtomicInteger LOADER_SEQ = new AtomicInteger(1);
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(LOADER_THREADS, r -> {
        Thread t = new Thread(r, "thumb-loader-" + LOADER_SEQ.getAndIncrement());
        t.setDaemon(true);
        return t;
    });

    private Thumbs() {}

    /**
     * The "no preview" icon to show in place of a thumbnail {@link #load} came back null for:
     * video-off for a clip, camera-off for anything else (a HEIC, say).  ImageIO can decode neither,
     * so this is what every thumbnail consumer falls back to.
     */
    public static Icon placeholderIcon(Path path, int size) {
        FlatSVGIcon base = path != null && PathValidation.isVideoFile(path.getFileName().toString())
                ? DDIconButtons.VIDEO_OFF
                : DDIconButtons.CAMERA_OFF;
        return DDIconButtons.svgIcon(base, size, "Label.disabledForeground");
    }

    /**
     * As {@link #loadAsync(Path, int, int, String, Consumer)}, but sized in <i>device</i> pixels for
     * the screen showing {@code c}: the requested logical box is multiplied by that screen's scale
     * factor (1.25 for Windows at 125%, 2.0 for Retina-class).  The caller must then draw the result
     * into the original logical box, which downscales it — far sharper than generating at logical
     * size and letting the device transform upscale.
     *
     * <p>Thumbnail sizes are part of the disk cache key, so scaled sizes simply get their own cache
     * entries; nothing collides and no migration is needed.
     */
    public static Future<BufferedImage> loadAsyncForDisplay(Component c, Path path,
                                                            int maxWidth, int maxHeight, String crop,
                                                            Consumer<BufferedImage> onResult) {
        double scale = RenderUtils.getDisplayScale(c);
        int w = Math.max(1, (int) Math.ceil(maxWidth * scale));
        int h = Math.max(1, (int) Math.ceil(maxHeight * scale));
        return loadAsync(path, w, h, crop, onResult);
    }

    /**
     * Decodes a thumbnail on the shared loader pool and delivers it to {@code onResult} on the EDT
     * (null when the image can't be read, e.g. HEIC or a video).  Returns a handle the caller can
     * {@link Future#cancel cancel} — when a preview is replaced or an editor window closes — to drop
     * queued and interrupt running decodes; {@code onResult} is skipped if the job is cancelled.
     */
    public static Future<BufferedImage> loadAsync(Path path, int maxWidth, int maxHeight, String crop,
                                                  Consumer<BufferedImage> onResult) {
        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            protected BufferedImage doInBackground() { return load(path, maxWidth, maxHeight, crop); }
            protected void done() {
                if (isCancelled()) return;
                BufferedImage img = null;
                try {
                    img = get();
                } catch (Exception e) {
                    logger.warn("Thumbnail load failed: {}", path);
                }
                onResult.accept(img);
            }
        };
        LOADER.execute(worker);
        return worker;
    }

    /**
     * Loads a disk-cached thumbnail for the given image, scaled to fit within
     * {@code maxWidth x maxHeight}.  When {@code crop} is "top"/"center"/"bottom" the image is
     * scaled to fill the width and a maxHeight-tall slice is cropped from that position; otherwise
     * it is scaled to fit both dimensions.  The cache key includes the file's mtime, so edits
     * invalidate automatically.  Returns null if the image cannot be read.
     */
    public static BufferedImage load(Path path, int maxWidth, int maxHeight, String crop) {
        // ImageIO has no reader for any video container, so a clip could only fail - and it would
        // fail loudly, since ImageDef logs at error level.  Bail out here and let the caller draw
        // the video-off placeholder (see placeholderIcon).
        if (PathValidation.isVideoFile(path.getFileName().toString())) return null;

        Path cacheFile = cachePathFor(path, maxWidth, maxHeight, crop);
        if (cacheFile != null && cacheFile.toFile().exists()) {
            try {
                BufferedImage cached = ImageIO.read(cacheFile.toFile());
                if (cached != null) return cached;
            } catch (IOException e) {
                logger.warn("Corrupt thumbnail cache entry, regenerating: {}", cacheFile);
            }
        }

        BufferedImage full;
        try {
            full = ImageDef.getBufferedImage(path.toFile());
        } catch (Exception e) {
            logger.warn("Failed to load image: {}", path);
            logger.warn(Utils.formatExceptionText(e));
            return null;
        }
        if (full == null) return null;

        BufferedImage thumb = scaled(full, maxWidth, maxHeight, crop);

        if (cacheFile != null) {
            try {
                Files.createDirectories(cacheFile.getParent());
                deleteStaleCache(path, cacheFile, maxWidth, maxHeight, crop);
                ImageIO.write(thumb, "JPEG", cacheFile.toFile());
            } catch (IOException e) {
                logger.warn("Failed to write thumbnail cache: {}", cacheFile);
            }
        }

        return thumb;
    }

    private static BufferedImage scaled(BufferedImage src, int maxWidth, int maxHeight, String crop) {
        int iw = src.getWidth();
        int ih = src.getHeight();
        if (crop != null && !crop.isEmpty()) {
            // Fill width, then crop a maxHeight-tall slice from top/center/bottom.
            double scale = (double) maxWidth / iw;
            int scaledH = Math.max(1, (int) (ih * scale));
            int cropH   = Math.min(maxHeight, scaledH);
            int destY0  = switch (crop) {
                case "top"    -> 0;
                case "bottom" -> Math.max(0, scaledH - maxHeight);
                default       -> Math.max(0, (scaledH - maxHeight) / 2);
            };
            // Map destination crop back to source coordinates for direct sub-image draw.
            int srcY0 = (int) (destY0 / scale);
            int srcY1 = Math.min(ih, (int) ((destY0 + cropH) / scale));
            BufferedImage thumb = new BufferedImage(maxWidth, cropH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, maxWidth, cropH, 0, srcY0, iw, srcY1, null);
            g.dispose();
            return thumb;
        }
        double scale = Math.min((double) maxWidth / iw, (double) maxHeight / ih);
        int dw = Math.max(1, (int) (iw * scale));
        int dh = Math.max(1, (int) (ih * scale));
        BufferedImage thumb = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, dw, dh, null);
        g.dispose();
        return thumb;
    }

    private static void deleteStaleCache(Path path, Path keep, int maxWidth, int maxHeight, String crop) {
        try {
            String prefix = cacheKeyPrefix(path, maxWidth, maxHeight);
            String suffix = cacheKeySuffix(crop);
            try (var stream = Files.list(THUMB_CACHE_DIR)) {
                stream.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith(prefix) && name.endsWith(suffix) && !p.equals(keep);
                }).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException e) { logger.warn("Failed to delete stale cache: {}", p); }
                });
            }
        } catch (Exception e) {
            logger.warn("Failed to clean stale cache for: {}", path);
        }
    }

    private static Path cachePathFor(Path path, int maxWidth, int maxHeight, String crop) {
        try {
            long mtime = path.toFile().lastModified();
            return THUMB_CACHE_DIR.resolve(cacheKeyPrefix(path, maxWidth, maxHeight) + mtime + cacheKeySuffix(crop));
        } catch (Exception e) {
            return null;
        }
    }

    // Cache key = hash(absPath)_WxH_ + mtime + [_crop] + .jpg.  Size is part of the key so
    // different consumers (preview vs editor rows) don't collide on differently-sized thumbs.
    private static String cacheKeyPrefix(Path path, int maxWidth, int maxHeight) {
        int hash = Math.abs(path.toAbsolutePath().toString().hashCode());
        return hash + "_" + maxWidth + "x" + maxHeight + "_";
    }

    private static String cacheKeySuffix(String crop) {
        return ((crop != null && !crop.isEmpty()) ? "_" + crop : "") + ".jpg";
    }
}
