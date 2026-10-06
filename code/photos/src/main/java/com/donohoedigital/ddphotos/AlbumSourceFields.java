package com.donohoedigital.ddphotos;

import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.gui.DDComboBox;
import com.donohoedigital.gui.DDLabel;
import com.donohoedigital.gui.OptionFileChooser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

/**
 * The <b>Base</b> and <b>Source</b> fields of a local album, shared by {@link AlbumDetailPanel}
 * and {@link AlbumDialog} so both apply the same rules.
 *
 * <p>The source is required and is checked against the selected base with
 * {@link PathValidation#evaluateUnderBase}.  A folder picked with the browse button is made
 * relative to the base when one is selected.  The caller lays the fields out and decides when
 * they apply: while {@code active} is false (a synced album), the source is always valid.
 */
final class AlbumSourceFields {
    private static final String PREF_BROWSE_LAST_DIR = "browsesource.lastdir";

    private final Supplier<AlbumsFile> albumsFile_;
    private final BooleanSupplier active_;
    private final Preferences prefs_ = PhotosConstants.getAppPreferences();

    // Backed by mutable lists so resetValues() picks up site changes.
    private final List<String> baseKeys_ = new ArrayList<>();
    private final List<String> baseDisplays_ = new ArrayList<>();

    private final DDLabel baseLabel_;
    private final DDComboBox<String> baseCombo_;
    private final OptionFileChooser source_;
    private final List<Runnable> baseListeners_ = new ArrayList<>();

    /**
     * @param albumsFile the albums file whose bases are offered and paths resolved; may supply null
     * @param active     whether the album is local, so the source applies
     */
    AlbumSourceFields(String style, TypedHashMap map, int width,
                      Supplier<AlbumsFile> albumsFile, BooleanSupplier active) {
        albumsFile_ = albumsFile;
        active_ = active;

        baseLabel_ = new DDLabel("albumbase", style);
        baseCombo_ = EditableDetailPanel.createBaseCombo(
                EditableDetailPanel.createBaseElement("albumbase", baseKeys_, baseDisplays_));

        source_ = new OptionFileChooser(null, "albumsourcepath", style, map,
                PhotosConstants.MAX_PATH_LENGTH, width, null);
        source_.getTextField().setRegExp(PhotosConstants.REGEXP_OPTIONAL);
        source_.setDirectoryMode(true);
        source_.setChooserTitle(PropertyConfig.getMessage("msg.filechooser.title.source"));
        source_.setStartDirSupplier(() -> {
            Path baseAbsPath = resolveBasePath();
            return baseAbsPath != null
                    ? baseAbsPath.toString()
                    : prefs_.get(PREF_BROWSE_LAST_DIR, System.getProperty("user.home"));
        });
        source_.setPickedPathProcessor(this::relativizeToBase);
        source_.setCustomValidator(_ -> isValid());

        // The source resolves against the base, so check it again when the base changes.  A
        // change in validity fires no listener, so callers hear about it only afterward.
        baseCombo_.addActionListener(_ -> {
            source_.revalidateData();
            baseListeners_.forEach(Runnable::run);
        });
    }

    /** Runs after a base change, once the source has been checked against the new base. */
    void addBaseListener(Runnable listener) {
        baseListeners_.add(listener);
    }

    DDLabel getBaseLabel() {
        return baseLabel_;
    }

    DDComboBox<String> getBaseCombo() {
        return baseCombo_;
    }

    OptionFileChooser getSource() {
        return source_;
    }

    /** Refills the base list from the current albums file. */
    void rebuildBaseList() {
        EditableDetailPanel.populateBaseList(baseKeys_, baseDisplays_, albumsFile_.get());
        baseCombo_.resetValues();
    }

    /** The first base the site defines, or null when it has none. */
    String firstBase() {
        return baseKeys_.size() > 1 ? baseKeys_.get(1) : null;
    }

    /** Selects the given base, or None for null. */
    void selectBase(String base) {
        baseCombo_.setSelectedItem(base != null ? base : EditableDetailPanel.NONE_BASE);
    }

    /** True when the base list offers the given base; null (None) is always offered. */
    boolean hasBase(String base) {
        return base == null || baseKeys_.contains(base);
    }

    /** The selected base, or null when None is selected. */
    String selectedBase() {
        return EditableDetailPanel.selectedBaseKey(baseCombo_);
    }

    /** The source as typed, trimmed; empty when blank. */
    String sourceText() {
        return source_.getText().trim();
    }

    Path resolveBasePath() {
        String base = selectedBase();
        AlbumsFile af = albumsFile_.get();
        return (base != null && af != null) ? af.resolveBasePath(base) : null;
    }

    /** Evaluates the source against the selected base: a folder, with no image requirement. */
    PathValidation.PathStatus evaluate() {
        return PathValidation.evaluateUnderBase(source_.getText(), resolveBasePath(),
                selectedBase() != null, false, "source");
    }

    /** A local album needs a source, which the field's regexp cannot require only sometimes. */
    boolean isValid() {
        if (!active_.getAsBoolean()) return true;
        return !source_.getText().isBlank() && evaluate().isValid();
    }

    /**
     * The name of the source folder, for suggesting a slug; null when the source is blank.
     */
    String folderName() {
        String text = sourceText();
        if (text.isEmpty()) return null;
        try {
            Path name = Path.of(text).getFileName();
            return name != null ? name.toString() : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** A picked folder, relative to the selected base when there is one. */
    private String relativizeToBase(String chosen) {
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
    }
}
