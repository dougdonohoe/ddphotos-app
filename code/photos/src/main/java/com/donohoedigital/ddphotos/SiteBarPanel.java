package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.DataElement;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.SitesFile;
import com.donohoedigital.ddphotos.config.SitesFileException;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SiteBarPanel extends DDPanel
{
    private static final Logger logger = LogManager.getLogger(SiteBarPanel.class);
    private static final String STYLE = "SiteBar";

    private final AppContext context_;

    private final DDImageButton logoButton_;
    private final SitesFile sitesFile_;
    private final OptionCombo<Site> siteOption_;
    private final DDComboBox<Site> siteCombo_;
    private final DDButton editBtn_;
    private final DDButton deleteBtn_;

    private final List<Consumer<Site>> siteListeners_ = new ArrayList<>();
    private BooleanSupplier dirtyChecker_;
    private Site lastSelectedSite_;
    private boolean suppressSiteChange_ = false;

    public SiteBarPanel(AppContext context, SitesFile sitesFile, Site selectSite)
    {
        context_ = context;
        sitesFile_ = sitesFile;

        setBorder(BorderFactory.createEmptyBorder(4, 11, 0, 8));

        logoButton_ = new DDImageButton("icon48");
        siteOption_ = new OptionCombo<>(
                new DataElement<>("sitecombo", sitesFile_.getSites(), null),
                PhotosConstants.PREFS_NODE_APP, "sitecombo", STYLE,
                new TypedHashMap(), 675, true);
        siteCombo_ = siteOption_.getComboBox();
        siteCombo_.setRenderer(new SiteComboRenderer(siteCombo_));

        DDButton addBtn_ = DDIconButtons.iconButton("addsite",    STYLE, DDIconButtons.PLUS);
        editBtn_          = DDIconButtons.iconButton("editsite",   STYLE, DDIconButtons.EDIT);
        deleteBtn_        = DDIconButtons.iconButton("deletesite", STYLE, DDIconButtons.TRASH);

        addBtn_.addActionListener(   _ -> onAdd());
        editBtn_.addActionListener(  _ -> onEdit());
        deleteBtn_.addActionListener(_ -> onDelete());

        DDPanel widgets = new DDPanel();
        widgets.setLayout(new BoxLayout(widgets, BoxLayout.X_AXIS));
        widgets.setBorder(BorderFactory.createEmptyBorder(11, 20, 0, 0));
        widgets.add(siteOption_);
        widgets.add(Box.createHorizontalStrut(8));
        widgets.add(addBtn_);
        widgets.add(Box.createHorizontalStrut(4));
        widgets.add(editBtn_);
        widgets.add(Box.createHorizontalStrut(4));
        widgets.add(deleteBtn_);

        DDPanel bar = new DDPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.add(logoButton_);
        bar.add(GuiUtils.NORTH(widgets));

        add(bar, BorderLayout.WEST);

        // selectSite (e.g., a site just added via the wizard) overrides the prefs-saved selection
        refreshCombo(selectSite);
        lastSelectedSite_ = (Site) siteCombo_.getSelectedItem();
        siteCombo_.addActionListener(_ -> onSiteSelected());
    }

    // -------------------------------------------------------------------------
    // Sites management
    // -------------------------------------------------------------------------

    void refreshCombo(Site toSelect)
    {
        suppressSiteChange_ = true;
        List<Site> sites = sitesFile_.getSites();
        siteCombo_.resetValues();
        if (toSelect != null && sites.contains(toSelect)) {
            siteCombo_.setSelectedItem(toSelect);
        } else if (!sites.isEmpty()) {
            siteOption_.resetToPrefs();
        }
        suppressSiteChange_ = false;
        lastSelectedSite_ = (Site) siteCombo_.getSelectedItem();
        notifySiteListeners(lastSelectedSite_);
        updateButtonState();
    }

    private void updateButtonState()
    {
        boolean has = siteCombo_.getSelectedItem() != null;
        editBtn_.setEnabled(has);
        deleteBtn_.setEnabled(has);
    }

    // -------------------------------------------------------------------------
    // Button handlers
    // -------------------------------------------------------------------------

    private void onAdd()
    {
        refreshCombo(PhotosUtils.openAddSiteDialog(context_, sitesFile_));
    }

    private void onEdit()
    {
        Site selected = (Site) siteCombo_.getSelectedItem();
        if (selected == null) return;

        TypedHashMap params = new TypedHashMap();
        params.setObject(SiteDialog.PARAM_SITES_FILE,      sitesFile_);
        params.setObject(SiteDialog.PARAM_SITE,            selected);
        params.setObject("dialog-windowtitle-prop", "msg.windowtitle.EditSiteDialog");
        context_.processPhaseNow("SiteDialog", params);

        refreshCombo(selected);
    }

    private void onDelete()
    {
        Site selected = (Site) siteCombo_.getSelectedItem();
        if (selected == null) return;

        if (!EngineUtils.displayConfirmationDialog(context_, PropertyConfig.getMessage("msg.confirm.remove.site", selected.getDisplayName()))) {
            return;
        }

        sitesFile_.getSites().remove(selected);
        try {
            sitesFile_.save();
        } catch (SitesFileException e) {
            logger.error("Failed to save sites file: {}{}", sitesFile_.getPath(), Utils.formatExceptionText(e));
            EngineUtils.displayErrorDialog(context_, e.getMessage(), "msg.windowtitle.saveError", null);
        }
        refreshCombo(null);
    }

    // -------------------------------------------------------------------------
    // Site-change listener / dirty-check support
    // -------------------------------------------------------------------------

    public void addSiteListener(Consumer<Site> listener)
    {
        siteListeners_.add(listener);
    }

    public void setDirtyChecker(BooleanSupplier checker)
    {
        dirtyChecker_ = checker;
    }

    private void notifySiteListeners(Site site)
    {
        siteListeners_.forEach(l -> l.accept(site));
    }

    private void onSiteSelected()
    {
        if (suppressSiteChange_) return;
        Site selected = (Site) siteCombo_.getSelectedItem();
        if (Objects.equals(selected, lastSelectedSite_)) {
            updateButtonState();
            return;
        }
        if (dirtyChecker_ != null && dirtyChecker_.getAsBoolean()) {
            if (!EngineUtils.displayConfirmationDialog(context_,
                    PropertyConfig.getMessage("msg.confirm.unsaved.changes"))) {
                suppressSiteChange_ = true;
                siteCombo_.setSelectedItem(lastSelectedSite_);
                suppressSiteChange_ = false;
                return;
            }
        }
        lastSelectedSite_ = selected;
        notifySiteListeners(selected);
        updateButtonState();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Site getSelectedSite()
    {
        return (Site) siteCombo_.getSelectedItem();
    }

    public DDComponent getLogoComponent() {
        return logoButton_;
    }

    // -------------------------------------------------------------------------
    // Combo rendering
    // -------------------------------------------------------------------------

    /**
     * Renders Site entries with the (id) and dirPath segments in muted colors,
     * leaving the display name in the default color.
     */
    private static final class SiteComboRenderer extends DDComboBoxRenderer
    {
        SiteComboRenderer(DDComboBox<Site> combo)
        {
            super(combo);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
        {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Site site) {
                String name = escape(site.getDisplayName() != null ? site.getDisplayName() : "");
                String id = escape(site.getIdOrDefault());
                StringBuilder html = new StringBuilder("<html><b>")
                        .append(name)
                        .append("</b> <span style='color:#808080'>(").append(id).append(")</span>");
                String configPath = site.getActualConfigPath();
                if (configPath != null && !configPath.isBlank()) {
                    html.append(" <span style='color:#5B7C99'>").append(escape(configPath)).append("</span>");
                }
                html.append("</html>");
                setText(html.toString());
            }
            return this;
        }

        private static String escape(String s)
        {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
