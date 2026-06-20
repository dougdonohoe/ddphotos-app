package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.HeroEntry;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

public class BasesPanel extends DDPanel {

    private static final Logger logger = LogManager.getLogger(BasesPanel.class);
    private static final String STYLE  = "Options";

    private final AppContext context_;

    private Site currentSite_;

    private final DefaultListModel<String> listModel_ = new DefaultListModel<>();
    private DDList<String> list_;
    private DDButton addBtn_, editBtn_, deleteBtn_;

    private final List<Runnable> basesChangedListeners_ = new ArrayList<>();

    public BasesPanel(AppContext context) {
        context_ = context;
        buildUI();
    }

    // -------------------------------------------------------------------------
    // Build UI
    // -------------------------------------------------------------------------

    private void buildUI() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 4, 4, 10));

        DDLabelBorder section = new DDLabelBorder("bases", STYLE);
        section.setLayout(new BorderLayout(0, 4));
        section.setBorder(BorderFactory.createCompoundBorder(
                section.getBorder(),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        list_ = new DDList<>(listModel_, "baseslist", STYLE);
        list_.setVisibleRowCount(2);
        list_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list_.setCellRenderer(new BaseCellRenderer());
        list_.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updateButtons(); });
        list_.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && list_.getSelectedIndex() >= 0) editBase();
            }
        });

        JScrollPane scroll = new JScrollPane(list_);
        scroll.setBorder(null);

        addBtn_    = DDIconButtons.iconButton("addbase",    STYLE, DDIconButtons.PLUS);
        editBtn_   = DDIconButtons.iconButton("editbase",   STYLE, DDIconButtons.EDIT);
        deleteBtn_ = DDIconButtons.iconButton("deletebase", STYLE, DDIconButtons.TRASH);

        addBtn_.addActionListener(   _ -> addBase());
        editBtn_.addActionListener(  _ -> editBase());
        deleteBtn_.addActionListener(_ -> deleteBase());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        buttonRow.add(addBtn_);
        buttonRow.add(editBtn_);
        buttonRow.add(deleteBtn_);

        section.add(scroll, BorderLayout.CENTER);
        section.add(buttonRow, BorderLayout.SOUTH);
        add(section, BorderLayout.CENTER);

        updateButtons();
    }

    // -------------------------------------------------------------------------
    // Site loading
    // -------------------------------------------------------------------------

    public void loadSite(Site site) {
        currentSite_ = site;
        rebuildList();
        updateButtons();
    }

    private AlbumsFile albumsFile() {
        return currentSite_ != null ? currentSite_.getAlbumsFile() : null;
    }

    private void rebuildList() {
        String selected = list_.getSelectedValue();
        listModel_.clear();
        AlbumsFile af = albumsFile();
        if (af != null) {
            for (String name : af.getBases().keySet()) {
                listModel_.addElement(name);
            }
        }
        if (selected != null) {
            int idx = listModel_.indexOf(selected);
            if (idx >= 0) list_.setSelectedIndex(idx);
        }
    }

    // -------------------------------------------------------------------------
    // Base actions
    // -------------------------------------------------------------------------

    private void addBase() {
        if (currentSite_ == null) return;
        AlbumsFile af = currentSite_.getOrCreateAlbumsFile();

        Map<String, String> before = new LinkedHashMap<>(af.getBases());

        TypedHashMap params = new TypedHashMap();
        params.setObject(BaseDialog.PARAM_SITE,        currentSite_);
        params.setObject("dialog-windowtitle-prop",          "msg.windowtitle.AddBaseDialog");
        context_.processPhaseNow("BaseDialog", params);

        if (!af.getBases().equals(before)) {
            Set<String> added = new LinkedHashSet<>(af.getBases().keySet());
            before.keySet().forEach(added::remove);
            rebuildList();
            if (!added.isEmpty()) {
                int idx = listModel_.indexOf(added.iterator().next());
                if (idx >= 0) list_.setSelectedIndex(idx);
            }
            basesChangedListeners_.forEach(Runnable::run);
        }
        updateButtons();
    }

    private void editBase() {
        String selected = list_.getSelectedValue();
        AlbumsFile af = albumsFile();
        if (selected == null || af == null) return;

        Map<String, String> before = new LinkedHashMap<>(af.getBases());

        TypedHashMap params = new TypedHashMap();
        params.setObject(BaseDialog.PARAM_SITE,        currentSite_);
        params.setObject(BaseDialog.PARAM_BASE_NAME,        selected);
        params.setObject("dialog-windowtitle-prop",          "msg.windowtitle.EditBaseDialog");
        context_.processPhaseNow("BaseDialog", params);

        if (!af.getBases().equals(before)) {
            String currentName = selected;
            if (!af.getBases().containsKey(selected)) {
                Set<String> added = new LinkedHashSet<>(af.getBases().keySet());
                before.keySet().forEach(added::remove);
                if (!added.isEmpty()) currentName = added.iterator().next();
            }
            rebuildList();
            int idx = listModel_.indexOf(currentName);
            if (idx >= 0) list_.setSelectedIndex(idx);
            basesChangedListeners_.forEach(Runnable::run);
        }
        updateButtons();
    }

    private void deleteBase() {
        String selected = list_.getSelectedValue();
        AlbumsFile af = albumsFile();
        if (selected == null || af == null) return;

        List<String> users = new ArrayList<>();
        for (AlbumEntry a : af.getAlbums()) {
            if (selected.equals(a.getBase())) {
                String label = a.getName() != null && !a.getName().isBlank() ? a.getName() : a.getSlug();
                users.add(label);
            }
        }
        HeroEntry hero = af.getSettings().getHero();
        if (hero != null && selected.equals(hero.getBase())) {
            users.add("hero image");
        }

        if (!users.isEmpty()) {
            StringBuilder items = new StringBuilder("<ul>");
            users.forEach(u -> items.append("<li>").append(u).append("</li>"));
            items.append("</ul>");
            EngineUtils.displayWarningDialog(context_,
                    PropertyConfig.getMessage("msg.base.in.use", selected, items));
            return;
        }

        if (!EngineUtils.displayConfirmationDialog(context_,
                PropertyConfig.getMessage("msg.confirm.remove.base", selected))) {
            return;
        }

        int idx = list_.getSelectedIndex();
        af.getBases().remove(selected);

        try {
            currentSite_.saveAlbumsFile();
            rebuildList();
            int newIdx = Math.min(idx, listModel_.size() - 1);
            if (newIdx >= 0) list_.setSelectedIndex(newIdx);
            basesChangedListeners_.forEach(Runnable::run);
        } catch (AlbumsFileException e) {
            logger.error("Failed to save albums file: {}{}", currentSite_.getAlbumsFilePath(),
                    Utils.formatExceptionText(e));
            EngineUtils.displayErrorDialog(context_, e.getMessage(), "msg.windowtitle.saveError", null);
        }
        updateButtons();
    }

    // -------------------------------------------------------------------------
    // Button state
    // -------------------------------------------------------------------------

    private void updateButtons() {
        boolean hasSite     = currentSite_ != null;
        boolean hasSelection = list_.getSelectedIndex() >= 0;
        addBtn_.setEnabled(hasSite);
        editBtn_.setEnabled(hasSelection);
        deleteBtn_.setEnabled(hasSelection);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void addBasesChangedListener(Runnable listener) {
        basesChangedListeners_.add(listener);
    }

    // -------------------------------------------------------------------------
    // Cell renderer
    // -------------------------------------------------------------------------

    private class BaseCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            AlbumsFile af = albumsFile();
            if (value instanceof String name && af != null) {
                String path = af.getBases().get(name);
                label.setText(name + ": " + (path != null ? path : ""));
            }
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return label;
        }
    }
}
