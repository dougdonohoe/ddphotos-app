package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.app.engine.AppEngine;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.gui.DDPanel;
import com.donohoedigital.gui.OptionSplitPane;
import com.donohoedigital.gui.DDTabPanel;
import com.donohoedigital.gui.DDTabbedPane;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

public class ConfigTab extends DDTabPanel implements AppEngine.CloseListener
{
    private static final String STYLE = "Options";

    private final AppContext context_;
    private final SiteBarPanel   siteBar_;
    private SiteDetailsPanel     siteDetailsPanel_;
    private AlbumDetailPanel     albumDetailPanel_;

    /** Watches the selected site's albums.yaml; closed in {@link #removeNotify}. */
    private ConfigWatcher.Registration albumsWatch_;

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

        // Follows the selection rather than a fixed site, so switching sites re-points the watch
        // (and re-baselines it) with nothing extra to wire up.
        albumsWatch_ = ConfigWatcher.watch(
                () -> {
                    Site site = siteBar_.getSelectedSite();
                    return site == null ? null : site.getAlbumsFile();
                },
                this::onAlbumsFileChangedOnDisk);
    }

    /**
     * The selected site's {@code albums.yaml} was rewritten by something else.  With nothing
     * pending it just reloads; with unsaved edits on screen the user chooses, and either way the
     * log records which.  Keeping the edits leaves the file changed on disk, which is what
     * {@link EditableDetailPanel#okayToOverwrite} picks up at Save time.
     *
     * <p>Note {@link #isDirty()} means "editing <em>and</em> something was actually typed": having
     * pressed Edit and touched nothing has nothing to lose, so it reloads silently, and the reload
     * takes the panel back out of edit mode on its way through.
     */
    private void onAlbumsFileChangedOnDisk()
    {
        Site site = siteBar_.getSelectedSite();
        if (site == null) return;
        Path path = site.getAlbumsFilePath();

        // Asked before reloading: the reload takes the panels out of edit mode, so asking
        // afterward would always say clean.
        if (isDirty() && !ExternalChange.confirmDiscard(context_, path)) {
            return;   // their edits stand; the watch will not re-ask about this same version
        }

        // A failure here is a half-written or hand-broken file: keep what is in memory and say
        // nothing.  The watch reports each state once, so the next real write gets another try.
        if (siteBar_.refreshSelectedSite()) {
            ExternalChange.logReloaded(path);
        }
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
        if (albumsWatch_ != null) {
            albumsWatch_.close();
            albumsWatch_ = null;
        }
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
