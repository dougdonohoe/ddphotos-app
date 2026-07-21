package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.EngineConstants;
import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.BasePhase;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.DataElement;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.config.StylesConfig;
import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.PhotogenFile;
import com.donohoedigital.ddphotos.config.PhotogenFileException;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Standalone window for editing an album's per-folder {@code photogen.txt} captions and order.
 * One window per album — see {@link #open}, which focuses an existing window rather than opening a
 * duplicate.  The Pick-Folder combo switches between the album's source ("Main") and its subfolders
 * (when the album is recursive); every folder's {@link PhotogenFile} is held in memory and a single
 * Save persists all changed folders at once.
 */
public class PhotogenEditorPhase extends BasePhase {

    private static final Logger logger = LogManager.getLogger(PhotogenEditorPhase.class);
    private static final String STYLE = "Options";
    private static final int THUMB_MIN_H = 72;
    private static final int NAME_COL_WIDTH = 240;

    // Thumbnail cell size — computed in buildUI to fill the row: height matches the caption box,
    // width is landscape (3:2) so typical photos use the available space.
    private int thumbW_ = 108;
    private int thumbH_ = THUMB_MIN_H;

    static final String PARAM_SITE = "site";
    static final String PARAM_ALBUM = "album";
    static final String PARAM_KEY = "key";

    /** One open editor per album (key = siteId/slug); prevents duplicate windows. */
    private static final Map<String, PhotogenEditorPhase> OPEN = new HashMap<>();

    private final Preferences prefs_ = PhotosConstants.getAppPreferences();

    private Site site_;
    private AlbumEntry album_;
    private AlbumsFile albumsFile_;
    private String key_;
    private Path sourceDir_;

    private final Map<Path, PhotogenFile> filesByDir_ = new LinkedHashMap<>();
    private final Map<String, Path> dirByValue_ = new LinkedHashMap<>();
    private final Map<Path, FolderEditor> editors_ = new LinkedHashMap<>();

    private boolean built_;
    private boolean switching_;

    private DDPanel base_;
    private OptionCombo<String> folderCombo_;
    private JScrollPane scroll_;
    private JPanel rowsPanel_;
    private DDButton upBtn_, downBtn_;
    private DDButton saveBtn_, saveCloseBtn_;
    private DDHtmlArea helptext_;
    private DDLabel siteLabel_;

    private FolderEditor currentFolder_;
    private Row selectedRow_;

    // -------------------------------------------------------------------------
    // Launch / single-instance
    // -------------------------------------------------------------------------

    /** Opens (or focuses the existing) caption editor window for the given album. */
    public static void open(AppContext context, Site site, AlbumEntry album) {
        if (context == null || site == null || album == null) return;
        String key = keyFor(site, album);
        PhotogenEditorPhase existing = OPEN.get(key);
        if (existing != null && existing.context_ != null) {
            existing.context_.getWindow().toFront();
            return;
        }
        TypedHashMap params = new TypedHashMap();
        params.setObject(PARAM_SITE, site);
        params.setObject(PARAM_ALBUM, album);
        params.setString(PARAM_KEY, key);
        // Give each album its own window identity: a distinct title and its own remembered
        // size/position (the engine keys both off the window name).
        params.setString(EngineConstants.PARAM_WINDOW_NAME, "photogen-editor-" + site.getIdOrDefault() + "-" + album.getSlug());
        params.setString(EngineConstants.PARAM_WINDOW_TITLE, PropertyConfig.getMessage(
                "msg.windowtitle.PhotogenEditor.full",
                site.getDisplayName() != null ? site.getDisplayName() : site.getIdOrDefault(),
                album.getName() != null ? album.getName() : album.getSlug()));
        context.processPhase("PhotogenEditor", params);
    }

    private static String keyFor(Site site, AlbumEntry album) {
        return site.getIdOrDefault() + "/" + album.getSlug();
    }

    // -------------------------------------------------------------------------
    // Phase lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void start() {
        if (!built_) {
            site_ = (Site) phase_.getObject(PARAM_SITE);
            album_ = (AlbumEntry) phase_.getObject(PARAM_ALBUM);
            key_ = phase_.getString(PARAM_KEY, keyFor(site_, album_));
            albumsFile_ = site_.getAlbumsFile();
            OPEN.put(key_, this);
            buildUI();
            built_ = true;
        }
        context_.setMainUIComponent(this, base_, true, folderCombo_);
        context_.getWindow().setHelpTextWidget(helptext_);
        context_.getWindow().showHelp(siteLabel_); // init help
    }

    @Override
    public void finish() {
        if (key_ != null && OPEN.get(key_) == this) {
            OPEN.remove(key_);
        }
        super.finish();
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void buildUI() {
        loadPhotogenFiles();

        // Size thumbnails to fill a row: match the caption box height, landscape (3:2) width.
        int captionHeight = new OptionTextArea(null, "photogencaption", STYLE, null,
                new TypedHashMap(), 2000, null, 2, 500).getPreferredSize().height;
        thumbH_ = Math.max(THUMB_MIN_H, captionHeight);
        thumbW_ = Math.round(thumbH_ * 1.5f);

        base_ = new DDPanel();

        JComponent topBar = buildTopBar();
        topBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 0, 10));
        base_.add(topBar, BorderLayout.NORTH);

        DDPanel center = new DDPanel();
        center.setOpaque(true);
        Color panelBg = StylesConfig.getColor("app.panel.bg");
        center.setBackground(panelBg);
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        base_.add(center, BorderLayout.CENTER);
        rowsPanel_ = new ScrollingRows();
        rowsPanel_.setLayout(new BoxLayout(rowsPanel_, BoxLayout.Y_AXIS));
        scroll_ = new JScrollPane(rowsPanel_);
        scroll_.setBorder(BorderFactory.createEtchedBorder());
        scroll_.setBackground(panelBg);
        scroll_.getViewport().setBackground(panelBg);
        scroll_.getVerticalScrollBar().setUnitIncrement(16);
        center.add(scroll_, BorderLayout.CENTER);
        center.add(buildBottomBar(), BorderLayout.SOUTH);

        // SOUTH: help text
        helptext_ = PhotosUtils.createHelpText(PhotosBasePhase.STYLE);
        base_.add(helptext_, BorderLayout.SOUTH);

        Path initial = initialFolderDir();
        switching_ = true;
        folderCombo_.getComboBox().setSelectedItem(valueForDir(initial));
        switching_ = false;
        showFolder(initial);
        onDirtyChanged();
    }

    private JComponent buildTopBar() {
        DDPanel widgets = new DDPanel();
        widgets.setLayout(new BoxLayout(widgets, BoxLayout.X_AXIS));
        widgets.setBorder(BorderFactory.createEmptyBorder(11, 20, 0, 0));
        siteLabel_ = new DDLabel("photogensite", STYLE);
        siteLabel_.setText(siteLabelHtml());
        widgets.add(siteLabel_);
        widgets.add(Box.createHorizontalGlue());

        folderCombo_ = new OptionCombo<>(buildFolderElement(), null,
                "photogenfolder", STYLE, new TypedHashMap(), 240, true);
        folderCombo_.setRequired(false);
        folderCombo_.getComboBox().addActionListener(_ -> onFolderChanged());
        Dimension size = folderCombo_.getPreferredSize();
        folderCombo_.setMaximumSize(new Dimension(Math.max(240, size.width), size.height));
        widgets.add(folderCombo_);

        DDPanel bar = new DDPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.add(new DDImageButton("icon48"));
        bar.add(GuiUtils.NORTH(widgets));

        return bar;
    }

    private JComponent buildBottomBar() {
        DDPanel bar = new DDPanel();
        bar.setLayout(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        upBtn_   = DDIconButtons.iconButton("photogenup",   STYLE, DDIconButtons.CHEVRON_UP);
        downBtn_ = DDIconButtons.iconButton("photogendown", STYLE, DDIconButtons.CHEVRON_DOWN);
        upBtn_.addActionListener(_ -> moveSelected(-1));
        downBtn_.addActionListener(_ -> moveSelected(1));
        JPanel reorder = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        reorder.setOpaque(false);
        reorder.add(upBtn_);
        reorder.add(downBtn_);
        bar.add(reorder, BorderLayout.WEST);

        DDButton cancel = new DDButton("cancel", STYLE);
        saveBtn_ = new DDButton("save", STYLE);
        saveCloseBtn_ = new DDButton("saveclose", STYLE);
        cancel.addActionListener(_ -> onCancel());
        saveBtn_.addActionListener(_ -> onSave());
        saveCloseBtn_.addActionListener(_ -> onSaveClose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        actions.add(saveBtn_);
        actions.add(saveCloseBtn_);
        bar.add(actions, BorderLayout.EAST);

        return bar;
    }

    private String siteLabelHtml() {
        String albumName = album_.getName() != null ? album_.getName() : album_.getSlug();
        return "<html><b>" + PhotosUtils.escapeHtml(albumName) + "</b> &nbsp;|&nbsp; " +
                PhotosUtils.siteLabelHtml(site_) + "</html>";
    }

    // -------------------------------------------------------------------------
    // Folder model
    // -------------------------------------------------------------------------

    private void loadPhotogenFiles() {
        if (albumsFile_ == null) return;
        List<PhotogenFile> pfs = albumsFile_.getPhotogenFiles(album_);
        if (pfs.isEmpty()) return;
        sourceDir_ = pfs.getFirst().getDir();
        for (PhotogenFile pf : pfs) {
            filesByDir_.put(pf.getDir(), pf);
        }
    }

    private DataElement<String> buildFolderElement() {
        List<String> values = new ArrayList<>();
        List<String> displays = new ArrayList<>();
        for (Path dir : filesByDir_.keySet()) {
            String value = valueForDir(dir);
            dirByValue_.put(value, dir);
            values.add(value);
            displays.add(value.isEmpty()
                    ? PropertyConfig.getMessage("msg.photogen.folder.main")
                    : value);
        }
        return new DataElement<>("photogenfolder", values, displays);
    }

    private String valueForDir(Path dir) {
        if (dir == null || dir.equals(sourceDir_)) return "";
        return sourceDir_.relativize(dir).toString().replace('\\', '/');
    }

    private Path initialFolderDir() {
        String saved = prefs_.get(folderPrefKey(), "");
        Path dir = dirByValue_.get(saved);
        return dir != null ? dir : sourceDir_;
    }

    private String folderPrefKey() {
        return "photogen.folder." + key_;
    }

    private void onFolderChanged() {
        if (switching_) return;
        Object value = folderCombo_.getSelectedItem();
        Path dir = dirByValue_.get(value == null ? "" : value.toString());
        if (dir == null || (currentFolder_ != null && dir.equals(currentFolder_.dir))) return;
        prefs_.put(folderPrefKey(), value == null ? "" : value.toString());
        showFolder(dir);
    }

    private void showFolder(Path dir) {
        if (dir == null) return;
        currentFolder_ = editors_.computeIfAbsent(dir, this::buildFolderEditor);
        rebuildRows();
        selectedRow_ = currentFolder_.rows.isEmpty() ? null : currentFolder_.rows.getFirst();
        applySelectionHighlight();
        updateReorderButtons();
        // Reset to the top for a fresh open and on every folder switch (after the new rows lay out).
        SwingUtilities.invokeLater(() -> scroll_.getViewport().setViewPosition(new Point(0, 0)));
    }

    private FolderEditor buildFolderEditor(Path dir) {
        PhotogenFile pf = filesByDir_.get(dir);
        FolderEditor f = new FolderEditor(dir, pf);

        List<Row> images = new ArrayList<>();
        List<Row> subfolders = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String fileName = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    if (album_.isRecurse()) {
                        subfolders.add(Row.subfolder(fileName, p, pf.getCaption(fileName)));
                    }
                } else if (PathValidation.isImageFile(fileName)) {
                    images.add(Row.image(fileName, p, pf.getCaption(fileName)));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to list {}: {}", dir, e.getMessage());
        }
        images.sort(Comparator.comparing(r -> r.displayName, String.CASE_INSENSITIVE_ORDER));
        subfolders.sort(Comparator.comparing(r -> r.displayName, String.CASE_INSENSITIVE_ORDER));

        // Order by photogen.txt first, then append any on-disk items not listed.
        Map<String, Row> byMatchKey = new LinkedHashMap<>();
        for (Row r : images) byMatchKey.putIfAbsent(r.matchKey, r);
        for (Row r : subfolders) byMatchKey.putIfAbsent(r.matchKey, r);

        List<Row> ordered = new ArrayList<>();
        for (String entryKey : pf.getEntryKeys()) {
            Row r = byMatchKey.remove(PhotogenFile.normalizeKey(entryKey));
            if (r != null) ordered.add(r);
        }
        for (Row r : images) if (byMatchKey.containsKey(r.matchKey)) { ordered.add(r); byMatchKey.remove(r.matchKey); }
        for (Row r : subfolders) if (byMatchKey.containsKey(r.matchKey)) { ordered.add(r); byMatchKey.remove(r.matchKey); }

        f.rows = ordered;
        f.originalOrderKeys = ordered.stream().map(r -> r.matchKey).toList();
        return f;
    }

    // -------------------------------------------------------------------------
    // Row rendering
    // -------------------------------------------------------------------------

    private void rebuildRows() {
        rowsPanel_.removeAll();
        for (Row row : currentFolder_.rows) {
            if (row.comp == null) row.comp = buildRowComponent(row);
            rowsPanel_.add(row.comp);
        }
        rowsPanel_.revalidate();
        rowsPanel_.repaint();
    }

    private JComponent buildRowComponent(Row row) {
        DDPanel p = new DDPanel();
        p.setLayout(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(rowBorder(false));

        // Column 0 — filename / subfolder name (fixed width so every row's thumb lines up).
        // Wrap long names rather than truncating them; the taller rows give room for 2–3 lines.
        DDLabel name = new DDLabel("photogenrowname", STYLE);
        setWrappedName(name, row.displayName);
        name.setToolTipText(row.displayName);
        name.setVerticalAlignment(SwingConstants.CENTER);
        fixSize(name, NAME_COL_WIDTH, thumbH_);
        GridBagConstraints nc = new GridBagConstraints();
        nc.gridx = 0; nc.gridy = 0; nc.weightx = 0;
        nc.anchor = GridBagConstraints.WEST;
        nc.insets = new Insets(0, 0, 0, 8);
        p.add(name, nc);

        // Column 1 — thumbnail or subfolder Edit button (fixed THUMB-square cell).
        JComponent middle;
        if (row.type == RowType.IMAGE) {
            middle = makeThumbLabel(row.path);
        } else {
            DDButton edit = new DDButton("photogenfolderedit", STYLE);
            edit.addActionListener(_ -> folderCombo_.getComboBox().setSelectedItem(valueForDir(row.path)));
            JPanel wrap = new JPanel(new GridBagLayout());  // centers the button vertically + horizontally
            wrap.setOpaque(false);
            wrap.add(edit);
            middle = wrap;
        }
        fixSize(middle, thumbW_, thumbH_);
        GridBagConstraints mc = new GridBagConstraints();
        mc.gridx = 1; mc.gridy = 0; mc.weightx = 0;
        mc.anchor = GridBagConstraints.CENTER;
        mc.insets = new Insets(0, 0, 0, 8);
        p.add(middle, mc);

        // Column 2 — caption (wrapping, single logical line), fills the remaining width.
        GridBagConstraints cc = new GridBagConstraints();
        cc.gridx = 2; cc.gridy = 0; cc.weightx = 1;
        cc.fill = GridBagConstraints.BOTH;
        if (row.type == RowType.IMAGE) {
            p.add(makeCaptionArea(row), cc);
        } else {
            p.add(Box.createGlue(), cc);
        }

        MouseAdapter select = new MouseAdapter() {
            public void mousePressed(MouseEvent e) { selectRow(row); }
        };
        p.addMouseListener(select);
        name.addMouseListener(select);
        return p;
    }

    /**
     * A wrapping caption editor: word-wrap is on, but carriage returns are disallowed (Enter does
     * nothing and pasted newlines collapse to spaces) so a caption stays a single logical line.
     */
    private OptionTextArea  makeCaptionArea(Row row) {
        OptionTextArea captionArea = new OptionTextArea(null, "photogencaption", STYLE,
                null, new TypedHashMap(), 2000, null, 2, 500);
        DDTextArea caption = captionArea.getTextArea();
        caption.setText(row.caption);
        caption.setTabChangesFocus(true);
        // Disallow carriage returns: Enter is a no-op; pasted newlines become spaces.
        caption.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
        caption.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "none");
        ((AbstractDocument) caption.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override public void insertString(FilterBypass fb, int off, String s, AttributeSet a) throws BadLocationException {
                super.insertString(fb, off, collapse(s), a);
            }
            @Override public void replace(FilterBypass fb, int off, int len, String s, AttributeSet a) throws BadLocationException {
                super.replace(fb, off, len, collapse(s), a);
            }
            private String collapse(String s) { return s == null ? null : s.replaceAll("[\\r\\n]+", " "); }
        });
        caption.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() { row.caption = caption.getText(); onDirtyChanged(); }
            public void insertUpdate(DocumentEvent e)  { changed(); }
            public void removeUpdate(DocumentEvent e)  { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        });
        caption.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent e) {
                selectRow(row);
                // When tabbing (or clicking) into a caption, make sure its row is fully on screen.
                if (row.comp != null) {
                    row.comp.scrollRectToVisible(new Rectangle(0, 0, row.comp.getWidth(), row.comp.getHeight()));
                }
            }
        });
        // Captions rarely need to scroll internally; forward wheel events to the outer list so the
        // pointer being over a caption doesn't swallow the scroll.
        caption.addMouseWheelListener(e -> {
            Point p = SwingUtilities.convertPoint(caption, e.getPoint(), scroll_);
            scroll_.dispatchEvent(new MouseWheelEvent(scroll_, e.getID(), e.getWhen(), e.getModifiersEx(),
                    p.x, p.y, e.getClickCount(), e.isPopupTrigger(), e.getScrollType(),
                    e.getScrollAmount(), e.getWheelRotation()));
        });
        return captionArea;
    }

    /**
     * Sets a filename/subfolder label, wrapping it to the name column via {@code <br>}.  Swing's
     * JLabel HTML only wraps at spaces (not hyphens/dots), and filenames have none — so we break
     * the text ourselves at separators sized to the column, keeping the full name visible.
     */
    private void setWrappedName(DDLabel label, String text) {
        FontMetrics fm = label.getFontMetrics(label.getFont());
        List<String> lines = wrapToWidth(text, fm, NAME_COL_WIDTH - 8);
        StringBuilder sb = new StringBuilder("<html>");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("<br>");
            sb.append(PhotosUtils.escapeHtml(lines.get(i)));
        }
        label.setText(sb.append("</html>").toString());
    }

    /** Greedily splits {@code s} into lines no wider than {@code max}px, preferring to break after
     *  a separator ({@code - _ . space}) and hard-breaking only when a single run overflows. */
    private static List<String> wrapToWidth(String s, FontMetrics fm, int max) {
        List<String> lines = new ArrayList<>();
        int start = 0, n = s.length();
        while (start < n) {
            int end = start, lastBreak = -1;
            while (end < n) {
                if (fm.stringWidth(s.substring(start, end + 1)) > max && end > start) break;
                char c = s.charAt(end);
                if (c == '-' || c == '_' || c == '.' || c == ' ') lastBreak = end + 1;
                end++;
            }
            int cut = (end >= n) ? n : (lastBreak > start ? lastBreak : end);
            lines.add(s.substring(start, cut));
            start = cut;
        }
        return lines;
    }

    private static void fixSize(JComponent c, int w, int h) {
        Dimension d = new Dimension(w, h);
        c.setMinimumSize(d);
        c.setPreferredSize(d);
        c.setMaximumSize(d);
    }

    private JLabel makeThumbLabel(Path path) {
        JLabel label = new JLabel();
        label.setPreferredSize(new Dimension(thumbW_, thumbH_));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        new SwingWorker<BufferedImage, Void>() {
            protected BufferedImage doInBackground() { return Thumbs.load(path, thumbW_, thumbH_, null); }
            protected void done() {
                BufferedImage img = null;
                try {
                    img = get();
                } catch (Exception e) {
                    logger.warn("thumbnail load failed: {}", path);
                }
                // Decoded image, or the "no preview" camera-off placeholder when it can't be read
                // (e.g. HEIC, which ImageIO can't decode) — mirrors PhotoPreviewPanel.
                label.setIcon(img != null ? new ImageIcon(img) : placeholderIcon());
            }
        }.execute();
        return label;
    }

    /** Camera-off icon shown in a row's thumbnail cell when the image can't be decoded. */
    private static Icon placeholderIcon() {
        return DDIconButtons.svgIcon(DDIconButtons.CAMERA_OFF, 48, "Label.disabledForeground");
    }

    // -------------------------------------------------------------------------
    // Selection / reorder
    // -------------------------------------------------------------------------

    private void selectRow(Row row) {
        selectedRow_ = row;
        applySelectionHighlight();
        updateReorderButtons();
    }

    /**
     * Marks the current row (the reorder target) with a subtle left-accent bar rather than a
     * full-background highlight, so the row's text stays readable.
     */
    private void applySelectionHighlight() {
        for (Row row : currentFolder_.rows) {
            if (row.comp == null) continue;
            row.comp.setBorder(rowBorder(row == selectedRow_));
            row.comp.repaint();
        }
    }

    private static Border rowBorder(boolean current) {
        Color accent = UIManager.getColor("Component.focusColor");
        if (accent == null) accent = UIManager.getColor("List.selectionBackground");
        Border bar = current
                ? BorderFactory.createMatteBorder(0, 3, 0, 0, accent)
                : BorderFactory.createEmptyBorder(0, 3, 0, 0);
        return BorderFactory.createCompoundBorder(bar, BorderFactory.createEmptyBorder(4, 3, 4, 6));
    }

    private void updateReorderButtons() {
        int idx = selectedIndex();
        int size = currentFolder_ == null ? 0 : currentFolder_.rows.size();
        upBtn_.setEnabled(idx > 0);
        downBtn_.setEnabled(idx >= 0 && idx < size - 1);
    }

    private int selectedIndex() {
        return (currentFolder_ == null || selectedRow_ == null) ? -1 : currentFolder_.rows.indexOf(selectedRow_);
    }

    private void moveSelected(int delta) {
        int idx = selectedIndex();
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= currentFolder_.rows.size()) return;
        List<Row> rows = currentFolder_.rows;
        rows.add(target, rows.remove(idx));
        rebuildRows();
        applySelectionHighlight();
        updateReorderButtons();
        onDirtyChanged();
    }

    // -------------------------------------------------------------------------
    // Dirty / save
    // -------------------------------------------------------------------------

    private void onDirtyChanged() {
        boolean dirty = windowDirty();
        saveBtn_.setEnabled(dirty);
        saveCloseBtn_.setEnabled(dirty);
    }

    private boolean windowDirty() {
        for (FolderEditor f : editors_.values()) {
            if (folderDirty(f)) return true;
        }
        return false;
    }

    private boolean folderDirty(FolderEditor f) {
        List<String> cur = f.rows.stream().map(r -> r.matchKey).toList();
        if (!cur.equals(f.originalOrderKeys)) return true;
        for (Row r : f.rows) {
            if (r.type == RowType.IMAGE && !nz(r.caption).trim().equals(nz(r.originalCaption).trim())) return true;
        }
        return false;
    }

    private void onSave() {
        saveAll();
    }

    private void onSaveClose() {
        if (saveAll()) context_.close();
    }

    private void onCancel() {
        if (windowDirty() && !EngineUtils.displayConfirmationDialog(context_,
                PropertyConfig.getMessage("msg.confirm.photogen.discard"))) {
            return;
        }
        context_.close();
    }

    /** Persists every changed folder.  Returns true when all saved without error. */
    private boolean saveAll() {
        List<String> errors = new ArrayList<>();
        for (FolderEditor f : editors_.values()) {
            if (!folderDirty(f)) continue;
            try {
                applyFolder(f);
            } catch (PhotogenFileException e) {
                logger.error("Failed to save {}{}", f.pf.getPath(), Utils.formatExceptionText(e));
                errors.add(e.getMessage());
            }
        }
        onDirtyChanged();
        if (!errors.isEmpty()) {
            EngineUtils.displayErrorDialog(context_, String.join("\n", errors), "msg.windowtitle.saveError", null);
            return false;
        }
        return true;
    }

    private void applyFolder(FolderEditor f) throws PhotogenFileException {
        PhotogenFile pf = f.pf;
        List<String> curKeys = f.rows.stream().map(r -> r.matchKey).toList();
        boolean reordered = !curKeys.equals(f.originalOrderKeys);

        if (reordered) {
            // Order matters: ensure every displayed row is an entry, preserving untouched lines.
            for (Row r : f.rows) {
                String cap = r.type == RowType.IMAGE ? nz(r.caption).trim() : "";
                boolean capChanged = r.type == RowType.IMAGE && !cap.equals(nz(r.originalCaption).trim());
                if (capChanged) {
                    pf.setCaption(r.writeKey, cap);
                } else if (!pf.hasEntry(r.writeKey)) {
                    pf.setCaption(r.writeKey, cap);
                }
            }
            pf.setEntryOrder(f.rows.stream().map(r -> r.writeKey).toList());
        } else {
            // Captions only: update changed, remove cleared, leave the rest byte-exact.
            for (Row r : f.rows) {
                if (r.type != RowType.IMAGE) continue;
                String cap = nz(r.caption).trim();
                if (cap.equals(nz(r.originalCaption).trim())) continue;
                if (cap.isEmpty()) {
                    if (pf.hasEntry(r.writeKey)) pf.removeEntry(r.writeKey);
                } else {
                    pf.setCaption(r.writeKey, cap);
                }
            }
        }

        pf.saveOrCreate();

        // Re-baseline so this folder is no longer dirty.
        for (Row r : f.rows) r.originalCaption = r.caption;
        f.originalOrderKeys = new ArrayList<>(curKeys);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private enum RowType { IMAGE, SUBFOLDER }

    private static final class Row {
        final RowType type;
        final String displayName;  // full filename or subfolder name
        final String matchKey;     // normalized, for matching photogen entries
        final String writeKey;     // key written to photogen.txt (basename w/o ext, or subfolder name)
        final Path path;
        String caption;
        String originalCaption;
        JComponent comp;

        private Row(RowType type, String displayName, String matchKey, String writeKey, Path path, String caption) {
            this.type = type;
            this.displayName = displayName;
            this.matchKey = matchKey;
            this.writeKey = writeKey;
            this.path = path;
            this.caption = caption == null ? "" : caption;
            this.originalCaption = this.caption;
        }

        static Row image(String fileName, Path path, String caption) {
            return new Row(RowType.IMAGE, fileName, PhotogenFile.normalizeKey(fileName), stripExtension(fileName), path, caption);
        }

        static Row subfolder(String name, Path path, String caption) {
            return new Row(RowType.SUBFOLDER, name, PhotogenFile.normalizeKey(name), name, path, caption);
        }

        private static String stripExtension(String fileName) {
            if (!PathValidation.isImageFile(fileName)) return fileName;
            int dot = fileName.lastIndexOf('.');
            return dot > 0 ? fileName.substring(0, dot) : fileName;
        }
    }

    private static final class FolderEditor {
        final Path dir;
        final PhotogenFile pf;
        List<Row> rows = new ArrayList<>();
        List<String> originalOrderKeys = new ArrayList<>();

        FolderEditor(Path dir, PhotogenFile pf) {
            this.dir = dir;
            this.pf = pf;
        }
    }

    /** Rows panel (a DDPanel so it carries the standard window background) that fills the
     *  viewport width and scrolls only vertically. */
    private static final class ScrollingRows extends DDPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 100; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
