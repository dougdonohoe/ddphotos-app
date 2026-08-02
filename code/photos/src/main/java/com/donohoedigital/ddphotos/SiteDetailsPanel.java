package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.DataElement;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.AlbumsSettings;
import com.donohoedigital.ddphotos.config.HeroEntry;
import com.donohoedigital.ddphotos.config.PasswordsFile;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class SiteDetailsPanel extends EditableDetailPanel {
    private static final Logger logger = LogManager.getLogger(SiteDetailsPanel.class);

    /** Site ids are short, and the row has to leave room for the password controls. */
    private static final int PREFERRED_ID_TEXT_WIDTH = 160;

    private final AppContext context_;
    private final SiteBarPanel siteBar_;
    private final TypedHashMap dummy_ = new TypedHashMap();

    private Site currentSite_;
    private BasesPanel basesPanel_;
    private AlbumsSettings originalSettings_;

    private OptionText albumId_;
    private OptionText siteName_;
    private OptionText siteUrl_;
    private OptionTextArea siteDescription_;
    private OptionFileChooser descriptionsFile_;
    private OptionText copyrightOwner_;
    private OptionInteger copyrightYear_;
    private OptionBoolean allowCrawling_;
    private DDLabel themeLabel_;
    private DDRadioButton themeLightRadio_, themeDarkRadio_;
    private OptionTextArea siteTitleHtml_;
    private OptionTextArea siteSubtitleHtml_;
    private OptionTextArea siteOverviewHtml_;
    private DDButton cssBtn_;
    private DDPanel cssLabelWrapper_;

    // Hero section
    private final List<String> heroBaseKeys_     = new ArrayList<>();
    private final List<String> heroBaseDisplays_ = new ArrayList<>();
    private DataElement<String> heroBaseElement_;
    private DDComboBox<String> heroBaseCombo_;
    private OptionFileChooser heroImage_;
    private DDLabel heroBaseLabel_;
    private DDLabel heroCropLabel_;
    private DDRadioButton heroTopRadio_, heroCenterRadio_, heroBottomRadio_;
    private PhotoPreviewPanel heroPreview_;
    private DDHtmlArea heroWarningArea_;

    public SiteDetailsPanel(AppContext context, SiteBarPanel siteBar) {
        super("editdetails.site");
        context_ = context;
        siteBar_ = siteBar;
        buildUI();
        siteBar_.addSiteListener(this::loadSite);
        loadSite(siteBar_.getSelectedSite());
    }

    // -------------------------------------------------------------------------
    // Build UI
    // -------------------------------------------------------------------------

    private void buildUI() {
        setLayout(new BorderLayout());

        heroBaseElement_ = createBaseElement("herobase", heroBaseKeys_, heroBaseDisplays_);

        JPanel form = new ScrollableForm();
        form.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 8, VerticalFlowLayout.FILL));
        form.setBorder(new EmptyBorder(8, 10, 2, 4));
        form.add(buildBasicSection());
        form.add(buildCopyrightSection());
        form.add(buildHtmlSection());
        form.add(buildHeroSection());

        // Capture the hero labels' natural widths before the global pass below forces
        // heroImage_'s internal label to the wide form-wide column.
        JComponent heroImageLabel = heroImage_.getLabelComponent();
        int heroLabelWidth = Math.max(heroBaseLabel_.getPreferredSize().width,
                Math.max(heroCropLabel_.getPreferredSize().width,
                        heroImageLabel.getPreferredSize().width)) + 6;

        int labelColWidth = GuiUtils.setDDOptionLabelWidths(form, 16);
        // The theme label isn't a DDOption, so align it to the column by hand.
        setLabelWidth(themeLabel_, labelColWidth);
        // The Custom CSS button sits beside the overview label inside a wrapper; without this the
        // wrapper would take the button's width and knock that row's label column out of line.
        setLabelWidth(cssLabelWrapper_, labelColWidth);

        // The hero section doesn't need to align with the sections above it, so give
        // Base / Image / Crop their own tight column and reclaim the wasted gap.
        setLabelWidth(heroBaseLabel_, heroLabelWidth);
        setLabelWidth(heroCropLabel_, heroLabelWidth);
        setLabelWidth(heroImageLabel, heroLabelWidth);

        basesPanel_ = new BasesPanel(context_);
        basesPanel_.addBasesChangedListener(this::rebuildHeroBaseList);
        finishBuildUI(form);
    }

    private DDLabelBorder buildBasicSection() {
        DDLabelBorder panel = section("sitesettings");

        albumId_ = editable(new OptionText(null, "siteid", STYLE, dummy_,
                64, "^[a-zA-Z0-9][a-zA-Z0-9_-]*$", PREFERRED_ID_TEXT_WIDTH));
        panel.add(buildPasswordRow(albumId_, "sitepassword", "sitelock"));

        siteName_ = editable(new OptionText(null, "sitename", STYLE, dummy_,
                200, "^.+$", 350));
        panel.add(siteName_);

        siteUrl_ = editable(new OptionText(null, "siteurl", STYLE, dummy_,
                200, "^(https?://\\S+)?$", 350));
        panel.add(siteUrl_);

        siteDescription_ = editable(new OptionTextArea(null, "sitedescription", STYLE, null, dummy_,
                500, null, 2, 350));
        panel.add(siteDescription_);

        descriptionsFile_ = editable(new OptionFileChooser(null, "descriptionsfile", STYLE, dummy_,
                100, 350, null));
        descriptionsFile_.setFileExtensionFilter("txt");
        panel.add(descriptionsFile_);

        panel.add(buildThemeRow());

        return panel;
    }

    @Override
    protected void openPasswordDialog() {
        if (currentSite_ == null) return;
        TypedHashMap params = new TypedHashMap();
        params.setObject(PasswordDialog.PARAM_SITE, currentSite_);
        params.setObject("dialog-windowtitle-prop", "msg.windowtitle.PasswordDialog.site");
        context_.processPhaseNow("PasswordDialog", params);
        updatePasswordUi();
    }

    /**
     * Refreshes the password button and lock icon.  Editing is blocked while the panel is in
     * edit mode, since the dialog writes albums.yaml (for {@code settings.passwords}) behind
     * the unsaved form.
     */
    private void updatePasswordUi() {
        if (passwordBtn_ == null) return;
        passwordBtn_.setEnabled(!isEditing() && currentSite_ != null);
        // Protection can't change while the form is being edited, and checkButtons() fires on
        // every keystroke - don't re-stat the passwords file that often.
        if (isEditing()) return;

        AlbumsFile af = albumsFile();
        PasswordsFile pf = af != null ? af.getPasswordsFile() : null;
        boolean locked = pf != null && pf.isSiteProtected();
        setLockState(locked, locked ? "msg.password.locked.site" : "msg.password.unlocked.site");
    }

    /**
     * "Default Theme" label + Light/Dark radios, laid out like a regular field row
     * (label in the WEST column, radios where the value field would go). The label's
     * width is aligned to the form's label column in buildUI(), same as the hero labels.
     */
    private JComponent buildThemeRow() {
        themeLabel_ = new DDLabel("sitetheme", STYLE);

        ButtonGroup themeGroup = new ButtonGroup();
        themeLightRadio_ = editable(new DDRadioButton("sitethemelight", STYLE));
        themeDarkRadio_  = editable(new DDRadioButton("sitethemedark",  STYLE));
        themeGroup.add(themeLightRadio_);
        themeGroup.add(themeDarkRadio_);
        themeDarkRadio_.setSelected(true);
        themeLightRadio_.addActionListener(_ -> checkButtons());
        themeDarkRadio_.addActionListener(_ -> checkButtons());

        JPanel radios = new JPanel(new WrapLayout(FlowLayout.LEFT, 20, 0));
        radios.setOpaque(false);
        radios.add(themeLightRadio_);
        radios.add(themeDarkRadio_);

        return westCenterRow(themeLabel_, radios);
    }

    private DDLabelBorder buildCopyrightSection() {
        DDLabelBorder panel = section("copyrightcrawling");

        copyrightOwner_ = editable(new OptionText(null, "copyrightowner", STYLE, dummy_, 200, null, 350));
        panel.add(copyrightOwner_);

        int currentYear = Year.now().getValue();
        copyrightYear_ = editable(new OptionInteger(null, "copyrightyear", STYLE, dummy_, currentYear, 1800, currentYear, 0));
        copyrightYear_.setEditable(true);

        // Tuck Allow Crawling into the year row's free CENTER slot (the spinner + its
        // left label live in WEST) so the two share a line instead of stacking.
        copyrightYear_.setBorderLayoutGap(0, 25);
        allowCrawling_ = editable(new OptionBoolean(null, "allowcrawling", STYLE, dummy_));
        copyrightYear_.add(allowCrawling_, BorderLayout.CENTER);
        panel.add(copyrightYear_);

        return panel;
    }

    private DDLabelBorder buildHtmlSection() {
        DDLabelBorder panel = section("htmlcontent");

        siteTitleHtml_ = editable(new OptionTextArea(null, "sitetitlehtml", STYLE, null, dummy_,
                1000, null, 2, 350));
        panel.add(siteTitleHtml_);

        siteSubtitleHtml_ = editable(new OptionTextArea(null, "sitesubtitlehtml", STYLE, null, dummy_,
                1000, null, 3, 350));
        panel.add(siteSubtitleHtml_);

        siteOverviewHtml_ = editable(new OptionTextArea(null, "siteoverviewhtml", STYLE, null, dummy_,
                2000, null, 5, 350));
        addCustomCssButton(siteOverviewHtml_);
        panel.add(siteOverviewHtml_);

        return panel;
    }

    /**
     * Tucks the "Custom CSS..." button into the empty space below the given field's label - its
     * text area is five rows tall, so the label column has room to spare and the section doesn't
     * have to grow to hold another row.  The button sits centered at the bottom of that space.
     *
     * <p>The label itself is reused rather than rebuilt, so its help text and font survive.  It
     * stays the option's {@code getLabelComponent()}, which is what
     * {@link GuiUtils#setDDOptionLabelWidths} sizes; the wrapper follows it in
     * {@link #buildUI} via {@link #setLabelWidth} so a wide button can't push this row's label
     * column out of line with the rest of the form.
     */
    private void addCustomCssButton(OptionTextArea option) {
        cssBtn_ = new DDButton("customcss", STYLE);
        cssBtn_.addActionListener(_ -> openCssEditor());

        // Centered at the foot of the label column, so it sits opposite the bottom of the text
        // area rather than crowding the label.
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttons.setOpaque(false);
        buttons.add(cssBtn_);

        JComponent label = option.getLabelComponent();
        option.remove(label);
        cssLabelWrapper_ = new DDPanel();
        cssLabelWrapper_.add(label, BorderLayout.NORTH);
        cssLabelWrapper_.add(buttons, BorderLayout.SOUTH);
        option.add(cssLabelWrapper_, BorderLayout.WEST);
    }

    private void openCssEditor() {
        if (currentSite_ == null) return;
        CssEditorPhase.open(context_, currentSite_);
    }

    /**
     * The editor writes albums.yaml (for {@code settings.css}), so it is blocked while the form
     * has unsaved edits - same rule as the password button.
     */
    private void updateCssButton() {
        if (cssBtn_ == null) return;
        cssBtn_.setEnabled(!isEditing() && currentSite_ != null);
    }

    private DDLabelBorder buildHeroSection() {
        DDLabelBorder panel = gridSection("sitehero");

        // Validator delegates to the same evaluation updateHeroWarnings() uses, so the field's
        // red state and the warning message can never disagree.
        Predicate<String> heroValidator = _ -> evalHero().isValid();

        heroBaseCombo_ = editable(createBaseCombo(heroBaseElement_));
        heroBaseCombo_.addActionListener(_ -> {
            // re-trigger validation now that the base (and thus resolution) changed
            heroImage_.setCustomValidator(heroValidator);
            checkButtons();
        });
        heroBaseLabel_ = new DDLabel("siteherobase", STYLE);

        heroImage_ = editable(new OptionFileChooser(null, "siteheroimage", STYLE, dummy_, 500, 350, null));
        heroImage_.setChooserTitle(PropertyConfig.getMessage("msg.filechooser.title.hero"));
        heroImage_.setStartDirSupplier(() -> {
            Path baseAbsPath = resolveHeroBasePath();
            return baseAbsPath != null ? baseAbsPath.toString() : System.getProperty("user.home");
        });
        heroImage_.setPickedPathProcessor(chosen -> {
            Path baseAbsPath = resolveHeroBasePath();
            if (baseAbsPath == null) return chosen;
            try {
                Path realBase = baseAbsPath.toRealPath();
                Path realChosen = Path.of(chosen).toRealPath();
                return realBase.relativize(realChosen).toString();
            } catch (IOException | IllegalArgumentException ex) {
                return chosen;
            }
        });
        heroImage_.setCustomValidator(heroValidator);

        heroCropLabel_ = new DDLabel("siteherocrop", STYLE);

        ButtonGroup cropGroup = new ButtonGroup();
        heroTopRadio_    = editable(new DDRadioButton("siteherotop",    STYLE));
        heroCenterRadio_ = editable(new DDRadioButton("siteherocenter", STYLE));
        heroBottomRadio_ = editable(new DDRadioButton("siteherobottom", STYLE));
        cropGroup.add(heroTopRadio_);
        cropGroup.add(heroCenterRadio_);
        cropGroup.add(heroBottomRadio_);
        heroCenterRadio_.setSelected(true);
        heroTopRadio_.addActionListener(   _ -> checkButtons());
        heroCenterRadio_.addActionListener(_ -> checkButtons());
        heroBottomRadio_.addActionListener(_ -> checkButtons());

        JPanel cropPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 20, 0));
        cropPanel.setOpaque(false);
        cropPanel.add(heroTopRadio_);
        cropPanel.add(heroCenterRadio_);
        cropPanel.add(heroBottomRadio_);

        heroWarningArea_ = new DDHtmlArea("albumwarning", STYLE_ERROR);
        heroWarningArea_.setEditable(false);
        heroWarningArea_.setDisplayOnly(true);
        heroWarningArea_.setOpaque(false);
        heroWarningArea_.setVisible(false);
        heroWarningArea_.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        heroPreview_ = new PhotoPreviewPanel(350, 55);
        heroPreview_.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));

        GridBagForm.detail(panel, STYLE)
                .row(heroBaseLabel_, heroBaseCombo_, null)
                .span(heroImage_)
                .row(heroCropLabel_, cropPanel, null)
                .span(heroWarningArea_)
                .span(heroPreview_)
                .glue();

        return panel;
    }

    private AlbumsFile albumsFile() {
        return currentSite_ != null ? currentSite_.getAlbumsFile() : null;
    }

    private void rebuildHeroBaseList() {
        String preserved = selectedHeroBase();
        populateBaseList(heroBaseKeys_, heroBaseDisplays_, albumsFile());
        heroBaseCombo_.resetValues();
        if (preserved != null) heroBaseCombo_.setSelectedItem(preserved);
    }

    private void updateHeroWarnings() {
        PathValidation.PathStatus hero = evalHero();
        applyStatuses(heroWarningArea_, List.of(hero), 350);
        heroPreview_.setCrop(selectedCrop());
        heroPreview_.setImageFile(hero.resolved());
    }

    /** Evaluates the hero image against the selected base (image required). */
    private PathValidation.PathStatus evalHero() {
        return PathValidation.evaluateUnderBase(heroImage_.getText(), resolveHeroBasePath(),
                selectedHeroBase() != null, true, "hero");
    }

    private String selectedHeroBase() {
        return selectedBaseKey(heroBaseCombo_);
    }

    private Path resolveHeroBasePath() {
        AlbumsFile af = albumsFile();
        String heroBase = selectedHeroBase();
        return (heroBase != null && af != null) ? af.resolveBasePath(heroBase) : null;
    }

    private static void setLabelWidth(JComponent label, int width) {
        Dimension d = label.getPreferredSize();
        d.width = width;
        label.setPreferredSize(d);
    }

    private String selectedCrop() {
        if (heroTopRadio_.isSelected())    return "top";
        if (heroBottomRadio_.isSelected()) return "bottom";
        return "center";
    }

    // -------------------------------------------------------------------------
    // Site loading
    // -------------------------------------------------------------------------

    public void loadSite(Site site) {
        currentSite_ = site;
        originalSettings_ = null;

        if (site == null) {
            descriptionsFile_.setStartDirSupplier(null);
            descriptionsFile_.setPickedPathProcessor(null);
            descriptionsFile_.setCustomValidator(null);
            rebuildHeroBaseList();
            populate(new AlbumsSettings());
            editBtn_.setEnabled(false);
            basesPanel_.loadSite(null);
            setEditing(false);
            return;
        }

        Path albumsPath = site.getAlbumsFilePath();
        Path configDir  = albumsPath != null ? albumsPath.getParent() : null;
        if (configDir != null) {
            descriptionsFile_.setStartDirSupplier(configDir::toString);
            descriptionsFile_.setPickedPathProcessor(chosen -> {
                try {
                    return configDir.relativize(Path.of(chosen)).toString();
                } catch (Exception e) {
                    return chosen;
                }
            });
            descriptionsFile_.setCustomValidator(s -> s.isBlank() || configDir.resolve(s).toFile().exists());
        } else {
            descriptionsFile_.setStartDirSupplier(null);
            descriptionsFile_.setPickedPathProcessor(null);
            descriptionsFile_.setCustomValidator(null);
        }

        AlbumsFile af = site.getAlbumsFile();
        AlbumsSettings s = af != null ? af.getSettings() : new AlbumsSettings();
        rebuildHeroBaseList();
        populate(s);
        editBtn_.setEnabled(true);
        basesPanel_.loadSite(site);
        setEditing(false);
    }

    private void populate(AlbumsSettings s) {
        albumId_.getTextField().setText(nvl(s.getId()));
        siteName_.getTextField().setText(nvl(s.getSiteName()));
        siteUrl_.getTextField().setText(nvl(s.getSiteUrl()));
        siteDescription_.getTextArea().setText(nvl(s.getSiteDescription()));
        descriptionsFile_.setText(nvl(s.getDescriptions()));
        copyrightOwner_.getTextField().setText(nvl(s.getCopyrightOwner()));
        copyrightYear_.getSpinner().setValue(s.getCopyrightYear() > 0 ? s.getCopyrightYear() : Year.now().getValue());
        allowCrawling_.getCheckBox().setSelected(s.isAllowCrawling());
        // photogen defaults an absent default_theme to "dark", so treat blank as dark here.
        if (AlbumsFile.THEME_LIGHT.equals(s.getDefaultTheme())) themeLightRadio_.setSelected(true);
        else                                                    themeDarkRadio_.setSelected(true);
        siteTitleHtml_.getTextArea().setText(nvl(s.getSiteTitleHtml()));
        siteSubtitleHtml_.getTextArea().setText(nvl(s.getSiteSubtitleHtml()));
        siteOverviewHtml_.getTextArea().setText(nvl(s.getSiteOverviewHtml()));
        HeroEntry hero = s.getHero();
        if (hero != null) {
            heroBaseCombo_.setSelectedItem(hero.getBase() != null ? hero.getBase() : NONE_BASE);
            heroImage_.setText(nvl(hero.getImage()));
            String crop = hero.getCrop();
            if ("top".equals(crop))         heroTopRadio_.setSelected(true);
            else if ("bottom".equals(crop)) heroBottomRadio_.setSelected(true);
            else                            heroCenterRadio_.setSelected(true);
        } else {
            heroBaseCombo_.setSelectedItem(NONE_BASE);
            heroImage_.setText("");
            heroCenterRadio_.setSelected(true);
        }
        updateHeroWarnings();
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    // -------------------------------------------------------------------------
    // Edit / Save / Cancel
    // -------------------------------------------------------------------------

    @Override
    protected void enterEditMode() {
        originalSettings_ = settingsFromFields();
        setEditing(true);
    }

    @Override
    protected void cancelEdit() {
        loadSite(currentSite_);
    }

    @Override
    protected void applyAndSave() {
        AlbumsFile af = currentSite_.getOrCreateAlbumsFile();
        AlbumsSettings s = af.getSettings();
        AlbumsSettings updated = settingsFromFields();
        s.setId(updated.getId());
        s.setSiteName(updated.getSiteName());
        s.setSiteUrl(updated.getSiteUrl());
        s.setSiteDescription(updated.getSiteDescription());
        s.setDescriptions(updated.getDescriptions());
        s.setCopyrightOwner(updated.getCopyrightOwner());
        s.setCopyrightYear(updated.getCopyrightYear());
        s.setAllowCrawling(updated.isAllowCrawling());
        s.setDefaultTheme(updated.getDefaultTheme());
        s.setSiteTitleHtml(updated.getSiteTitleHtml());
        s.setSiteSubtitleHtml(updated.getSiteSubtitleHtml());
        s.setSiteOverviewHtml(updated.getSiteOverviewHtml());
        s.setHero(updated.getHero());

        try {
            currentSite_.saveAlbumsFile();
        } catch (AlbumsFileException e) {
            logger.error("Failed to save albums file: {}", currentSite_.getAlbumsFilePath(), e);
            PhotosUtils.showSaveError(context_, currentSite_.getAlbumsFilePath(), e);
            return;
        }

        originalSettings_ = null;
        setEditing(false);
        siteBar_.refreshCombo(currentSite_);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public BasesPanel getBasesPanel() {
        return basesPanel_;
    }

    public void addBasesChangedListener(Runnable listener) {
        basesPanel_.addBasesChangedListener(listener);
    }

    // -------------------------------------------------------------------------
    // Dirty check
    // -------------------------------------------------------------------------

    public boolean isDirty() {
        return isEditing() && originalSettings_ != null
                && !settingsFromFields().equals(originalSettings_);
    }

    private AlbumsSettings settingsFromFields() {
        AlbumsSettings s = new AlbumsSettings();
        s.setId(albumId_.getTextField().getText().trim());
        s.setSiteName(siteName_.getTextField().getText().trim());
        s.setSiteUrl(siteUrl_.getTextField().getText().trim());
        s.setSiteDescription(siteDescription_.getTextArea().getText().trim());
        s.setDescriptions(descriptionsFile_.getText().trim());
        s.setCopyrightOwner(copyrightOwner_.getTextField().getText().trim());
        s.setCopyrightYear(copyrightYear_.getValue());
        s.setAllowCrawling(allowCrawling_.getCheckBox().isSelected());
        s.setDefaultTheme(themeLightRadio_.isSelected() ? AlbumsFile.THEME_LIGHT : AlbumsFile.THEME_DARK);
        s.setSiteTitleHtml(siteTitleHtml_.getTextArea().getText().trim());
        s.setSiteSubtitleHtml(siteSubtitleHtml_.getTextArea().getText().trim());
        s.setSiteOverviewHtml(siteOverviewHtml_.getTextArea().getText().trim());
        String heroImage = heroImage_.getText().trim();
        if (!heroImage.isEmpty()) {
            HeroEntry hero = new HeroEntry();
            hero.setImage(heroImage);
            hero.setBase(selectedHeroBase());
            hero.setCrop(selectedCrop());
            s.setHero(hero);
        }
        return s;
    }

    // -------------------------------------------------------------------------
    // Validation / button state
    // -------------------------------------------------------------------------

    @Override
    protected void checkButtons() {
        updateHeroWarnings();
        updatePasswordUi();
        updateCssButton();
        if (!isEditing()) return;
        boolean valid = validatables_.stream().allMatch(DDValidatable::isValidData);
        if (valid) valid = !settingsFromFields().equals(originalSettings_);
        saveBtn_.setEnabled(valid);
    }
}
