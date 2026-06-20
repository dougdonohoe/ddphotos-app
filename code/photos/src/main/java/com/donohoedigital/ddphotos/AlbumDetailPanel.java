package com.donohoedigital.ddphotos;

import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.DataElement;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.prefs.Preferences;

public class AlbumDetailPanel extends EditableDetailPanel {
    private static final Logger logger = LogManager.getLogger(AlbumDetailPanel.class);
    private static final String PREF_BROWSE_LAST_DIR = "browsesource.lastdir";
    public static final int PREFERRED_TEXT_WIDTH = 350;
    public static final int PREFERRED_SHORT_TEXT_WIDTH = 350;

    private final AlbumsListPanel albumsList_;
    private final Preferences prefs_ = PhotosConstants.getAppPreferences();
    private final TypedHashMap dummy_ = new TypedHashMap();

    private AlbumEntry currentEntry_;
    private AlbumEntry originalEntry_;
    private boolean editing_    = false;
    private boolean populating_ = false;

    // Base combo — backed by mutable lists so resetValues() picks up site changes
    private final List<String> baseKeys_ = new ArrayList<>();
    private final List<String> baseDisplays_ = new ArrayList<>();
    private final DataElement<String> baseElement_;
    private DDComboBox<String> baseCombo_;

    // Fields
    private OptionText slug_;
    private OptionText name_;
    private OptionTextArea description_;
    private OptionFileChooser source_;
    private OptionFileChooser cover_;
    private DDHtmlArea warningArea_;
    private PhotoPreviewPanel coverPreview_;
    private OptionBoolean recurse_;
    private OptionBoolean manualSort_;

    private Runnable onSavedCallback_;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public AlbumDetailPanel(AlbumsListPanel albumsList) {
        albumsList_ = albumsList;
        baseElement_ = createBaseElement("albumbase", baseKeys_, baseDisplays_);
        buildUI();
        albumsList_.addSelectionListener(this::loadAlbum);
        loadAlbum(albumsList_.getSelectedAlbum());
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
        form.add(basicSection);
        form.add(sourceSection);

        int labelColWidth = GuiUtils.setDDOptionLabelWidths(basicSection, 16);
        recurse_.setBorder(new EmptyBorder(0, labelColWidth + 8, 0, 0));
        manualSort_.setBorder(new EmptyBorder(0, labelColWidth + 8, 0, 0));

        finishBuildUI(form);
    }

    private DDLabelBorder buildBasicSection() {
        DDLabelBorder panel = section("albumbasic");

        slug_ = new OptionText(null, "albumslug", STYLE, dummy_,
                64, "^[a-zA-Z0-9][a-zA-Z0-9_-]*$", 350);
        slug_.getTextField().setCustomValidator(text -> {
            if (currentAlbumsFile() == null || currentEntry_ == null) return true;
            return currentAlbumsFile().getAlbums().stream()
                    .filter(a -> a != currentEntry_)
                    .noneMatch(a -> text.equals(a.getSlug()));
        });
        GuiUtils.setPreferredWidth(slug_.getTextField(), PREFERRED_SHORT_TEXT_WIDTH);
        panel.add(slug_);

        name_ = new OptionText(null, "albumname", STYLE, dummy_, 200, "^.+$", 350);
        GuiUtils.setPreferredWidth(name_.getTextField(), PREFERRED_SHORT_TEXT_WIDTH);
        panel.add(name_);

        description_ = new OptionTextArea(null, "albumdescription", STYLE, null, dummy_,
                500, null, 4, 350);
        panel.add(description_);

        recurse_ = new OptionBoolean(null, "albumrecurse", STYLE, dummy_);
        manualSort_ = new OptionBoolean(null, "albummanualsort", STYLE, dummy_);
        panel.add(recurse_);
        panel.add(manualSort_);

        return panel;
    }

    private DDLabelBorder buildSourceSection() {
        DDLabelBorder panel = gridSection("albumsource");

        DDLabel baseLabel = new DDLabel("albumbase", STYLE);

        // Both validators delegate to the same evaluation that updateWarnings() uses, so the
        // field's red state and the warning message can never disagree.
        Predicate<String> sourceValidator = _ -> evalSource().isValid();
        Predicate<String> coverValidator  = _ -> evalCover().isValid();

        baseCombo_ = new DDComboBox<>(baseElement_, STYLE);
        initBaseCombo(baseCombo_);

        // The base display text is a full filesystem path, so the combo's natural
        // min/preferred width is huge.  Bound it: it stretches to fill via GridBag
        // when there's room and shrinks to a readable min otherwise, rather than
        // forcing the whole section wider than the viewport.
        Dimension comboSize = baseCombo_.getPreferredSize();
        baseCombo_.setPreferredSize(new Dimension(Math.min(comboSize.width, 380), comboSize.height));
        baseCombo_.setMinimumSize(new Dimension(200, comboSize.height));
        baseCombo_.addActionListener(_ -> {
            // re-trigger validation now that the base (and thus resolution) changed
            source_.setCustomValidator(sourceValidator);
            cover_.setCustomValidator(coverValidator);
            checkButtons();
        });

        source_ = new OptionFileChooser(null, "albumsourcepath", STYLE, dummy_, 500, PREFERRED_TEXT_WIDTH, null);
        source_.getTextField().setRegExp(".+");
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

        cover_ = new OptionFileChooser(null, "albumcover", STYLE, dummy_, 200, PREFERRED_TEXT_WIDTH, null);
        cover_.setChooserTitle(PropertyConfig.getMessage("msg.filechooser.title.cover"));
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

        source_.setCustomValidator(sourceValidator);
        cover_.setCustomValidator(coverValidator);
        source_.getTextField().addValidationListener(() -> cover_.setCustomValidator(coverValidator));

        warningArea_ = new DDHtmlArea("albumwarning", STYLE_ERROR);
        warningArea_.setEditable(false);
        warningArea_.setDisplayOnly(true);
        warningArea_.setOpaque(false);
        warningArea_.setVisible(false);
        warningArea_.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        coverPreview_ = new PhotoPreviewPanel(PREFERRED_TEXT_WIDTH, 188);
        coverPreview_.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));

        int row = 0;
        row = addRow(panel, baseLabel, baseCombo_, null, row);
        row = addSpanRow(panel, source_, row);
        row = addSpanRow(panel, cover_, row);
        row = addSpanRow(panel, warningArea_, row);
        row = addSpanRow(panel, coverPreview_, row);

        // Absorb any slack vertical space at the bottom so the GridBag keeps its rows
        // top-aligned instead of centering them (which opened a big gap above Base and
        // pushed the preview out of view when the panel is tall and narrow).
        GridBagConstraints glue = new GridBagConstraints();
        glue.gridx = 0; glue.gridy = row; glue.gridwidth = 3;
        glue.weighty = 1.0;
        glue.fill = GridBagConstraints.BOTH;
        panel.add(Box.createGlue(), glue);

        int labelWidth = GuiUtils.setDDOptionLabelWidths(panel, 16);
        Dimension baseLabelSize = baseLabel.getPreferredSize();
        baseLabelSize.width = labelWidth;
        baseLabel.setPreferredSize(baseLabelSize);

        return panel;
    }

    private static DDLabelBorder section(String name) {
        DDLabelBorder panel = new DDLabelBorder(name, STYLE);
        panel.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 4, VerticalFlowLayout.FILL));
        panel.setBorder(BorderFactory.createCompoundBorder(
                panel.getBorder(),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return panel;
    }

    /** Form that fills the viewport width but scrolls horizontally once it can't shrink further. */
    private static final class ScrollableForm extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 100; }
        @Override public boolean getScrollableTracksViewportWidth() {
            // Fill the viewport while it's wide enough; once it's narrower than our
            // minimum, stop shrinking and let the scroll pane add a horizontal bar.
            Component parent = getParent();
            return !(parent instanceof JViewport) || parent.getWidth() >= getMinimumSize().width;
        }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // -------------------------------------------------------------------------
    // Album loading
    // -------------------------------------------------------------------------

    public void loadAlbum(AlbumEntry entry) {
        populating_ = true;
        try {
            editing_ = false;
            originalEntry_ = null;
            currentEntry_ = entry;
            rebuildBaseList();
            if (entry == null) {
                clearFields();
                setReadOnly(true);
                editBtn_.setEnabled(false);
            } else {
                populate(entry);
                setReadOnly(true);
                editBtn_.setEnabled(true);
            }
        } finally {
            populating_ = false;
        }
        updateWarnings();
    }

    private void rebuildBaseList() {
        populateBaseList(baseKeys_, baseDisplays_, currentAlbumsFile());
        baseCombo_.resetValues();
    }

    private void populate(AlbumEntry entry) {
        slug_.getTextField().setText(nvl(entry.getSlug(), ""));
        name_.getTextField().setText(nvl(entry.getName(), ""));
        description_.getTextArea().setText(nvl(entry.getDescription(), ""));
        baseCombo_.setSelectedItem(nvl(entry.getBase(), NONE_BASE));
        source_.setText(nvl(entry.getSource(), ""));
        cover_.setText(nvl(entry.getCover(), ""));
        recurse_.getCheckBox().setSelected(entry.isRecurse());
        manualSort_.getCheckBox().setSelected(entry.isManualSortOrder());
    }

    private void clearFields() {
        slug_.getTextField().setText("");
        name_.getTextField().setText("");
        description_.getTextArea().setText("");
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
    }

    /** Evaluates the source path against the selected base (folder, no image requirement). */
    private PathValidation.PathStatus evalSource() {
        return PathValidation.evaluateUnderBase(source_.getText(), resolveBasePath(),
                selectedBase() != null, false, "source");
    }

    /** Evaluates the cover against the resolved source directory. */
    private PathValidation.PathStatus evalCover() {
        return PathValidation.evaluateCover(cover_.getText(), evalSource().resolved());
    }

    // -------------------------------------------------------------------------
    // Edit / Save / Cancel
    // -------------------------------------------------------------------------

    @Override
    protected void enterEditMode() {
        editing_ = true;
        originalEntry_ = entryFromFields();
        setReadOnly(false);
        checkButtons();
        fireEditModeChanged();
    }

    @Override
    protected void cancelEdit() {
        loadAlbum(currentEntry_);
        fireEditModeChanged();
    }

    @Override
    protected void applyAndSave() {
        Site site = albumsList_.getCurrentSite();
        AlbumsFile af = currentAlbumsFile();
        if (site == null || af == null || currentEntry_ == null) return;

        AlbumEntry updated = entryFromFields();
        currentEntry_.setSlug(updated.getSlug());
        currentEntry_.setName(updated.getName());
        currentEntry_.setDescription(updated.getDescription());
        currentEntry_.setBase(updated.getBase());
        currentEntry_.setSource(updated.getSource());
        currentEntry_.setCover(updated.getCover());
        currentEntry_.setRecurse(updated.isRecurse());
        currentEntry_.setManualSortOrder(updated.isManualSortOrder());

        try {
            site.saveAlbumsFile();
        } catch (AlbumsFileException e) {
            logger.error("Failed to save albums file: {}{}", site.getAlbumsFilePath(), Utils.formatExceptionText(e));
            EngineUtils.displayErrorDialog(albumsList_.getContext(), e.getMessage(), "msg.windowtitle.saveError", null);
            return;
        }

        editing_ = false;
        originalEntry_ = null;
        setReadOnly(true);
        fireEditModeChanged();

        if (onSavedCallback_ != null) onSavedCallback_.run();
    }

    // -------------------------------------------------------------------------
    // Read-only toggle
    // -------------------------------------------------------------------------

    private void setReadOnly(boolean readOnly) {
        slug_.setDisplayOnly(readOnly);
        name_.setDisplayOnly(readOnly);
        description_.setDisplayOnly(readOnly);
        baseCombo_.setDisplayOnly(readOnly);
        source_.setDisplayOnly(readOnly);
        cover_.setDisplayOnly(readOnly);
        recurse_.setDisplayOnly(readOnly);
        manualSort_.setDisplayOnly(readOnly);

        editBtn_.setVisible(readOnly);
        saveBtn_.setVisible(!readOnly);
        cancelBtn_.setVisible(!readOnly);
    }

    // -------------------------------------------------------------------------
    // Dirty check
    // -------------------------------------------------------------------------

    public boolean isDirty() {
        return editing_ && originalEntry_ != null
                && !entryFromFields().equals(originalEntry_);
    }

    @Override
    public boolean isEditing() {
        return editing_;
    }

    private AlbumEntry entryFromFields() {
        AlbumEntry e = new AlbumEntry();
        e.setSlug(slug_.getTextField().getText().trim());
        e.setName(name_.getTextField().getText().trim());
        e.setDescription(emptyToNull(description_.getTextArea().getText().trim()));
        e.setBase(selectedBase());
        e.setSource(emptyToNull(source_.getText().trim()));
        e.setCover(emptyToNull(cover_.getText().trim()));
        e.setRecurse(recurse_.getCheckBox().isSelected());
        e.setManualSortOrder(manualSort_.getCheckBox().isSelected());
        return e;
    }

    // -------------------------------------------------------------------------
    // Validation / button state
    // -------------------------------------------------------------------------

    @Override
    protected void checkButtons() {
        updateWarnings();
        if (!editing_) return;
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

    private Path resolveSourcePath() {
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

        // Refresh currentEntry_ from the fresh AlbumsFile so stale base refs are corrected
        if (currentEntry_ != null && !editing_) {
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

        rebuildBaseList();

        if (currentEntry_ != null) {
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
}
