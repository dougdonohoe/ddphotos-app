package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Modal photo picker: a wrapping grid of thumbnails on the left, a large preview on the right.
 *
 * <p>Replaces the platform file dialog for the cover and hero image fields - see
 * {@link PhotoChooser} for how it is installed.
 *
 * <p>Thumbnails come from the shared, disk-cached {@link Thumbs} pipeline and are requested only for
 * the cells currently in (or just outside) the viewport, so opening a folder of several thousand
 * photos costs the same as opening one with a dozen.
 */
public class PhotoChooserDialog extends PhotosDialog {

    private static final Logger logger = LogManager.getLogger(PhotoChooserDialog.class);

    public static final String PHASE_NAME = "PhotoChooser";

    /** Directory to open in ({@link Path}). */
    public static final String PARAM_START_DIR = "photochooser-startdir";
    /** Directory browsing is confined to, or null to roam free ({@link Path}). */
    public static final String PARAM_ROOT_DIR = "photochooser-rootdir";
    /** Photo to open selected - the field's current value - or null ({@link Path}). */
    public static final String PARAM_SELECT = "photochooser-select";
    /** Whether videos are offered alongside photos ({@link Boolean}) - true for a cover, false for a hero. */
    public static final String PARAM_ALLOW_VIDEO = "photochooser-allow-video";

    private static final String BUTTON_CHOOSE = "choose";

    /** Style for the chooser's own widgets - the dialog's OptionsDialog fonts are headline sized. */
    private static final String CHOOSER_STYLE = "PhotoChooser";

    private static final int THUMB_W = 120;
    private static final int THUMB_H = 100;
    private static final int CELL_W  = THUMB_W + 16;
    private static final int CELL_H  = THUMB_H + 26;

    /** Rows of cells to decode beyond the viewport so scrolling isn't chasing the thumbnails. */
    private static final int LOOKAHEAD_CELLS = 12;

    /** Size of a cell's stand-in icon: a folder, or the camera-off/video-off "no preview" mark. */
    private static final int PLACEHOLDER_ICON_SIZE = 48;

    private static final int PREVIEW_SIZE = 280;
    private static final int PREVIEW_COL_W = PREVIEW_SIZE + 20;

    private Path currentDir_;
    private Path rootDir_;
    private boolean allowVideo_;

    /**
     * The field's existing photo, consumed by the first {@link #showFolder} so the chooser opens
     * on it.  One-shot: once the user starts navigating, each folder falls back to its first photo.
     */
    private Path pendingSelect_;

    private DDList<Entry> list_;
    private DDScrollPane scroll_;
    private DDButton upBtn_;
    private DDLabel pathLabel_;
    private DDLabel nameLabel_;
    private DDLabel dimsLabel_;
    private DDLabel sizeLabel_;
    private PhotoPreviewPanel preview_;

    private int pathLabelWidth_;

    // Thumbnail bookkeeping.  icons_ is what the renderer draws from; requested_ stops a cell from
    // being queued twice as it scrolls in and out; folderGen_ makes callbacks from the previous
    // folder harmless when they land after a navigation.
    private final Map<Path, Icon> icons_ = new HashMap<>();
    private final Set<Path> requested_ = new HashSet<>();
    private final List<Future<?>> thumbJobs_ = new ArrayList<>();
    private long folderGen_;

    /** Guards against a slow dimension read from a previous selection overwriting the current one. */
    private long metaGen_;

    /** One cell in the grid: a subfolder to descend into, or an image to pick. */
    record Entry(Path path, String name, boolean folder) {}

    // -------------------------------------------------------------------------
    // DialogPhase API
    // -------------------------------------------------------------------------

    @Override
    public JComponent createDialogContents() {
        rootDir_ = (Path) phase_.getObject(PARAM_ROOT_DIR);
        pendingSelect_ = (Path) phase_.getObject(PARAM_SELECT);
        allowVideo_ = phase_.getBoolean(PARAM_ALLOW_VIDEO, false);
        Path start = (Path) phase_.getObject(PARAM_START_DIR);
        if (start == null || !Files.isDirectory(start)) {
            start = rootDir_ != null ? rootDir_ : Path.of(System.getProperty("user.home"));
        }

        Dimension size = contentSize();
        DDPanel main = new DDPanel();
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        main.setBorderLayoutGap(6, 0);
        main.setPreferredSize(size);

        main.add(buildFolderBar(), BorderLayout.NORTH);
        main.add(buildGrid(),      BorderLayout.CENTER);
        main.add(buildPreview(),   BorderLayout.EAST);

        // The path label gets whatever the folder bar doesn't spend on the Up button.
        pathLabelWidth_ = size.width - upBtn_.getPreferredSize().width - 12;

        showFolder(start);
        return main;
    }

    /**
     * The dialog is a non-resizable internal frame, so it is sized once, here: as large as is
     * comfortable, but always inside the app window.
     */
    private Dimension contentSize() {
        Dimension frame = context_.getFrame().getSize();
        int w = Math.clamp(frame.width - 80, 620, 920);
        int h = Math.clamp(frame.height - 180, 420, 620);
        return new Dimension(w, h);
    }

    @Override
    protected void opened() {
        super.opened();
        checkButtons();
        stretchCellsToFill();
        loadVisibleThumbs();
    }

    @Override
    protected Component getFocusComponent() {
        return list_;
    }

    @Override
    public boolean processButton(AppButton button) {
        if (BUTTON_CHOOSE.equals(button.getName())) {
            Entry e = list_.getSelectedValue();
            if (e != null && !e.folder()) setResult(e.path());
        }
        removeDialog();
        return true;
    }

    @Override
    public void finish() {
        cancelThumbJobs();
        super.finish();
    }

    // -------------------------------------------------------------------------
    // Build UI
    // -------------------------------------------------------------------------

    private JComponent buildFolderBar() {
        upBtn_ = DDIconButtons.iconButton("photoup", CHOOSER_STYLE, DDIconButtons.ARROW_UP);
        upBtn_.addActionListener(_ -> goUp());

        pathLabel_ = new DDLabel(GuiManager.DEFAULT, CHOOSER_STYLE);

        DDPanel bar = new DDPanel();
        bar.setBorderLayoutGap(0, 8);
        bar.add(upBtn_, BorderLayout.WEST);
        bar.add(pathLabel_, BorderLayout.CENTER);
        return bar;
    }

    private JComponent buildGrid() {
        list_ = new DDList<>(new DefaultListModel<>(), "photochooser", CHOOSER_STYLE);
        list_.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list_.setVisibleRowCount(-1);          // as many cells per row as the viewport width allows
        list_.setFixedCellWidth(CELL_W);
        list_.setFixedCellHeight(CELL_H);
        list_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list_.setCellRenderer(new TileRenderer());

        list_.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) selectionChanged();
        });

        list_.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent ev) {
                if (ev.getClickCount() != 2) return;
                int i = list_.locationToIndex(ev.getPoint());
                Rectangle cell = i < 0 ? null : list_.getCellBounds(i, i);
                if (cell == null || !cell.contains(ev.getPoint())) return;
                list_.setSelectedIndex(i);
                activate(list_.getModel().getElementAt(i));
            }
        });

        // Enter descends into a folder rather than firing the dialog's default button; the list's
        // own binding runs before the root pane's default-button one.
        GuiUtils.addKeyAction(list_, JComponent.WHEN_FOCUSED, "photochooser-activate",
                new AbstractAction() {
                    public void actionPerformed(java.awt.event.ActionEvent e) {
                        activate(list_.getSelectedValue());
                    }
                }, KeyEvent.VK_ENTER, 0);

        // Backspace goes up a folder, as in any file browser.  Delete is spoken for - DialogPhase
        // maps it to the close button.
        GuiUtils.addKeyAction(list_, JComponent.WHEN_FOCUSED, "photochooser-up",
                new AbstractAction() {
                    public void actionPerformed(java.awt.event.ActionEvent e) {
                        goUp();
                    }
                }, KeyEvent.VK_BACK_SPACE, 0);

        scroll_ = new DDScrollPane(list_, CHOOSER_STYLE,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll_.setBorder(BorderFactory.createEtchedBorder());
        scroll_.getVerticalScrollBar().setUnitIncrement(CELL_H / 3);
        // Fires on scroll and on resize, which is exactly when the visible range and the width the
        // columns have to divide up can change.
        scroll_.getViewport().addChangeListener(_ -> {
            stretchCellsToFill();
            loadVisibleThumbs();
        });
        return scroll_;
    }

    private JComponent buildPreview() {
        preview_ = new PhotoPreviewPanel(PREVIEW_SIZE, PREVIEW_SIZE);
        preview_.setOpaque(false);

        nameLabel_ = new DDLabel(GuiManager.DEFAULT, CHOOSER_STYLE);
        dimsLabel_ = new DDLabel(GuiManager.DEFAULT, CHOOSER_STYLE);
        sizeLabel_ = new DDLabel(GuiManager.DEFAULT, CHOOSER_STYLE);

        DDPanel info = new DDPanel();
        info.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 2, VerticalFlowLayout.LEFT));
        info.add(nameLabel_);
        info.add(dimsLabel_);
        info.add(sizeLabel_);

        DDPanel col = new DDPanel();
        col.setBorderLayoutGap(8, 0);
        col.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        col.setPreferredSize(new Dimension(PREVIEW_COL_W, 0));
        col.add(preview_, BorderLayout.NORTH);
        col.add(info, BorderLayout.CENTER);
        return col;
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private void showFolder(Path dir) {
        cancelThumbJobs();
        folderGen_++;
        icons_.clear();
        requested_.clear();

        currentDir_ = dir;
        DefaultListModel<Entry> model = new DefaultListModel<>();
        listEntries(dir, allowVideo_).forEach(model::addElement);
        list_.setModel(model);

        upBtn_.setEnabled(canGoUp(dir, rootDir_));
        pathLabel_.setText(elideLeft(dir.toString(), pathLabel_, pathLabelWidth_));

        // Open on the photo the field already holds; otherwise start on the first one, so the
        // preview has something in it and Choose is live either way.
        int select = indexOf(model, pendingSelect_);
        pendingSelect_ = null;
        if (select < 0) {
            for (int i = 0; i < model.size(); i++) {
                if (!model.get(i).folder()) { select = i; break; }
            }
        }
        list_.setSelectedIndex(select);
        selectionChanged();

        SwingUtilities.invokeLater(() -> {
            scroll_.getViewport().setViewPosition(new Point(0, 0));
            if (list_.getSelectedIndex() >= 0) list_.ensureIndexIsVisible(list_.getSelectedIndex());
            stretchCellsToFill();
            loadVisibleThumbs();
        });
    }

    /** Index of the cell for {@code path}, or -1 when it isn't in this folder. */
    private static int indexOf(ListModel<Entry> model, Path path) {
        if (path == null) return -1;
        for (int i = 0; i < model.getSize(); i++) {
            if (model.getElementAt(i).path().equals(path)) return i;
        }
        return -1;
    }

    private void goUp() {
        if (canGoUp(currentDir_, rootDir_)) showFolder(currentDir_.getParent());
    }

    /** Double-click / Enter: descend into a folder, choose an image. */
    private void activate(Entry e) {
        if (e == null) return;
        if (e.folder()) showFolder(e.path());
        else if (okayButton_ != null && okayButton_.isEnabled()) okayButton_.doClick();
    }

    private void selectionChanged() {
        Entry e = list_.getSelectedValue();
        metaGen_++;
        if (e == null || e.folder()) {
            preview_.setImageFile(null);
            nameLabel_.setText(e == null ? emptyFolderText() : e.name());
            dimsLabel_.setText("");
            sizeLabel_.setText("");
        } else {
            preview_.setImageFile(e.path());
            nameLabel_.setText(e.name());
            sizeLabel_.setText(formatFileSize(e.path()));
            loadDimensions(e.path());
        }
        checkButtons();
    }

    private String emptyFolderText() {
        if (list_.getModel().getSize() != 0) return "";
        return PropertyConfig.getMessage(allowVideo_ ? "msg.photochooser.empty.media"
                                                     : "msg.photochooser.empty");
    }

    @Override
    protected void checkButtons() {
        if (okayButton_ == null) return;
        Entry e = list_.getSelectedValue();
        okayButton_.setEnabled(e != null && !e.folder());
    }

    // -------------------------------------------------------------------------
    // Listing rules (Swing-free, unit tested)
    // -------------------------------------------------------------------------

    /**
     * Subfolders first, then photos, each sorted case-insensitively.  Anything that isn't a photo
     * is left out - this is a photo picker - as are dotfiles and unreadable entries.  With
     * {@code allowVideo} the picker also offers clips: an album cover may be a video (photogen uses
     * its poster frame), a site hero may not.
     */
    static List<Entry> listEntries(Path dir, boolean allowVideo) {
        List<Entry> folders = new ArrayList<>();
        List<Entry> images = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (name.startsWith(".")) continue;
                if (Files.isDirectory(p)) folders.add(new Entry(p, name, true));
                else if (matches(name, allowVideo) && !name.isBlank()) {
                    images.add(new Entry(p, name, false));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to list {}: {}", dir, e.getMessage());
        }
        Comparator<Entry> byName = Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
        folders.sort(byName);
        images.sort(byName);

        List<Entry> all = new ArrayList<>(folders.size() + images.size());
        all.addAll(folders);
        all.addAll(images);
        return all;
    }

    private static boolean matches(String name, boolean allowVideo) {
        return allowVideo ? PathValidation.isMediaFile(name) : PathValidation.isImageFile(name);
    }

    /**
     * True when {@code dir} has a parent to climb to.  With a root set - the album's source folder
     * or the hero's base - the root is the ceiling, since a pick from above it could not be
     * expressed relative to that base anyway.
     */
    static boolean canGoUp(Path dir, Path root) {
        if (dir == null || dir.getParent() == null) return false;
        return root == null || !dir.normalize().equals(root.normalize());
    }

    // -------------------------------------------------------------------------
    // Thumbnails
    // -------------------------------------------------------------------------

    /**
     * Queues thumbnail decodes for the cells on screen (plus a little beyond).  Called on every
     * viewport change, so it must be cheap for cells it has already handled - {@link #requested_}
     * is what makes it so.
     */
    private void loadVisibleThumbs() {
        ListModel<Entry> model = list_.getModel();
        int first = list_.getFirstVisibleIndex();
        if (first < 0) return;
        int last = list_.getLastVisibleIndex();

        first = Math.max(0, first - LOOKAHEAD_CELLS);
        last  = Math.min(model.getSize() - 1, last + LOOKAHEAD_CELLS);

        long gen = folderGen_;
        for (int i = first; i <= last; i++) {
            Entry e = model.getElementAt(i);
            if (e.folder() || !requested_.add(e.path())) continue;
            thumbJobs_.add(Thumbs.loadAsyncForDisplay(list_, e.path(), THUMB_W, THUMB_H, null, img -> {
                if (gen != folderGen_) return;   // navigated away while this was decoding
                icons_.put(e.path(), boxed(img != null ? thumbIcon(list_, img)
                                                       : Thumbs.placeholderIcon(e.path(), PLACEHOLDER_ICON_SIZE)));
                list_.repaint();
            }));
        }
    }

    /**
     * Widens the cells so the columns divide the viewport exactly.  A fixed cell width leaves
     * however much doesn't make up a whole column as dead list background down the right-hand
     * edge; handing that slack to the columns spreads it into the gutters instead, at any dialog
     * size and whether the scrollbar is showing.  The thumbnails stay {@link #THUMB_W}
     * wide - they are centered in the cell - so only the spacing changes.
     */
    private void stretchCellsToFill() {
        int avail = scroll_.getViewport().getExtentSize().width;
        if (avail < CELL_W) return;
        int columns = avail / CELL_W;
        int width = avail / columns;
        // Guarded so the revalidate this triggers can't bounce back through the viewport listener.
        if (width != list_.getFixedCellWidth()) list_.setFixedCellWidth(width);
    }

    private void cancelThumbJobs() {
        for (Future<?> job : thumbJobs_) job.cancel(true);
        thumbJobs_.clear();
    }

    /**
     * Wrap a device-pixel thumbnail (see {@link Thumbs#loadAsyncForDisplay}) in an Icon that draws
     * at logical size - the same treatment the caption editor's rows get.
     */
    private static Icon thumbIcon(JComponent owner, BufferedImage img) {
        double scale = RenderUtils.getDisplayScale(owner);
        return new ImageComponent(img, scale > 0 ? 1.0d / scale : 1.0d).getUniqueIcon();
    }

    private static Icon folderIcon() {
        return DDIconButtons.svgIcon(DDIconButtons.FOLDER_OPEN, PLACEHOLDER_ICON_SIZE, "Label.foreground");
    }

    private static Icon boxed(Icon icon) {
        return new BoxIcon(icon, THUMB_W, THUMB_H);
    }

    /**
     * Fixed-size box holding a smaller icon centered in it.  Photos come back at every aspect
     * ratio; without this the icon height would vary cell to cell and the filenames under them
     * would sit at a different height in every column.
     */
    private record BoxIcon(Icon delegate, int width, int height) implements Icon {
        public int getIconWidth()  { return width; }
        public int getIconHeight() { return height; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (delegate == null) return;
            delegate.paintIcon(c, g, x + (width  - delegate.getIconWidth())  / 2,
                                     y + (height - delegate.getIconHeight()) / 2);
        }
    }

    /** A tile: thumbnail (or folder / no-preview icon) above an elided filename. */
    private final class TileRenderer extends DefaultListCellRenderer {
        private final Icon folder = boxed(folderIcon());
        private final Icon pending = boxed(null);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Entry e = (Entry) value;
            super.getListCellRendererComponent(list, e.name(), index, isSelected, cellHasFocus);
            setHorizontalAlignment(CENTER);
            setHorizontalTextPosition(CENTER);
            setVerticalTextPosition(BOTTOM);
            setIcon(e.folder() ? folder : icons_.getOrDefault(e.path(), pending));
            setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            return this;
        }
    }

    // -------------------------------------------------------------------------
    // Preview metadata
    // -------------------------------------------------------------------------

    /**
     * Reads the image's pixel dimensions off the EDT and fills in the label - the header read is
     * quick, but it is still disk I/O, and selection moves with the arrow keys.
     */
    private void loadDimensions(Path path) {
        long gen = metaGen_;
        dimsLabel_.setText("");
        new SwingWorker<Dimension, Void>() {
            protected Dimension doInBackground() { return readImageSize(path); }
            protected void done() {
                if (gen != metaGen_) return;   // selection moved on
                Dimension d = null;
                try {
                    d = get();
                } catch (Exception e) {
                    logger.debug("Could not read image size: {}", path);
                }
                dimsLabel_.setText(d == null ? ""
                        : PropertyConfig.getMessage("msg.photochooser.dimensions", d.width, d.height));
            }
        }.execute();
    }

    /** Pixel dimensions straight from the file header, or null when no reader recognizes it. */
    private static Dimension readImageSize(Path path) {
        try (ImageInputStream in = ImageIO.createImageInputStream(path.toFile())) {
            if (in == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatFileSize(Path path) {
        long bytes = path.toFile().length();
        if (bytes <= 0) return "";
        String[] units = {"bytes", "KB", "MB", "GB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        String amount = unit == 0 ? String.valueOf(bytes) : String.format("%.1f", value);
        return PropertyConfig.getMessage("msg.photochooser.filesize", amount, units[unit]);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Trims a path from the left to fit, since the tail - the folder you are actually in - is the
     * part worth showing.  The full path stays available as the tooltip.
     */
    private static String elideLeft(String text, JComponent c, int maxWidth) {
        FontMetrics fm = c.getFontMetrics(c.getFont());
        if (maxWidth <= 0 || fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int start = 0;
        while (start < text.length() && fm.stringWidth(ellipsis + text.substring(start)) > maxWidth) {
            start++;
        }
        return ellipsis + text.substring(start);
    }
}
