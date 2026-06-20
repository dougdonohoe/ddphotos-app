package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.app.engine.AppEngine;
import com.donohoedigital.gui.DDPanel;
import com.donohoedigital.gui.OptionSplitPane;
import com.donohoedigital.gui.DDTabPanel;
import com.donohoedigital.gui.DDTabbedPane;

import javax.swing.*;
import java.awt.*;

public class ConfigTab extends DDTabPanel implements AppEngine.CloseListener
{
    private static final String STYLE = "Options";

    private final AppContext context_;
    private final SiteBarPanel   siteBar_;
    private SiteDetailsPanel     siteDetailsPanel_;
    private AlbumDetailPanel     albumDetailPanel_;

    public ConfigTab(AppContext context, SiteBarPanel siteBar)
    {
        super(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        context_ = context;
        siteBar_ = siteBar;
    }

    @Override
    protected void createUI()
    {
        setLayout(new BorderLayout());

        siteDetailsPanel_ = new SiteDetailsPanel(context_, siteBar_);
        AlbumsListPanel albumsListPanel = new AlbumsListPanel(context_, siteBar_);
        albumDetailPanel_ = new AlbumDetailPanel(albumsListPanel);

        siteBar_.setDirtyChecker(this::isDirty);
        albumsListPanel.setDirtyChecker(albumDetailPanel_::isDirty);
        albumDetailPanel_.setOnSavedCallback(albumsListPanel::refreshAfterAlbumSaved);
        siteDetailsPanel_.addBasesChangedListener(albumDetailPanel_::onBasesChanged);

        siteDetailsPanel_.setOnEditModeChanged(this::updateTabLocking);
        albumDetailPanel_.setOnEditModeChanged(this::updateTabLocking);

        DDPanel detailArea = new DDPanel();
        detailArea.setLayout(new BorderLayout());
        detailArea.add(albumDetailPanel_, BorderLayout.CENTER);

        DDPanel albumsArea = new DDPanel();
        albumsArea.setLayout(new BorderLayout());
        albumsArea.add(albumsListPanel, BorderLayout.WEST);
        albumsArea.add(detailArea,       BorderLayout.CENTER);

        DDPanel rightArea = new DDPanel();
        rightArea.setLayout(new BorderLayout());
        rightArea.add(siteDetailsPanel_.getBasesPanel(), BorderLayout.NORTH);
        rightArea.add(albumsArea,                        BorderLayout.CENTER);

        siteDetailsPanel_.setMinimumSize(new Dimension(525, 0));
        rightArea.setMinimumSize(new Dimension(747, 0));

        OptionSplitPane split = new OptionSplitPane("configsplit", STYLE,
                JSplitPane.HORIZONTAL_SPLIT,
                siteDetailsPanel_, rightArea, true,
                PhotosConstants.PREFS_NODE_APP);
        split.setResizeWeight(0.0);
        add(split, BorderLayout.CENTER);

        AppEngine.getAppEngine().addCloseListener(this);
    }

    /**
     * Lock the other tabs while a site or album edit is in progress, so the
     * user can't navigate away (and e.g. trigger commands against half-edited
     * config) without first saving or canceling.
     */
    private void updateTabLocking()
    {
        boolean editing = siteDetailsPanel_.isEditing() || albumDetailPanel_.isEditing();
        DDTabbedPane pane = getTabPane();
        int myTab = getTabNum();
        for (int i = 0; i < pane.getTabCount(); i++) {
            if (i != myTab) pane.setEnabledAt(i, !editing);
        }
    }

    @Override
    public void removeNotify()
    {
        super.removeNotify();
        AppEngine.getAppEngine().removeCloseListener(this);
    }

    public boolean isDirty()
    {
        return (siteDetailsPanel_ != null && siteDetailsPanel_.isDirty())
            || (albumDetailPanel_ != null && albumDetailPanel_.isDirty());
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // AppEngine.CloseListener
    // ──────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean okayToClose()
    {
        if (!isDirty()) return true;

        return EngineUtils.displayConfirmationDialog(context_,
                PropertyConfig.getMessage("msg.confirm.unsaved.close"));
    }
}
