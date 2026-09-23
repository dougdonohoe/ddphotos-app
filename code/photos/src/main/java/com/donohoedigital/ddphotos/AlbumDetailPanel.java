package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.DataElement;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.PasswordsFile;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.SyncEntry;
import com.donohoedigital.ddphotos.config.SyncMetadataFile;
import com.donohoedigital.ddphotos.config.SyncText;
import com.donohoedigital.ddphotos.sync.SyncAlbumInfo;
import com.donohoedigital.ddphotos.sync.SyncProvider;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.prefs.Preferences;

/**
 * Edits one album.  An album is either <b>Local</b> (photos from {@code source}, optionally under
 * a {@code base}) or <b>Sync</b> (photogen downloads it from a provider such as Immich into a
 * folder it owns; see {@link AlbumsFile#resolveSyncPath}).
 *
 * <p>For a synced album the name and description may be left out of {@code albums.yaml}, and
 * photogen then uses the upstream values recorded in the sync folder's {@code metadata.yaml}.
 * Each field has an <b>Override</b> checkbox: unchecked, the field shows the upstream value and
 * nothing is written; checked, the typed value is written, unless it equals the upstream value,
 * so an override that changes nothing never pins a stale copy into the file.
 *
 * <p>Until photogen first syncs the album there are no photos, so the cover chooser is replaced
 * by a note saying so.  The panel watches the album's {@code metadata.yaml} and refreshes when a
 * photogen run writes it.
 */
public class AlbumDetailPanel extends EditableDetailPanel {
    private static final Logger logger = LogManager.getLogger(AlbumDetailPanel.class);
    private static final String PREF_BROWSE_LAST_DIR = "browsesource.lastdir";
    public static final int PREFERRED_TEXT_WIDTH = 350;
    public static final int PREFERRED_SHORT_TEXT_WIDTH = 350;
    /** Slugs are short, and the row has to leave room for the password controls. */
    private static final int PREFERRED_SLUG_TEXT_WIDTH = 231;
    /** Album ids are UUIDs, and the row has to leave room for Choose... */
    private static final int PREFERRED_ALBUM_ID_WIDTH = 270;
    /** What the album id field is sized to show in full. */
    private static final String SAMPLE_ALBUM_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

    private final AlbumsListPanel albumsList_;
    private final Preferences prefs_ = PhotosConstants.getAppPreferences();
    private final TypedHashMap dummy_ = new TypedHashMap();

    private AlbumEntry currentEntry_;
    private AlbumEntry originalEntry_;
    private boolean populating_;

    // Base combo — backed by mutable lists so resetValues() picks up site changes
    private final List<String> baseKeys_ = new ArrayList<>();
    private final List<String> baseDisplays_ = new ArrayList<>();
    private final DataElement<String> baseElement_;
    private DDComboBox<String> baseCombo_;

    // Fields
    private OptionText slug_;
    private DDLabel sourceTypeLabel_;
    private SourceTypeRow sourceType_;
    private OptionText name_;
    private DDCheckBox nameOverride_;
    private DDLabel nameHint_;
    private OptionTextArea description_;
    private DDCheckBox descOverride_;
    private DDLabel descHint_;
    private DDLabelBorder sourceSection_;
    private JPanel localRows_;
    private JPanel syncRows_;
    private OptionFileChooser source_;
    private OptionText albumId_;
    private DDButton chooseBtn_;
    private OptionBoolean captions_;
    private OptionFileChooser cover_;
    private DDHtmlArea notSyncedArea_;
    private DDHtmlArea warningArea_;
    private PhotoPreviewPanel coverPreview_;
    private OptionBoolean recurse_;
    private OptionBoolean manualSort_;
    /** Left inset that lines the checkbox row up with the fields above it. */
    private int checkboxIndent_;
    private DDButton editCaptionsBtn_;

    // Sync state.  metaName_/metaDesc_ are the upstream values for the album id on screen: from
    // metadata.yaml when it records that album, else from a pending Choose..., else null.
    private SyncMetadataFile meta_;
    private SyncAlbumInfo pending_;
    private String metaName_;
    private String metaDesc_;   // escaped, as photogen stores it
    private boolean synced_;
    /** The source type before the latest change, to tell Local-to-Sync from a provider change. */
    private boolean wasSync_;
    /** Watches the album's metadata.yaml while the panel is showing; see {@link #addNotify}. */
    private ConfigWatcher.Registration metaWatch_;

    private Runnable onSavedCallback_;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public AlbumDetailPanel(AlbumsListPanel albumsList) {
        super("editdetails.album");
        albumsList_ = albumsList;
        baseElement_ = createBaseElement("albumbase", baseKeys_, baseDisplays_);
        buildUI();
        albumsList_.addSelectionListener(this::loadAlbum);
        loadAlbum(albumsList_.getSelectedAlbum());
    }

    /**
     * Opens the metadata watch here and closes it in {@link #removeNotify}, so a panel dropped by
     * New Site or Rerun Setup Wizard stops being polled and can be garbage collected.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        if (metaWatch_ == null) metaWatch_ = ConfigWatcher.watch(() -> meta_, this::onMetadataChangedOnDisk);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (metaWatch_ != null) {
            metaWatch_.close();
            metaWatch_ = null;
        }
    }

    // -------------------------------------------------------------------------
    // Build UI
    // -------------------------------------------------------------------------

    private void buildUI() {
        setLayout(new BorderLayout());

        JPanel form = new ScrollableForm();
        form.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 8, VerticalFlowLayout.FILL));
        form.setBorder(new EmptyBorder(4, 8, 4, 10));

        DDLabelBorder basicSection = buildBasicSection();
        DDLabelBorder sourceSection = buildSourceSection();
        DDLabelBorder photosSection = buildPhotosSection();
        form.add(basicSection);
        form.add(sourceSection);
        form.add(photosSection);

        int labelColWidth = GuiUtils.setDDOptionLabelWidths(basicSection, 16);
        Dimension typeLabelSize = sourceTypeLabel_.getPreferredSize();
        typeLabelSize.width = labelColWidth;
        sourceTypeLabel_.setPreferredSize(typeLabelSize);
        checkboxIndent_ = labelColWidth + 8;
        recurse_.setBorder(new EmptyBorder(0, checkboxIndent_, 0, 0));

        finishBuildUI(form);
    }

    private DDLabelBorder buildBasicSection() {
        DDLabelBorder panel = section("albumbasic");

        slug_ = editable(new OptionText(null, "albumslug", STYLE, dummy_,
                PhotosConstants.MAX_SLUG_LENGTH, PhotosConstants.REGEXP_SLUG, PREFERRED_SLUG_TEXT_WIDTH));
        slug_.getTextField().setCustomValidator(text -> {
            if (currentAlbumsFile() == null || currentEntry_ == null) return true;
            return currentAlbumsFile().getAlbums().stream()
                    .filter(a -> a != currentEntry_)
                    .noneMatch(a -> text.equalsIgnoreCase(a.getSlug()));
        });

        // Source type first, as in the New Album dialog.  Sized to the option label column in
        // buildUI().
        sourceTypeLabel_ = new DDLabel("sourcetype", STYLE);
        sourceType_ = new SourceTypeRow(STYLE);
        sourceType_.getCredentialsButton().addActionListener(_ -> editCredentials());
        sourceType_.addChangeListener(this::sourceTypeChanged);
        panel.add(westCenterRow(sourceTypeLabel_, sourceType_));
        panel.add(buildPasswordRow(slug_, "albumpassword", "albumlock"));

        // Name is required for a local album and for an overridden synced one; see nameRequired().
        name_ = editable(new OptionText(null, "albumname", STYLE, dummy_,
                PhotosConstants.MAX_TEXT_LENGTH, PhotosConstants.REGEXP_OPTIONAL, 350));
        GuiUtils.setPreferredWidth(name_.getTextField(), PREFERRED_SHORT_TEXT_WIDTH);
        name_.getTextField().setCustomValidator(text -> !nameRequired() || !text.isBlank());
        nameOverride_ = new DDCheckBox("albumoverride", STYLE);
        nameHint_ = hintIcon();
        nameOverride_.addActionListener(_ -> overrideToggled(nameOverride_));
        panel.add(overrideRow(name_, nameOverride_, nameHint_));

        description_ = editable(new OptionTextArea(null, "albumdescription", STYLE, null, dummy_,
                PhotosConstants.MAX_DESCRIPTION_LENGTH, null, 4, 350));
        descOverride_ = new DDCheckBox("albumoverride", STYLE);
        descHint_ = hintIcon();
        descOverride_.addActionListener(_ -> overrideToggled(descOverride_));
        panel.add(overrideRow(description_, descOverride_, descHint_));

        recurse_ = editable(new OptionBoolean(null, "albumrecurse", STYLE, dummy_));
        manualSort_ = editable(new OptionBoolean(null, "albummanualsort", STYLE, dummy_));
        JPanel checkboxRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        checkboxRow.setOpaque(false);
        checkboxRow.add(recurse_);
        checkboxRow.add(manualSort_);
        panel.add(checkboxRow);

        return panel;
    }

    /**
     * The info icon beside an Override box.  Its tooltip says where the value shown comes from;
     * as text it was too long for a narrow window, and its width pushed the panel wider.
     */
    private static DDLabel hintIcon() {
        DDLabel hint = new DDLabel("albumsynchint", STYLE);
        hint.setText(null);
        hint.setIcon(DDIconButtons.INFO);
        return hint;
    }

    /** A field with its Override checkbox and the "from Immich" info icon beside it, top-aligned. */
    private static JPanel overrideRow(JPanel field, DDCheckBox override, DDLabel hint) {
        JPanel side = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        side.setOpaque(false);
        side.add(override);
        side.add(hint);
        JPanel sideTop = new JPanel(new BorderLayout());
        sideTop.setOpaque(false);
        sideTop.add(side, BorderLayout.NORTH);

        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setOpaque(false);
        row.add(field, BorderLayout.WEST);
        row.add(sideTop, BorderLayout.CENTER);
        return row;
    }

    @Override
    protected AppContext getContext() {
        return albumsList_.getContext();
    }

    @Override
    protected void openPasswordDialog() {
        Site site = albumsList_.getCurrentSite();
        if (site == null || currentEntry_ == null) return;
        TypedHashMap params = new TypedHashMap();
        params.setObject(PasswordDialog.PARAM_SITE, site);
        params.setObject(PasswordDialog.PARAM_ALBUM_SLUG, currentEntry_.getSlug());
        params.setObject("dialog-windowtitle-prop", "msg.windowtitle.PasswordDialog.album");
        albumsList_.getContext().processPhaseNow("PasswordDialog", params);
        updatePasswordUi();
    }

    /**
     * Refreshes the password button and lock icon.  An album with no entry of its own still
     * shows a lock when the site is protected — it really is protected — with a tooltip
     * saying where that protection comes from.
     */
    private void updatePasswordUi() {
        if (passwordBtn_ == null) return;
        passwordBtn_.setEnabled(!isEditing() && currentEntry_ != null);
        // Protection can't change while the form is being edited, and checkButtons() fires on
        // every keystroke - don't re-stat the passwords file that often.
        if (isEditing()) return;

        AlbumsFile af = currentAlbumsFile();
        PasswordsFile pf = af != null ? af.getPasswordsFile() : null;
        String slug = currentEntry_ != null ? currentEntry_.getSlug() : null;

        boolean locked = pf != null && slug != null && pf.isAlbumProtected(slug);
        boolean own    = locked && pf.hasAlbumEntry(slug);

        setLockState(locked, !locked ? "msg.password.unlocked.album"
                                    : own ? "msg.password.locked.album"
                                          : "msg.password.inherited.album");
    }

    private DDLabelBorder buildSourceSection() {
        DDLabelBorder panel = gridSection("albumsource");
        sourceSection_ = panel;

        DDLabel baseLabel = new DDLabel("albumbase", STYLE);

        // Both validators delegate to the same evaluation that updateWarnings() uses, so the
        // field's red state and the warning message can never disagree.
        Predicate<String> sourceValidator = _ -> isSourceValid();
        Predicate<String> coverValidator  = _ -> evalCover().isValid();

        baseCombo_ = editable(createBaseCombo(baseElement_));
        baseCombo_.addActionListener(_ -> {
            // re-trigger validation now that the base (and thus resolution) changed
            source_.setCustomValidator(sourceValidator);
            cover_.setCustomValidator(coverValidator);
            checkButtons();
        });

        source_ = editable(new OptionFileChooser(null, "albumsourcepath", STYLE, dummy_,
                PhotosConstants.MAX_PATH_LENGTH, PREFERRED_TEXT_WIDTH, null));
        source_.getTextField().setRegExp(PhotosConstants.REGEXP_OPTIONAL);
        source_.setDirectoryMode(true);
        source_.setChooserTitle(PropertyConfig.getMessage("msg.filechooser.title.source"));
        source_.setStartDirSupplier(() -> {
            Path baseAbsPath = resolveBasePath();
            return baseAbsPath != null
                    ? baseAbsPath.toString()
                    : prefs_.get(PREF_BROWSE_LAST_DIR, System.getProperty("user.home"));
        });
        source_.setPickedPathProcessor(chosen -> {
            Path baseAbsPath = resolveBasePath();
            if (baseAbsPath == null) {
                prefs_.put(PREF_BROWSE_LAST_DIR, chosen);
                return chosen;
            }
            try {
                Path realBase = baseAbsPath.toRealPath();
                Path realChosen = Path.of(chosen).toRealPath();
                return realBase.relativize(realChosen).toString();
            } catch (IOException | IllegalArgumentException ex) {
                return chosen;
            }
        });

        albumId_ = editable(new OptionText(null, "albumsyncid", STYLE, dummy_,
                PhotosConstants.MAX_PATH_LENGTH, PhotosConstants.REGEXP_OPTIONAL, PREFERRED_ALBUM_ID_WIDTH));
        DDTextField idField = albumId_.getTextField();
        Insets in = idField.getInsets();
        GuiUtils.setPreferredWidth(idField, idField.getFontMetrics(idField.getFont()).stringWidth(SAMPLE_ALBUM_ID)
                                            + in.left + in.right + 8);
        idField.setCustomValidator(this::isAlbumIdValid);
        albumId_.getTextField().addValidationListener(this::albumIdChanged);
        chooseBtn_ = new DDButton("syncchoose", STYLE);
        chooseBtn_.addActionListener(_ -> chooseAlbum());
        captions_ = editable(new OptionBoolean(null, "albumsynccaptions", STYLE, dummy_));

        cover_ = editable(new OptionFileChooser(null, "albumcover", STYLE, dummy_,
                PhotosConstants.MAX_PATH_LENGTH, PREFERRED_TEXT_WIDTH, null));
        cover_.setStartDirSupplier(() -> {
            Path sourceDir = resolveSourcePath();
            return sourceDir != null ? sourceDir.toString() : System.getProperty("user.home");
        });
        cover_.setPickedPathProcessor(chosen -> {
            Path sourceDir = resolveSourcePath();
            Path chosenPath = Path.of(chosen);
            if (sourceDir == null) return chosenPath.getFileName().toString();
            try {
                Path realSourceDir = sourceDir.toRealPath();
                Path realChosen = chosenPath.toRealPath();
                return realSourceDir.relativize(realChosen).toString();
            } catch (IOException ex) {
                return chosenPath.getFileName().toString();
            }
        });

        // Browse with the DD thumbnail grid, rooted at the album's source folder so a pick is
        // always expressible relative to it.  Videos are offered: photogen covers an album with
        // the clip's poster frame.
        PhotoChooser.install(cover_, albumsList_.getContext(), "msg.filechooser.title.cover",
                             this::resolveSourcePath, true);

        source_.setCustomValidator(sourceValidator);
        cover_.setCustomValidator(coverValidator);
        source_.getTextField().addValidationListener(() -> cover_.setCustomValidator(coverValidator));

        notSyncedArea_ = new DDHtmlArea("albumnotsynced", STYLE);
        notSyncedArea_.setEditable(false);
        notSyncedArea_.setDisplayOnly(true);
        notSyncedArea_.setOpaque(false);
        notSyncedArea_.setVisible(false);
        notSyncedArea_.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        warningArea_ = new DDHtmlArea("albumwarning", STYLE_ERROR);
        warningArea_.setEditable(false);
        warningArea_.setDisplayOnly(true);
        warningArea_.setOpaque(false);
        warningArea_.setVisible(false);
        warningArea_.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        coverPreview_ = new PhotoPreviewPanel(PREFERRED_TEXT_WIDTH, 188);
        coverPreview_.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));

        // Local and Sync rows sit in panels of their own, so switching the source type just
        // shows one and hides the other; the shared cover rows follow either.
        // GridBagForm.detail() adds grid-bag rows but leaves the layout to the caller.
        localRows_ = new JPanel(new GridBagLayout());
        localRows_.setOpaque(false);
        GridBagForm.detail(localRows_, STYLE)
                .row(baseLabel, baseCombo_, null)
                .span(source_);

        JPanel idRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        idRow.setOpaque(false);
        idRow.add(albumId_);
        idRow.add(Box.createHorizontalStrut(8));
        idRow.add(chooseBtn_);
        syncRows_ = new JPanel(new BorderLayout(0, 4));
        syncRows_.setOpaque(false);
        syncRows_.add(idRow, BorderLayout.NORTH);
        syncRows_.add(captions_, BorderLayout.CENTER);
        syncRows_.setVisible(false);

        GridBagForm.detail(panel, STYLE)
                .span(localRows_)
                .span(syncRows_)
                .span(cover_)
                .span(notSyncedArea_)
                .span(warningArea_)
                .span(coverPreview_)
                .glue();

        int labelWidth = GuiUtils.setDDOptionLabelWidths(panel, 16);
        Dimension baseLabelSize = baseLabel.getPreferredSize();
        baseLabelSize.width = labelWidth;
        baseLabel.setPreferredSize(baseLabelSize);
        captions_.setBorder(new EmptyBorder(0, labelWidth + 8, 0, 0));

        return panel;
    }

    private DDLabelBorder buildPhotosSection() {
        DDLabelBorder panel = section("albumphotos");
        editCaptionsBtn_ = new DDButton("editcaptions", STYLE);
        editCaptionsBtn_.addActionListener(_ -> openCaptionEditor());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.add(editCaptionsBtn_);
        panel.add(row);
        return panel;
    }

    private void openCaptionEditor() {
        Site site = albumsList_.getCurrentSite();
        if (site != null && currentEntry_ != null) {
            PhotogenEditorPhase.open(albumsList_.getContext(), site, currentEntry_);
        }
    }

    /** Caption editing is available on a saved album whose source directory resolves. */
    private void updateEditCaptionsButton() {
        if (editCaptionsBtn_ != null) {
            editCaptionsBtn_.setEnabled(!isEditing() && currentEntry_ != null && resolveSourcePath() != null);
        }
    }

    // -------------------------------------------------------------------------
    // Album loading
    // -------------------------------------------------------------------------

    public void loadAlbum(AlbumEntry entry) {
        populating_ = true;
        try {
            originalEntry_ = null;
            currentEntry_ = entry;
            pending_ = null;
            rebuildBaseList();
            if (entry == null) {
                clearFields();
            } else {
                populate(entry);
            }
            editBtn_.setEnabled(entry != null);
        } finally {
            populating_ = false;
        }
        setEditing(false);
    }

    private void rebuildBaseList() {
        populateBaseList(baseKeys_, baseDisplays_, currentAlbumsFile());
        baseCombo_.resetValues();
    }

    private void populate(AlbumEntry entry) {
        SyncEntry sync = entry.getSync();
        sourceType_.setSync(sync != null);
        sourceType_.setProviderId(sync != null ? sync.getProvider() : null);
        albumId_.getTextField().setText(sync != null ? nvl(sync.getAlbumId(), "") : "");
        captions_.getCheckBox().setSelected(sync == null || sync.isCaptionsEnabled());
        loadMeta();

        slug_.getTextField().setText(nvl(entry.getSlug(), ""));
        nameOverride_.setSelected(!isBlank(entry.getName()));
        descOverride_.setSelected(!isBlank(entry.getDescription()));
        name_.getTextField().setText(sync != null && !nameOverride_.isSelected() ? nvl(metaName_, "") : nvl(entry.getName(), ""));
        description_.setText(sync != null && !descOverride_.isSelected() ? nvl(metaDesc_, "") : nvl(entry.getDescription(), ""));
        baseCombo_.setSelectedItem(nvl(entry.getBase(), NONE_BASE));
        source_.setText(nvl(entry.getSource(), ""));
        cover_.setText(nvl(entry.getCover(), ""));
        recurse_.getCheckBox().setSelected(entry.isRecurse());
        manualSort_.getCheckBox().setSelected(entry.isManualSortOrder());
    }

    private void clearFields() {
        sourceType_.setSync(false);
        albumId_.getTextField().setText("");
        captions_.getCheckBox().setSelected(true);
        meta_ = null;
        metaName_ = metaDesc_ = null;
        synced_ = false;
        slug_.getTextField().setText("");
        nameOverride_.setSelected(false);
        descOverride_.setSelected(false);
        name_.getTextField().setText("");
        description_.setText("");
        baseCombo_.setSelectedItem(NONE_BASE);
        source_.setText("");
        cover_.setText("");
        recurse_.getCheckBox().setSelected(false);
        manualSort_.getCheckBox().setSelected(false);
    }

    private void updateWarnings() {
        if (populating_) return;
        PathValidation.PathStatus src = evalSource();
        PathValidation.PathStatus cov = evalCover();
        coverPreview_.setImageFile(cov.resolved());
        applyStatuses(warningArea_, List.of(src, cov), PREFERRED_TEXT_WIDTH);
        updateEditCaptionsButton();
        updatePasswordUi();
    }

    /**
     * Evaluates the source path against the selected base (folder, no image requirement).  A
     * synced album has no source to check: its folder is photogen's, and is the cover's root
     * once photogen has synced into it.
     */
    private PathValidation.PathStatus evalSource() {
        if (isSyncMode()) {
            return PathValidation.PathStatus.resolved(synced_ ? syncDir() : null);
        }
        return PathValidation.evaluateUnderBase(source_.getText(), resolveBasePath(),
                selectedBase() != null, false, "source");
    }

    /** A local album needs a source, which the field's regexp cannot require only sometimes. */
    private boolean isSourceValid() {
        if (isSyncMode()) return true;
        return !source_.getText().isBlank() && evalSource().isValid();
    }

    /** Evaluates the cover against the resolved source directory. */
    private PathValidation.PathStatus evalCover() {
        return PathValidation.evaluateCover(cover_.getText(), evalSource().resolved());
    }

    // -------------------------------------------------------------------------
    // Sync
    // -------------------------------------------------------------------------

    private boolean isSyncMode() {
        return sourceType_ != null && sourceType_.isSync();
    }

    /**
     * The sync folder for what is on screen.  It uses the <em>saved</em> slug, because the folder
     * is only moved to a new slug when the album is saved.
     */
    private Path syncDir() {
        AlbumsFile af = currentAlbumsFile();
        if (af == null || currentEntry_ == null) return null;
        return af.resolveSyncPath(af.getSettings().getId(), sourceType_.getProviderId(), currentEntry_.getSlug());
    }

    /** Re-reads {@code metadata.yaml} for the folder on screen, then recomputes the upstream values. */
    private void loadMeta() {
        Path dir = isSyncMode() ? syncDir() : null;
        meta_ = dir != null ? new SyncMetadataFile(dir).load() : null;
        applyMeta();
    }

    /**
     * Works out the upstream name and description for the album id on screen, and shows them in
     * any field that is not overridden.  {@code metadata.yaml} counts only when it records this
     * album; otherwise a Choose... result for this album stands in until photogen runs.
     */
    private void applyMeta() {
        String id = albumId_.getTextField().getText().strip();
        if (meta_ != null && meta_.existsOnDisk() && id.equalsIgnoreCase(nvl(meta_.getAlbumId(), ""))) {
            metaName_ = meta_.getName();
            metaDesc_ = meta_.getDescription();
            synced_ = meta_.hasSynced();
        } else if (pending_ != null && id.equalsIgnoreCase(pending_.id())) {
            metaName_ = pending_.name();
            metaDesc_ = SyncText.escape(pending_.description());
            synced_ = false;
        } else {
            metaName_ = metaDesc_ = null;
            synced_ = false;
        }
        if (isSyncMode()) {
            boolean was = populating_;
            populating_ = true;
            try {
                if (!nameOverride_.isSelected()) name_.getTextField().setText(nvl(metaName_, ""));
                if (!descOverride_.isSelected()) description_.setText(nvl(metaDesc_, ""));
            } finally {
                populating_ = was;
            }
        }
    }

    /** photogen wrote the album's {@code metadata.yaml}: pick up the new name, description and photos. */
    private void onMetadataChangedOnDisk() {
        if (meta_ == null) return;
        logger.info("sync metadata changed on disk: {}", meta_.getPath());
        meta_.load();
        applyMeta();
        cover_.setCustomValidator(_ -> evalCover().isValid());
        checkButtons();
        albumsList_.repaint();  // the list shows the upstream name for an album without one
    }

    private void sourceTypeChanged() {
        boolean was = wasSync_;
        wasSync_ = isSyncMode();
        if (populating_) return;
        if (was && wasSync_) {
            // Another provider: a different folder, so different upstream values.  What the user
            // chose to override stays overridden.
            loadMeta();
        } else if (wasSync_) {
            // Coming from Local, a typed value is kept as an override only where it differs from
            // the upstream one; matching text is just the upstream value, so no override.  Both
            // boxes are held on while the upstream values load, so the load cannot overwrite
            // the typed text before it is compared.
            String name = name_.getTextField().getText().trim();
            String desc = description_.getText().trim();
            nameOverride_.setSelected(true);
            descOverride_.setSelected(true);
            loadMeta();
            nameOverride_.setSelected(!name.isEmpty() && !name.equals(nvl(metaName_, "").trim()));
            descOverride_.setSelected(!desc.isEmpty() && !desc.equals(nvl(metaDesc_, "").trim()));
            applyMeta();   // fills the fields that are not overridden
        } else {
            // Going to Local, a name is required again: start from what photogen would publish.
            if (name_.getTextField().getText().isBlank() && currentEntry_ != null) {
                name_.getTextField().setText(nvl(currentEntry_.getSlug(), ""));
            }
            meta_ = null;
        }
        revalidateAll();
    }

    private void overrideToggled(DDCheckBox box) {
        if (!box.isSelected()) {
            // Reverting: the field shows the upstream value again.
            if (box == nameOverride_) name_.getTextField().setText(nvl(metaName_, ""));
            else description_.setText(nvl(metaDesc_, ""));
        }
        revalidateAll();
    }

    private void albumIdChanged() {
        if (populating_ || !isSyncMode()) return;
        applyMeta();
        cover_.setCustomValidator(_ -> evalCover().isValid());
    }

    /** Re-runs every validator whose answer depends on the source type or override state. */
    private void revalidateAll() {
        name_.getTextField().setCustomValidator(text -> !nameRequired() || !text.isBlank());
        DDTextField idField = albumId_.getTextField();
        Insets in = idField.getInsets();
        GuiUtils.setPreferredWidth(idField, idField.getFontMetrics(idField.getFont()).stringWidth(SAMPLE_ALBUM_ID)
                                            + in.left + in.right + 8);
        idField.setCustomValidator(this::isAlbumIdValid);
        source_.setCustomValidator(_ -> isSourceValid());
        cover_.setCustomValidator(_ -> evalCover().isValid());
        checkButtons();
    }

    private boolean nameRequired() {
        return !isSyncMode() || (nameOverride_ != null && nameOverride_.isSelected());
    }

    private boolean isAlbumIdValid(String text) {
        if (!isSyncMode()) return true;
        String id = text.strip();
        SyncProvider p = sourceType_.getProvider();
        if (p == null) return !id.isEmpty();       // a provider the app does not offer (mock)
        if (!p.isValidAlbumId(id)) return false;
        return !SyncUi.albumIdsInUse(currentAlbumsFile(), p.id(), currentEntry_).contains(id.toLowerCase(Locale.ROOT));
    }

    private void editCredentials() {
        Site site = albumsList_.getCurrentSite();
        SyncProvider p = sourceType_.getProvider();
        if (site != null && p != null) SyncUi.editCredentials(getContext(), site, p);
    }

    private void chooseAlbum() {
        Site site = albumsList_.getCurrentSite();
        SyncProvider p = sourceType_.getProvider();
        if (site == null || p == null) return;
        SyncAlbumInfo info = SyncAlbumChooserDialog.choose(getContext(), site, p,
                SyncUi.albumIdsInUse(currentAlbumsFile(), p.id(), currentEntry_),
                albumId_.getTextField().getText());
        if (info == null) return;
        pending_ = info;
        albumId_.getTextField().setText(info.id());
        applyMeta();
        revalidateAll();
    }

    /** Shows, hides and locks what depends on the source type, the override boxes and edit mode. */
    private void updateSyncUi() {
        boolean sync = isSyncMode();
        boolean editing = isEditing();

        sourceType_.setEditable(editing);
        localRows_.setVisible(!sync);
        syncRows_.setVisible(sync);
        sourceSection_.setText(PropertyConfig.getMessage(sync ? "labelborder.albumsync.label"
                                                              : "labelborder.albumsource.label"));
        chooseBtn_.setEnabled(editing && sync && sourceType_.getProvider() != null);

        nameOverride_.setVisible(sync);
        descOverride_.setVisible(sync);
        nameOverride_.setEnabled(editing);
        descOverride_.setEnabled(editing);
        name_.setDisplayOnly(!editing || (sync && !nameOverride_.isSelected()));
        description_.setDisplayOnly(!editing || (sync && !descOverride_.isSelected()));
        updateHint(nameHint_, nameOverride_, metaName_);
        updateHint(descHint_, descOverride_, metaDesc_);

        // Recurse is meaningless for a flat sync folder.  Manual Sort Order then leads the row,
        // so it takes the indent that lines it up with the fields.
        recurse_.setVisible(!sync);
        int indent = sync ? checkboxIndent_ : PhotosConstants.HORIZONTAL_GAP;
        if (manualSort_.getInsets().left != indent) manualSort_.setBorder(new EmptyBorder(0, indent, 0, 0));

        // Before the first sync there are no photos to choose a cover from.
        boolean showCover = !sync || synced_;
        cover_.setVisible(showCover);
        coverPreview_.setVisible(showCover);
        notSyncedArea_.setVisible(!showCover);
        if (!showCover) notSyncedArea_.setText(PropertyConfig.getMessage("msg.album.notsynced"));
    }

    private void updateHint(DDLabel hint, DDCheckBox override, String upstream) {
        boolean sync = isSyncMode();
        hint.setVisible(sync && !override.isSelected());
        if (!hint.isVisible()) return;
        SyncProvider p = sourceType_.getProvider();
        String provider = p != null ? p.displayName() : sourceType_.getProviderId();
        hint.setToolTipText(PropertyConfig.getMessage(isBlank(upstream) ? "msg.album.hint.pending"
                                                                        : "msg.album.hint.upstream", provider));
    }

    // -------------------------------------------------------------------------
    // Edit / Save / Cancel
    // -------------------------------------------------------------------------

    @Override
    protected void enterEditMode() {
        originalEntry_ = entryFromFields();
        setEditing(true);
    }

    @Override
    protected void cancelEdit() {
        loadAlbum(currentEntry_);
    }

    @Override
    protected void applyAndSave() {
        Site site = albumsList_.getCurrentSite();
        AlbumsFile af = currentAlbumsFile();
        if (site == null || af == null || currentEntry_ == null) return;
        if (!okayToOverwrite(af)) return;

        AlbumEntry updated = entryFromFields();
        // Pointing a synced album at another upstream album makes the next photogen run replace
        // its photos, so their caption edits are lost for good.  Once confirmed, the album
        // starts over as if it were new.
        if (SyncUi.switchesSyncedAlbum(currentEntry_, updated)) {
            if (!EngineUtils.displayConfirmationDialog(albumsList_.getContext(), PropertyConfig.getMessage(
                    "msg.confirm.switch.syncalbum", PhotosUtils.escapeHtml(af.displayName(currentEntry_))))) {
                return;
            }
            SyncUi.startFresh(updated);
        }
        String oldSlug = currentEntry_.getSlug();
        Path oldSyncDir = af.resolveSyncPath(currentEntry_);
        currentEntry_.setSlug(updated.getSlug());
        currentEntry_.setName(updated.getName());
        currentEntry_.setDescription(updated.getDescription());
        currentEntry_.setBase(updated.getBase());
        currentEntry_.setSource(updated.getSource());
        currentEntry_.setSync(updated.getSync());
        currentEntry_.setCover(updated.getCover());
        currentEntry_.setRecurse(updated.isRecurse());
        currentEntry_.setManualSortOrder(updated.isManualSortOrder());

        try {
            site.saveAlbumsFile();
        } catch (AlbumsFileException e) {
            logger.error("Failed to save albums file: {}", site.getAlbumsFilePath(), e);
            PhotosUtils.showSaveError(albumsList_.getContext(), site.getAlbumsFilePath(), e);
            return;
        }

        // Only once albums.yaml is safely written, and before setEditing() re-reads the
        // passwords file for the lock icon.
        if (!Objects.equals(oldSlug, currentEntry_.getSlug())) {
            PhotosUtils.renameAlbumPassword(albumsList_.getContext(), af,
                                            oldSlug, currentEntry_.getSlug());
            // A synced album's downloads follow its slug.  Moved only when the provider is the
            // same, since a different provider's folder holds a different album anyway.
            Path newSyncDir = af.resolveSyncPath(currentEntry_);
            if (oldSyncDir != null && newSyncDir != null
                    && oldSyncDir.getParent().equals(newSyncDir.getParent())) {
                PhotosUtils.renameSyncFolder(albumsList_.getContext(), oldSyncDir, newSyncDir);
            }
        }
        if (currentEntry_.isSynced() && pending_ != null
                && pending_.id().equalsIgnoreCase(currentEntry_.getSync().getAlbumId())) {
            AlbumDialog.writeStub(af, currentEntry_, pending_);
        }

        // Reload rather than just leave edit mode: the override boxes and upstream values are
        // derived from what was just saved.
        loadAlbum(currentEntry_);

        if (onSavedCallback_ != null) onSavedCallback_.run();

        showPhotogenReminder();
    }

    // -------------------------------------------------------------------------
    // Dirty check
    // -------------------------------------------------------------------------

    public boolean isDirty() {
        return isEditing() && originalEntry_ != null
                && !entryFromFields().equals(originalEntry_);
    }

    private AlbumEntry entryFromFields() {
        AlbumEntry e = new AlbumEntry();
        e.setSlug(slug_.getTextField().getText().trim());
        e.setCover(emptyToNull(cover_.getText().trim()));
        e.setRecurse(recurse_.getCheckBox().isSelected());
        e.setManualSortOrder(manualSort_.getCheckBox().isSelected());
        if (isSyncMode()) {
            // Start from the saved block so a mock: sub-block (not editable here) is carried over.
            SyncEntry saved = currentEntry_ != null ? currentEntry_.getSync() : null;
            SyncEntry s = saved != null && Objects.equals(saved.getProvider(), sourceType_.getProviderId())
                    ? new SyncEntry(saved) : new SyncEntry();
            s.setProvider(sourceType_.getProviderId());
            s.setAlbumId(emptyToNull(albumId_.getTextField().getText().strip()));
            s.setCaptions(captions_.getCheckBox().isSelected());
            e.setSync(s);
            e.setName(override(nameOverride_, name_.getTextField().getText().trim(), metaName_));
            e.setDescription(override(descOverride_, description_.getText().trim(), metaDesc_));
        } else {
            e.setName(name_.getTextField().getText().trim());
            e.setDescription(emptyToNull(description_.getText().trim()));
            e.setBase(selectedBase());
            e.setSource(emptyToNull(source_.getText().trim()));
        }
        return e;
    }

    /** The value to save for an overridable field: null unless overridden to something new. */
    private static String override(DDCheckBox box, String typed, String upstream) {
        if (!box.isSelected() || typed.isEmpty() || typed.equals(nvl(upstream, "").trim())) return null;
        return typed;
    }

    // -------------------------------------------------------------------------
    // Validation / button state
    // -------------------------------------------------------------------------

    @Override
    protected void checkButtons() {
        updateSyncUi();
        updateWarnings();
        if (!isEditing()) return;
        boolean valid = validatables_.stream().allMatch(DDValidatable::isValidData);
        if (valid) valid = !entryFromFields().equals(originalEntry_);
        saveBtn_.setEnabled(valid);
    }

    // -------------------------------------------------------------------------
    // Folder / file pickers
    // -------------------------------------------------------------------------

    private String selectedBase() {
        return selectedBaseKey(baseCombo_);
    }

    private Path resolveBasePath() {
        String base = selectedBase();
        AlbumsFile af = currentAlbumsFile();
        return (base != null && af != null) ? af.resolveBasePath(base) : null;
    }

    /** The folder the album's photos are in: its source, or its sync folder once one exists. */
    private Path resolveSourcePath() {
        if (isSyncMode()) {
            Path dir = syncDir();
            return dir != null && Files.isDirectory(dir) ? dir : null;
        }
        AlbumsFile af = currentAlbumsFile();
        return af != null ? af.resolveSourcePath(entryFromFields()) : null;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setOnSavedCallback(Runnable r) {
        onSavedCallback_ = r;
    }

    public void onBasesChanged() {
        albumsList_.reloadAlbumsFile();

        // Refresh currentEntry_ from the fresh AlbumsFile so stale base refs are corrected.
        // Do this mid-edit too: the slug validator excludes currentEntry_ by identity, and
        // applyAndSave() writes into it, so a stale instance breaks both.  Edits live in the
        // fields until save, so the entry's slug is still the one on disk.
        if (currentEntry_ != null) {
            AlbumsFile af = albumsList_.getCurrentAlbumsFile();
            if (af != null) {
                String slug = currentEntry_.getSlug();
                for (AlbumEntry a : af.getAlbums()) {
                    if (slug != null && slug.equals(a.getSlug())) {
                        currentEntry_ = a;
                        break;
                    }
                }
            }
        }

        // Mid-edit, keep the pending base choice (None included) if it still exists; otherwise
        // show the saved base.  Same idea as SiteDetailsPanel.rebuildHeroBaseList().
        String preserved = isEditing() ? nvl(selectedBase(), NONE_BASE) : null;

        rebuildBaseList();

        if (preserved != null && baseKeys_.contains(preserved)) {
            baseCombo_.setSelectedItem(preserved);
        } else if (currentEntry_ != null) {
            baseCombo_.setSelectedItem(nvl(currentEntry_.getBase(), NONE_BASE));
        }

        updateWarnings();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AlbumsFile currentAlbumsFile() {
        return albumsList_.getCurrentAlbumsFile();
    }

    private static String nvl(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static String emptyToNull(String s) {
        return (s != null && !s.isEmpty()) ? s : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
