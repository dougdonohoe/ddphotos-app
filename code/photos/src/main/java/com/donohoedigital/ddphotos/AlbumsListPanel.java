package com.donohoedigital.ddphotos;

import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.AlbumsSettings;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class AlbumsListPanel extends DDPanel {
    private static final Logger logger = LogManager.getLogger(AlbumsListPanel.class);
    private static final String STYLE = "Options";

    private final AppContext context_;
    private Site currentSite_;
    private final DefaultListModel<AlbumEntry> listModel_ = new DefaultListModel<>();
    private OptionList<AlbumEntry> list_;
    private DDButton upBtn_, downBtn_, addBtn_, deleteBtn_;

    private final List<Consumer<AlbumEntry>> selectionListeners_ = new ArrayList<>();
    private BooleanSupplier dirtyChecker_;
    private boolean suppressSelectionChange_ = false;
    private int lastLoadedIndex_ = -1;

    public AlbumsListPanel(AppContext context, SiteBarPanel siteBar) {
        context_ = context;
        buildUI();
        siteBar.addSiteListener(this::loadSite);
        loadSite(siteBar.getSelectedSite());
    }

    public AppContext getContext() {
        return context_;
    }

    // -------------------------------------------------------------------------
    // Build UI
    // -------------------------------------------------------------------------

    private void buildUI() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(4, 4, 4, 10));

        DDLabelBorder section = new DDLabelBorder("albumslist", STYLE);
        section.setLayout(new BorderLayout(0, 4));
        section.setBorder(BorderFactory.createCompoundBorder(
                section.getBorder(),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        list_ = new OptionList<>(listModel_, PhotosConstants.PREFS_NODE_APP, "albumlist", STYLE);
        list_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list_.setCellRenderer(new AlbumCellRenderer());
        list_.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onSelectionChanged();
        });

        JScrollPane scroll = new JScrollPane(list_);
        scroll.setBorder(null);

        addBtn_ = DDIconButtons.iconButton("addalbum", STYLE, DDIconButtons.PLUS);
        deleteBtn_ = DDIconButtons.iconButton("deletealbum", STYLE, DDIconButtons.TRASH);
        upBtn_ = DDIconButtons.iconButton("albumup", STYLE, DDIconButtons.CHEVRON_UP);
        downBtn_ = DDIconButtons.iconButton("albumdown", STYLE, DDIconButtons.CHEVRON_DOWN);

        upBtn_.addActionListener(_ -> moveUp());
        downBtn_.addActionListener(_ -> moveDown());
        addBtn_.addActionListener(_ -> addAlbum());
        deleteBtn_.addActionListener(_ -> deleteAlbum());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        buttonRow.add(upBtn_);
        buttonRow.add(downBtn_);
        buttonRow.add(Box.createHorizontalStrut(8));
        buttonRow.add(addBtn_);
        buttonRow.add(deleteBtn_);

        section.add(scroll, BorderLayout.CENTER);
        section.add(buttonRow, BorderLayout.SOUTH);
        add(section, BorderLayout.CENTER);

        updateButtons();
    }

    // -------------------------------------------------------------------------
    // Site loading
    // -------------------------------------------------------------------------

    private void loadSite(Site site) {
        currentSite_ = site;

        suppressSelectionChange_ = true;
        listModel_.clear();
        lastLoadedIndex_ = -1;

        if (site == null) {
            return;
        }

        AlbumsFile af = site.getAlbumsFile();
        if (af != null) {
            for (AlbumEntry a : af.getAlbums()) {
                listModel_.addElement(a);
            }
            if (!listModel_.isEmpty()) {
                AlbumsSettings settings = af.getSettings();
                list_.setExtraKey(settings != null ? settings.getId() : null);
                list_.restoreFromPrefs();
                lastLoadedIndex_ = list_.getSelectedIndex();
            }
        }
        suppressSelectionChange_ = false;

        notifySelectionListeners(list_.getSelectedValue());
        updateButtons();
    }

    // -------------------------------------------------------------------------
    // Album actions
    // -------------------------------------------------------------------------

    private void addAlbum() {
        if (currentSite_ == null) return;
        AlbumsFile af = currentSite_.getOrCreateAlbumsFile();
        int sizeBefore = af.getAlbums().size();

        TypedHashMap params = new TypedHashMap();
        params.setObject(AlbumDialog.PARAM_SITE, currentSite_);
        context_.processPhaseNow("AlbumDialog", params);

        List<AlbumEntry> albums = af.getAlbums();
        if (albums.size() > sizeBefore) {
            AlbumEntry newEntry = albums.removeLast();

            int selectedIdx = list_.getSelectedIndex();
            int insertIdx = selectedIdx >= 0 ? selectedIdx + 1 : sizeBefore;

            albums.add(insertIdx, newEntry);

            suppressSelectionChange_ = true;
            listModel_.add(insertIdx, newEntry);
            list_.setSelectedIndex(insertIdx);
            list_.ensureIndexIsVisible(insertIdx);
            lastLoadedIndex_ = insertIdx;
            suppressSelectionChange_ = false;

            if (insertIdx < sizeBefore) {
                saveAlbumsFile();  // re-save to persist corrected insertion order
            }

            notifySelectionListeners(newEntry);
        }
        updateButtons();
    }

    private void deleteAlbum() {
        AlbumEntry selected = list_.getSelectedValue();
        if (selected == null) return;

        if (!EngineUtils.displayConfirmationDialog(context_, PropertyConfig.getMessage("msg.confirm.remove.album", selected.getName()))) {
            return;
        }

        int idx = list_.getSelectedIndex();
        AlbumsFile af = currentSite_.getAlbumsFile();
        if (af == null) return;
        af.getAlbums().remove(selected);

        suppressSelectionChange_ = true;
        listModel_.removeElement(selected);
        int newIdx = Math.min(idx, listModel_.size() - 1);
        if (newIdx >= 0) list_.setSelectedIndex(newIdx);
        lastLoadedIndex_ = newIdx;
        suppressSelectionChange_ = false;

        saveAlbumsFile();
        notifySelectionListeners(list_.getSelectedValue());
        updateButtons();
    }

    private void moveUp() {
        int idx = list_.getSelectedIndex();
        if (idx <= 0) return;

        AlbumsFile af = currentSite_.getAlbumsFile();
        if (af == null) return;
        List<AlbumEntry> albums = af.getAlbums();
        AlbumEntry a = albums.remove(idx);
        albums.add(idx - 1, a);

        suppressSelectionChange_ = true;
        AlbumEntry e = listModel_.remove(idx);
        listModel_.add(idx - 1, e);
        list_.setSelectedIndex(idx - 1);
        lastLoadedIndex_ = idx - 1;
        suppressSelectionChange_ = false;

        saveAlbumsFile();
        updateButtons();
    }

    private void moveDown() {
        int idx = list_.getSelectedIndex();
        if (idx < 0 || idx >= listModel_.size() - 1) return;

        AlbumsFile af = currentSite_.getAlbumsFile();
        if (af == null) return;
        List<AlbumEntry> albums = af.getAlbums();
        AlbumEntry a = albums.remove(idx);
        albums.add(idx + 1, a);

        suppressSelectionChange_ = true;
        AlbumEntry e = listModel_.remove(idx);
        listModel_.add(idx + 1, e);
        list_.setSelectedIndex(idx + 1);
        lastLoadedIndex_ = idx + 1;
        suppressSelectionChange_ = false;

        saveAlbumsFile();
        updateButtons();
    }

    private void saveAlbumsFile() {
        if (currentSite_ == null) return;
        try {
            currentSite_.saveAlbumsFile();
        } catch (AlbumsFileException e) {
            logger.error("Failed to save albums file: {}{}", currentSite_.getAlbumsFilePath(), Utils.formatExceptionText(e));
            EngineUtils.displayErrorDialog(context_, e.getMessage(), "msg.windowtitle.saveError", null);
        }
    }

    // -------------------------------------------------------------------------
    // Selection
    // -------------------------------------------------------------------------

    private void onSelectionChanged() {
        if (suppressSelectionChange_) return;
        int newIdx = list_.getSelectedIndex();

        if (dirtyChecker_ != null && dirtyChecker_.getAsBoolean()) {
            if (!EngineUtils.displayConfirmationDialog(context_,
                    PropertyConfig.getMessage("msg.confirm.unsaved.album.changes"))) {
                suppressSelectionChange_ = true;
                if (lastLoadedIndex_ >= 0 && lastLoadedIndex_ < listModel_.size()) {
                    list_.setSelectedIndex(lastLoadedIndex_);
                } else {
                    list_.clearSelection();
                }
                suppressSelectionChange_ = false;
                return;
            }
        }

        lastLoadedIndex_ = newIdx;
        notifySelectionListeners(list_.getSelectedValue());
        updateButtons();
    }

    private void notifySelectionListeners(AlbumEntry entry) {
        selectionListeners_.forEach(l -> l.accept(entry));
    }

    // -------------------------------------------------------------------------
    // Button state
    // -------------------------------------------------------------------------

    private void updateButtons() {
        boolean hasSite = currentSite_ != null;
        int idx = list_.getSelectedIndex();
        boolean hasSelection = idx >= 0;
        boolean notDirty = dirtyChecker_ == null || !dirtyChecker_.getAsBoolean();

        addBtn_.setEnabled(hasSite && notDirty);
        deleteBtn_.setEnabled(hasSelection && notDirty);
        upBtn_.setEnabled(hasSelection && idx > 0 && notDirty);
        downBtn_.setEnabled(hasSelection && idx < listModel_.size() - 1 && notDirty);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void addSelectionListener(Consumer<AlbumEntry> listener) {
        selectionListeners_.add(listener);
    }

    public void setDirtyChecker(BooleanSupplier checker) {
        dirtyChecker_ = checker;
    }

    public AlbumEntry getSelectedAlbum() {
        return list_.getSelectedValue();
    }

    public AlbumsFile getCurrentAlbumsFile() {
        return currentSite_ != null ? currentSite_.getAlbumsFile() : null;
    }

    public Site getCurrentSite() {
        return currentSite_;
    }

    public void refreshAfterAlbumSaved() {
        list_.repaint();
        updateButtons();
    }

    public void reloadAlbumsFile() {
        if (currentSite_ == null) return;
        currentSite_.reloadAlbumsFile();
        AlbumsFile freshFile = currentSite_.getAlbumsFile();
        if (freshFile == null) return;

        // Update listModel_ entries with fresh objects so stale base references are fixed
        Map<String, AlbumEntry> freshBySlug = new HashMap<>();
        for (AlbumEntry a : freshFile.getAlbums()) {
            if (a.getSlug() != null) freshBySlug.put(a.getSlug(), a);
        }
        for (int i = 0; i < listModel_.size(); i++) {
            AlbumEntry old = listModel_.get(i);
            AlbumEntry fresh = old.getSlug() != null ? freshBySlug.get(old.getSlug()) : null;
            if (fresh != null) listModel_.set(i, fresh);
        }
    }

    // -------------------------------------------------------------------------
    // Cell renderer
    // -------------------------------------------------------------------------

    private static class AlbumCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (value instanceof AlbumEntry entry) {
                String name = entry.getName();
                label.setText(name != null && !name.isBlank() ? name : entry.getSlug());
            }
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return label;
        }
    }
}
