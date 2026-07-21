package com.donohoedigital.ddphotos;

import com.donohoedigital.gui.DDIconButtons;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public class PhotoPreviewPanel extends JPanel {

    private static final Logger logger = LogManager.getLogger(PhotoPreviewPanel.class);
    private static final int PLACEHOLDER_ICON_SIZE = 48;

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
                return Thumbs.load(path, maxWidth, maxHeight, crop_);
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
