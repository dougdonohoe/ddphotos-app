package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.SyncEntry;
import com.donohoedigital.ddphotos.sync.SyncAlbumInfo;
import com.donohoedigital.ddphotos.sync.SyncProvider;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.gui.DDButton;
import com.donohoedigital.gui.DDHtmlArea;
import com.donohoedigital.gui.DDLabel;
import com.donohoedigital.gui.DDTextField;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.Locale;

/**
 * Adds an album.  A <b>Local</b> album gets a base, a source folder, a slug and a name; its base
 * and source follow the same rules as in the detail panel (see {@link AlbumSourceFields}).  A
 * <b>Sync</b> album gets a slug and an upstream album id, usually picked with <b>Choose...</b>; it
 * has no name, source or base in {@code albums.yaml}, because photogen supplies all three.
 *
 * <p>Picking a folder suggests a slug and a name from the folder's name; picking an upstream
 * album suggests a slug from the album's name.  A later pick replaces a suggestion, but never
 * text the user typed.  A new local album starts under the site's first base, if it has one.
 *
 * <p>When the album was picked with Choose..., its upstream name and description are written to
 * a stub {@code metadata.yaml} in the album's sync folder, so the app can show them before
 * photogen first syncs.
 */
public class AlbumDialog extends PhotosDialog
{
    private static final Logger logger = LogManager.getLogger(AlbumDialog.class);

    public static final String PARAM_SITE = "site";

    private static final int PREFERRED_WIDTH = 650;
    /** The source field's starting width; the form stretches it to fill the row. */
    private static final int SOURCE_TEXT_WIDTH = 300;
    /** The form panel's left and right border, which the warning area has to fit inside. */
    private static final int FORM_SIDE_BORDERS = 16;

    private Site site_;
    private AlbumsFile af_;

    private JPanel form_;
    private JComponent wrapper_;
    private int width_;
    private int instructionsHeight_;

    private DDTextField slugField_;
    private Suggestion slugSuggestion_;
    private SourceTypeRow sourceType_;

    // Local
    private AlbumSourceFields localSource_;
    private DDHtmlArea warningArea_;
    private DDLabel nameLabel_;
    private DDTextField nameField_;
    private Suggestion nameSuggestion_;

    // Sync
    private DDLabel albumIdLabel_;
    private DDTextField albumIdField_;
    private DDButton chooseBtn_;
    private DDLabel syncNameLabel_;
    private DDTextField syncNameField_;
    private DDLabel syncDescLabel_;
    private DDTextField syncDescField_;

    /** The album picked with Choose..., while the album id field still holds its id. */
    private SyncAlbumInfo chosen_;

    // -------------------------------------------------------------------------
    // DialogPhase API
    // -------------------------------------------------------------------------

    @Override
    public JComponent createDialogContents()
    {
        site_ = (Site) phase_.getObject(PARAM_SITE);
        af_ = site_ != null ? site_.getOrCreateAlbumsFile() : null;

        slugField_ = new DDTextField("albumslug", STYLE);
        slugField_.setRegExp(PhotosConstants.REGEXP_SLUG);
        slugField_.setTextLengthLimit(PhotosConstants.MAX_SLUG_LENGTH);
        slugField_.setCustomValidator(text -> {
            if (af_ == null) return true;
            return af_.getAlbums().stream().noneMatch(a -> text.equalsIgnoreCase(a.getSlug()));
        });
        slugSuggestion_ = new Suggestion(slugField_);

        sourceType_ = new SourceTypeRow(STYLE);
        sourceType_.getCredentialsButton().addActionListener(_ -> {
            SyncProvider p = sourceType_.getProvider();
            if (p != null) SyncUi.editCredentials(context_, site_, p);
        });
        sourceType_.addChangeListener(this::modeChanged);

        localSource_ = new AlbumSourceFields(STYLE, new TypedHashMap(), SOURCE_TEXT_WIDTH,
                                             () -> af_, () -> !sourceType_.isSync());
        localSource_.rebuildBaseList();
        localSource_.selectBase(localSource_.firstBase());
        localSource_.getSource().getTextField().addValidationListener(this::folderChanged);
        localSource_.addBaseListener(this::checkButtons);

        warningArea_ = new DDHtmlArea("albumwarning", "OptionsError");
        warningArea_.setEditable(false);
        warningArea_.setDisplayOnly(true);
        warningArea_.setOpaque(false);
        warningArea_.setVisible(false);
        warningArea_.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));

        nameLabel_ = new DDLabel("albumname", STYLE);
        nameField_ = new DDTextField("albumname", STYLE);
        nameField_.setRegExp(PhotosConstants.REGEXP_REQUIRED);
        nameField_.setTextLengthLimit(PhotosConstants.MAX_TEXT_LENGTH);
        nameSuggestion_ = new Suggestion(nameField_);

        albumIdLabel_ = new DDLabel("syncalbumid", STYLE);
        albumIdField_ = new DDTextField("syncalbumid", STYLE);
        albumIdField_.setTextLengthLimit(PhotosConstants.MAX_PATH_LENGTH);
        albumIdField_.setCustomValidator(this::isAlbumIdValid);
        albumIdField_.addValidationListener(this::albumIdEdited);

        chooseBtn_ = new DDButton("syncchoose", STYLE);
        chooseBtn_.addActionListener(_ -> chooseAlbum());

        syncNameLabel_ = new DDLabel("syncalbumname", STYLE);
        syncNameField_ = displayOnlyField("syncalbumname");
        syncDescLabel_ = new DDLabel("syncalbumdescription", STYLE);
        syncDescField_ = displayOnlyField("syncalbumdescription");

        // Source type first: it decides the other rows.  Then where the photos come from, which
        // suggests a slug.  showModeRows() hides the rows the other mode uses.  The source field
        // brings its own label; adding it to the form's label column moves it out of the field.
        GridBagForm form = GridBagForm.dialog(STYLE)
                .row("sourcetype", sourceType_, null)
                .row(albumIdLabel_, albumIdField_, chooseBtn_)
                .row(localSource_.getBaseLabel(), localSource_.getBaseCombo(), null)
                .row(localSource_.getSource().getLabelComponent(), localSource_.getSource(), null)
                .span(warningArea_)
                .row("albumslug", slugField_, null)
                .row(nameLabel_, nameField_, null)
                .row(syncNameLabel_, syncNameField_, null)
                .row(syncDescLabel_, syncDescField_, null);

        // Measured with every row showing, so the dialog is wide enough for either mode.
        form_ = form.panel();
        width_ = Math.max(PREFERRED_WIDTH, form_.getPreferredSize().width);
        wrapper_ = wrapWithInstructions("addalbuminstruct",
                PropertyConfig.getMessage("msg.addalbum.instructions"), form_, width_);
        instructionsHeight_ = wrapper_.getPreferredSize().height - form_.getPreferredSize().height;
        showModeRows();
        return wrapper_;
    }

    @Override
    protected void opened()
    {
        super.opened();
        checkButtons();
    }

    @Override
    protected Component getFocusComponent()
    {
        return sourceType_.isSync() ? slugField_ : localSource_.getSource().getTextField();
    }

    @Override
    public boolean processButton(AppButton button)
    {
        if ("save".equals(button.getName())) {
            apply();
        }
        removeDialog();
        return true;
    }

    // -------------------------------------------------------------------------
    // Mode
    // -------------------------------------------------------------------------

    private void modeChanged()
    {
        showModeRows();
        // The album id's validity depends on the provider.
        albumIdField_.setCustomValidator(this::isAlbumIdValid);
        localSource_.getSource().revalidateData();
        checkButtons();
    }

    /** Shows the rows the selected source type uses and hides the rest. */
    private void showModeRows()
    {
        boolean sync = sourceType_.isSync();
        for (JComponent c : List.of(nameLabel_, nameField_, localSource_.getBaseLabel(), localSource_.getBaseCombo(),
                                    localSource_.getSource().getLabelComponent(), localSource_.getSource())) {
            c.setVisible(!sync);
        }
        for (JComponent c : List.of(albumIdLabel_, albumIdField_, chooseBtn_,
                                    syncNameLabel_, syncNameField_, syncDescLabel_, syncDescField_)) {
            c.setVisible(sync);
        }
        chooseBtn_.setEnabled(sourceType_.getProvider() != null);
        updateWarnings();
        fitToRows();
    }

    /** Shows what is wrong with a local album's source, as the detail panel does. */
    private void updateWarnings()
    {
        int width = width_ - FORM_SIDE_BORDERS;
        if (sourceType_.isSync()) {
            EditableDetailPanel.applyWarnings(warningArea_, List.of(), width);
        } else {
            EditableDetailPanel.applyStatuses(warningArea_, List.of(localSource_.evaluate()), width);
        }
    }

    /**
     * GridBagLayout skips invisible components, so hiding a row's widgets collapses the row.  The
     * wrapper's height was fixed when it was built, so it is re-fitted to the rows now showing;
     * otherwise the leftover space opens up as gaps above and below the form.
     */
    private void fitToRows()
    {
        Dimension size = new Dimension(width_, instructionsHeight_ + form_.getPreferredSize().height);
        if (size.equals(wrapper_.getPreferredSize())) return;
        wrapper_.setPreferredSize(size);
        wrapper_.revalidate();
        // A DialogPhase lives in an InternalDialog inside the app frame, so it is packed through
        // getDialog() - the Window ancestor would be the app frame itself.
        if (getDialog() != null) getDialog().pack();
    }

    // -------------------------------------------------------------------------
    // Sync album id
    // -------------------------------------------------------------------------

    private boolean isAlbumIdValid(String text)
    {
        String id = text.strip();
        SyncProvider p = sourceType_ != null ? sourceType_.getProvider() : null;
        if (p == null || !p.isValidAlbumId(id)) return false;
        return !SyncUi.albumIdsInUse(af_, p.id(), null).contains(id.toLowerCase(Locale.ROOT));
    }

    /** A hand edit that no longer matches the chosen album drops what Choose... filled in. */
    private void albumIdEdited()
    {
        if (chosen_ != null && !chosen_.id().equalsIgnoreCase(albumIdField_.getText().strip())) {
            chosen_ = null;
            syncNameField_.setText("");
            syncDescField_.setText("");
        }
    }

    private void chooseAlbum()
    {
        SyncProvider p = sourceType_.getProvider();
        if (p == null || site_ == null) return;
        SyncAlbumInfo info = SyncAlbumChooserDialog.choose(context_, site_, p, SyncUi.albumIdsInUse(af_, p.id(), null),
                                                           albumIdField_.getText());
        if (info == null) return;
        chosen_ = info;
        albumIdField_.setText(info.id());
        syncNameField_.setText(info.name() == null ? "" : info.name());
        syncDescField_.setText(info.description() == null ? "" : info.description().strip());
        slugSuggestion_.offer(slugFrom(info.name()));
        checkButtons();
    }

    /** Names the album, and its slug, after the source folder. */
    private void folderChanged()
    {
        String folder = localSource_.folderName();
        if (folder == null) return;
        slugSuggestion_.offer(slugFrom(folder));
        nameSuggestion_.offer(truncate(folder.strip(), PhotosConstants.MAX_TEXT_LENGTH));
    }

    /** A slug from an album name ({@link PhotosUtils#slugify}), cut to the maximum slug length. */
    static String slugFrom(String name)
    {
        return truncate(PhotosUtils.slugify(name), PhotosConstants.MAX_SLUG_LENGTH);
    }

    private static String truncate(String s, int max)
    {
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * A field the dialog fills in from a pick.  It takes a suggestion while it is blank or still
     * holds the previous one, so a later pick replaces a suggestion but never text the user typed.
     */
    private static final class Suggestion
    {
        private final DDTextField field_;
        /** What was last filled in; null once the user types over it. */
        private String suggested_;

        Suggestion(DDTextField field)
        {
            field_ = field;
            field.addValidationListener(() -> {
                if (!field_.getText().trim().equals(suggested_)) suggested_ = null;
            });
        }

        void offer(String value)
        {
            if (value == null || value.isBlank()) return;
            String current = field_.getText().trim();
            if (!current.isEmpty() && !current.equals(suggested_)) return;
            suggested_ = value;
            if (!value.equals(current)) field_.setText(value);
        }
    }

    // -------------------------------------------------------------------------
    // Button state
    // -------------------------------------------------------------------------

    @Override
    protected void checkButtons()
    {
        if (sourceType_ == null || wrapper_ == null) return;
        if (!sourceType_.isSync()) {
            updateWarnings();
            fitToRows();
        }
        if (okayButton_ == null) return;
        boolean valid = slugField_.isValidData()
                && (sourceType_.isSync() ? albumIdField_.isValidData()
                                         : nameField_.isValidData() && localSource_.isValid());
        okayButton_.setEnabled(valid);
    }

    // -------------------------------------------------------------------------
    // Apply
    // -------------------------------------------------------------------------

    private void apply()
    {
        if (site_ == null) return;
        AlbumsFile af = site_.getOrCreateAlbumsFile();

        AlbumEntry entry = new AlbumEntry();
        entry.setSlug(slugField_.getText().trim());
        if (sourceType_.isSync()) {
            entry.setSync(new SyncEntry(sourceType_.getProviderId(), albumIdField_.getText().strip(), true));
        } else {
            entry.setName(nameField_.getText().trim());
            entry.setBase(localSource_.selectedBase());
            entry.setSource(localSource_.sourceText());
        }

        try {
            site_.addAlbum(entry);
        } catch (AlbumsFileException e) {
            logger.error("Failed to save albums file: {}", site_.getAlbumsFilePath(), e);
            PhotosUtils.showSaveError(context_, site_.getAlbumsFilePath(), e);
            return;
        }

        if (entry.isSynced() && chosen_ != null) SyncUi.writeStub(af, entry, chosen_);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DDTextField displayOnlyField(String name)
    {
        DDTextField f = new DDTextField(name, STYLE);
        f.setDisplayOnly(true);
        return f;
    }
}
