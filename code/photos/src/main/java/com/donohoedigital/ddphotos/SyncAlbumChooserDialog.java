package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.sync.SyncAlbumInfo;
import com.donohoedigital.ddphotos.sync.SyncException;
import com.donohoedigital.ddphotos.sync.SyncProvider;
import com.donohoedigital.gui.DDButton;
import com.donohoedigital.gui.DDHtmlArea;
import com.donohoedigital.gui.DDList;
import com.donohoedigital.gui.GuiUtils;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lists the albums a sync provider offers and lets the user pick one.  Albums already synced by
 * another album of the site are left out, since photogen rejects two albums syncing the same
 * upstream album.
 *
 * <p>The listing is fetched off the event thread; until it arrives the dialog shows a loading
 * message, and a failure is shown in place with a way to fix the credentials.  Use
 * {@link #choose}, which asks for credentials first when the site has none.
 */
public class SyncAlbumChooserDialog extends PhotosDialog
{
    public static final String PHASE_NAME = "SyncAlbumChooser";

    private static final String PARAM_SITE     = "sync-chooser-site";
    private static final String PARAM_PROVIDER = "sync-chooser-provider";
    private static final String PARAM_EXCLUDE  = "sync-chooser-exclude";
    private static final String PARAM_SELECT   = "sync-chooser-select";

    private static final int PREFERRED_WIDTH  = 560;
    private static final int PREFERRED_HEIGHT = 360;

    private Site site_;
    private SyncProvider provider_;
    private Set<String> exclude_;
    private String selectId_;

    private final DefaultListModel<SyncAlbumInfo> model_ = new DefaultListModel<>();
    private DDList<SyncAlbumInfo> list_;
    private DDHtmlArea status_;
    private DDButton credentialsBtn_;

    /**
     * Asks for credentials when the site has none, then shows the chooser.
     *
     * @param excludeIds album ids to leave out, compared ignoring case
     * @param selectId   the album id the caller already has, selected when it is listed; null or
     *                   blank for a new album, which selects the first album instead
     * @return the chosen album, or null when the user canceled either dialog
     */
    public static SyncAlbumInfo choose(AppContext context, Site site, SyncProvider provider,
                                       Set<String> excludeIds, String selectId)
    {
        AlbumsFile af = site.getOrCreateAlbumsFile();
        if (!provider.hasCredentials(af) && !SyncUi.editCredentials(context, site, provider)) return null;

        TypedHashMap params = new TypedHashMap();
        params.setObject(PARAM_SITE, site);
        params.setObject(PARAM_PROVIDER, provider);
        params.setObject(PARAM_EXCLUDE, excludeIds);
        params.setObject(PARAM_SELECT, selectId);
        params.setObject("dialog-windowtitle-prop", "msg.windowtitle.SyncAlbumChooser");
        Object result = context.processPhaseNow(PHASE_NAME, params).getResult();
        return result instanceof SyncAlbumInfo info ? info : null;
    }

    // -------------------------------------------------------------------------
    // DialogPhase API
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public JComponent createDialogContents()
    {
        site_     = (Site) phase_.getObject(PARAM_SITE);
        provider_ = (SyncProvider) phase_.getObject(PARAM_PROVIDER);
        exclude_  = (Set<String>) phase_.getObject(PARAM_EXCLUDE);
        selectId_ = (String) phase_.getObject(PARAM_SELECT);

        list_ = new DDList<>(model_, "syncalbumlist", STYLE);
        list_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list_.setCellRenderer(new AlbumRenderer());
        list_.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) checkButtons();
        });
        list_.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && okayButton_ != null && okayButton_.isEnabled()) {
                    okayButton_.doClick();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list_);
        scroll.setPreferredSize(new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT));

        status_ = new DDHtmlArea("syncalbumstatus", STYLE);
        status_.setDisplayOnly(true);
        status_.setOpaque(false);
        status_.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        credentialsBtn_ = new DDButton("synccredentials", STYLE);
        credentialsBtn_.addActionListener(_ -> {
            if (SyncUi.editCredentials(context_, site_, provider_)) load();
        });
        credentialsBtn_.setVisible(false);

        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setOpaque(false);
        south.add(status_, BorderLayout.CENTER);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnRow.setOpaque(false);
        btnRow.add(credentialsBtn_);
        south.add(btnRow, BorderLayout.EAST);

        JPanel form = new JPanel(new BorderLayout(0, 4));
        form.setOpaque(false);
        // The list and the status line below it both sit inside the dialog's usual 10px margin.
        form.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        form.add(scroll, BorderLayout.CENTER);
        form.add(south, BorderLayout.SOUTH);

        return wrapWithInstructions("syncalbuminstruct",
                PropertyConfig.getMessage("msg.syncchooser.instructions", provider_.displayName()),
                form, PREFERRED_WIDTH);
    }

    @Override
    protected void opened()
    {
        super.opened();
        load();
    }

    @Override
    protected Component getFocusComponent()
    {
        return list_;
    }

    @Override
    public boolean processButton(AppButton button)
    {
        if ("choose".equals(button.getName())) setResult(list_.getSelectedValue());
        removeDialog();
        return true;
    }

    @Override
    protected void checkButtons()
    {
        if (okayButton_ != null) okayButton_.setEnabled(list_.getSelectedValue() != null);
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    private void load()
    {
        model_.clear();
        credentialsBtn_.setVisible(false);
        setStatus(PropertyConfig.getMessage("msg.syncchooser.loading", provider_.displayName()));
        checkButtons();

        AlbumsFile af = site_.getOrCreateAlbumsFile();
        Thread.ofVirtual().name("sync-album-list").start(() -> {
            List<SyncAlbumInfo> albums = null;
            String error = null;
            try {
                albums = provider_.client(af).listAlbums();
            } catch (SyncException e) {
                error = e.getMessage();
            }
            List<SyncAlbumInfo> finalAlbums = albums;
            String finalError = error;
            SwingUtilities.invokeLater(() -> loaded(finalAlbums, finalError));
        });
    }

    private void loaded(List<SyncAlbumInfo> albums, String error)
    {
        if (error != null) {
            setStatus(PropertyConfig.getMessage("msg.syncchooser.failed", PhotosUtils.escapeHtml(error)));
            credentialsBtn_.setVisible(true);
            return;
        }
        List<SyncAlbumInfo> offered = albums.stream()
                .filter(a -> !exclude_.contains(a.id().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(a -> a.name() == null ? "" : a.name(),
                                             String.CASE_INSENSITIVE_ORDER))
                .toList();
        offered.forEach(model_::addElement);
        int hidden = albums.size() - offered.size();
        if (offered.isEmpty()) {
            setStatus(PropertyConfig.getMessage(albums.isEmpty() ? "msg.syncchooser.none" : "msg.syncchooser.allused"));
        } else {
            setStatus(hidden == 0 ? null : PropertyConfig.getMessage("msg.syncchooser.hidden", hidden));
            int select = 0;
            for (int i = 0; i < offered.size(); i++) {
                if (offered.get(i).id().equalsIgnoreCase(selectId_ == null ? "" : selectId_.strip())) select = i;
            }
            list_.setSelectedIndex(select);
            list_.ensureIndexIsVisible(select);
        }
        checkButtons();
    }

    private void setStatus(String html)
    {
        status_.setText(html == null ? "" : html);
        status_.setVisible(html != null);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /** Two lines: the name in bold with the photo count and owner, then the description. */
    private static final class AlbumRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus)
        {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SyncAlbumInfo(
                    String id, String name, String description, int assetCount, String owner
            )) {
                StringBuilder sb = new StringBuilder("<html><b>")
                        .append(PhotosUtils.escapeHtml(name == null || name.isBlank() ? id : name))
                        .append("</b>");
                if (assetCount >= 0) {
                    sb.append(" &nbsp;").append(PropertyConfig.getMessage("msg.syncchooser.count", assetCount));
                }
                if (owner != null) {
                    sb.append(" &nbsp;").append(PropertyConfig.getMessage("msg.syncchooser.owner",
                                                                          PhotosUtils.escapeHtml(owner)));
                }
                if (description != null && !description.isBlank()) {
                    String desc = GuiUtils.elideRight(description.strip().replaceAll("\\s+", " "), label, PREFERRED_WIDTH - 40);
                    sb.append("<br>").append(PhotosUtils.escapeHtml(desc));
                }
                label.setText(sb.append("</html>").toString());
            }
            label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return label;
        }
    }
}
