package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppConfigUtils;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.ImageDef;
import com.donohoedigital.gui.DDIconButtons;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public class PhotoPreviewPanel extends JPanel {

    private static final Logger logger = LogManager.getLogger(PhotoPreviewPanel.class);
    private static final int PLACEHOLDER_ICON_SIZE = 48;
    private static final Path THUMB_CACHE_DIR = AppConfigUtils.getCacheDir().toPath().resolve("thumbs");

    private final Icon placeholderIcon;
    private final int maxWidth;
    private final int maxHeight;
    private String crop_;
    private Path currentPath_;
    private BufferedImage image;
    private boolean isLoading = false;
    private SwingWorker<BufferedImage, Void> loadWorker;

    public PhotoPreviewPanel(int maxWidth, int maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        placeholderIcon = DDIconButtons.svgIcon(DDIconButtons.CAMERA_OFF, PLACEHOLDER_ICON_SIZE, "Label.disabledForeground");
    }

    @Override
    public Dimension getPreferredSize() {
        Insets ins = getInsets();
        return new Dimension(maxWidth + ins.left + ins.right, maxHeight + ins.top + ins.bottom);
    }

    public void setCrop(String crop) {
        if (!java.util.Objects.equals(crop_, crop)) {
            crop_ = crop;
            if (currentPath_ != null) {
                Path path = currentPath_;
                currentPath_ = null;  // force reload since crop changed
                setImageFile(path);
            }
        }
    }

    public void setImageFile(Path path) {
        if (java.util.Objects.equals(currentPath_, path)) return;
        currentPath_ = path;
        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }
        if (path == null || !path.toFile().exists()) {
            isLoading = false;
            image = null;
            repaint();
            return;
        }

        isLoading = true;
        loadWorker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                return loadThumbnail(path);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                isLoading = false;
                try {
                    image = get();
                } catch (InterruptedException | ExecutionException e) {
                    logger.warn("Failed to load preview for: {}", path);
                    image = null;
                }
                repaint();
            }
        };
        loadWorker.execute();
    }

    // -------------------------------------------------------------------------
    // Thumbnail loading and caching
    // -------------------------------------------------------------------------

    private BufferedImage loadThumbnail(Path path) {
        Path cacheFile = cachePathFor(path);
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

        BufferedImage thumb = scaledThumbnail(full);

        if (cacheFile != null) {
            try {
                Files.createDirectories(cacheFile.getParent());
                deleteStaleCache(path, cacheFile);
                ImageIO.write(thumb, "JPEG", cacheFile.toFile());
            } catch (IOException e) {
                logger.warn("Failed to write thumbnail cache: {}", cacheFile);
            }
        }

        return thumb;
    }

    private BufferedImage scaledThumbnail(BufferedImage src) {
        int iw = src.getWidth();
        int ih = src.getHeight();
        if (crop_ != null && !crop_.isEmpty()) {
            // Fill width, then crop a maxHeight-tall slice from top/center/bottom.
            double scale = (double) maxWidth / iw;
            int scaledH = Math.max(1, (int) (ih * scale));
            int cropH   = Math.min(maxHeight, scaledH);
            int destY0  = switch (crop_) {
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

    private void deleteStaleCache(Path path, Path keep) {
        try {
            int hash = Math.abs(path.toAbsolutePath().toString().hashCode());
            String cropSuffix = (crop_ != null && !crop_.isEmpty()) ? "_" + crop_ : "";
            String prefix = hash + "_";
            String suffix = cropSuffix + ".jpg";
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

    private Path cachePathFor(Path path) {
        try {
            long mtime = path.toFile().lastModified();
            int hash = Math.abs(path.toAbsolutePath().toString().hashCode());
            String cropSuffix = (crop_ != null && !crop_.isEmpty()) ? "_" + crop_ : "";
            return THUMB_CACHE_DIR.resolve(hash + "_" + mtime + cropSuffix + ".jpg");
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g = (Graphics2D) g1.create();
        try {
            if (image == null) {
                paintPlaceholder(g);
            } else {
                paintImage(g);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintPlaceholder(Graphics2D g) {
        if (isLoading) return;
        Insets ins = getInsets();
        int availW = getWidth()  - ins.left - ins.right;
        int availH = getHeight() - ins.top  - ins.bottom;
        int x = ins.left + (availW - PLACEHOLDER_ICON_SIZE) / 2;
        int y = ins.top  + (availH - PLACEHOLDER_ICON_SIZE) / 2;
        placeholderIcon.paintIcon(this, g, x, y);
    }

    private void paintImage(Graphics2D g) {
        Insets ins = getInsets();
        int availW = getWidth()  - ins.left - ins.right;
        int availH = getHeight() - ins.top  - ins.bottom;
        int x = ins.left + (availW - image.getWidth())  / 2;
        int y = ins.top  + (availH - image.getHeight()) / 2;
        g.drawImage(image, x, y, null);
    }
}
