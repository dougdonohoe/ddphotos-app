package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.DataElement;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.AlbumsSettings;
import com.donohoedigital.ddphotos.config.HeroEntry;
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
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class SiteDetailsPanel extends EditableDetailPanel {
    private static final Logger logger = LogManager.getLogger(SiteDetailsPanel.class);

    private final AppContext context_;
    private final SiteBarPanel siteBar_;
    private final TypedHashMap dummy_ = new TypedHashMap();

    private Site currentSite_;
    private BasesPanel basesPanel_;
    private AlbumsSettings originalSettings_;
    private boolean editing_ = false;

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

        albumId_ = new OptionText(null, "siteid", STYLE, dummy_,
                64, "^[a-zA-Z0-9][a-zA-Z0-9_-]*$", 350);
        panel.add(albumId_);

        siteName_ = new OptionText(null, "sitename", STYLE, dummy_,
                200, "^.+$", 350);
        panel.add(siteName_);

        siteUrl_ = new OptionText(null, "siteurl", STYLE, dummy_,
                200, "^(https?://\\S+)?$", 350);
        panel.add(siteUrl_);

        siteDescription_ = new OptionTextArea(null, "sitedescription", STYLE, null, dummy_,
                500, null, 2, 350);
        panel.add(siteDescription_);

        descriptionsFile_ = new OptionFileChooser(null, "descriptionsfile", STYLE, dummy_,
                100, 350, null);
        descriptionsFile_.setFileExtensionFilter("txt");
        panel.add(descriptionsFile_);

        panel.add(buildThemeRow());

        return panel;
    }

    /**
     * "Default Theme" label + Light/Dark radios, laid out like a regular field row
     * (label in the WEST column, radios where the value field would go). The label's
     * width is aligned to the form's label column in buildUI(), same as the hero labels.
     */
    private JComponent buildThemeRow() {
        themeLabel_ = new DDLabel("sitetheme", STYLE);

        ButtonGroup themeGroup = new ButtonGroup();
        themeLightRadio_ = new DDRadioButton("sitethemelight", STYLE);
        themeDarkRadio_  = new DDRadioButton("sitethemedark",  STYLE);
        themeGroup.add(themeLightRadio_);
        themeGroup.add(themeDarkRadio_);
        themeDarkRadio_.setSelected(true);
        themeLightRadio_.addActionListener(_ -> checkButtons());
        themeDarkRadio_.addActionListener(_ -> checkButtons());

        JPanel radios = new JPanel(new WrapLayout(FlowLayout.LEFT, 20, 0));
        radios.setOpaque(false);
        radios.add(themeLightRadio_);
        radios.add(themeDarkRadio_);

        DDPanel row = new DDPanel();
        row.setBorderLayoutGap(0, 8);
        row.add(themeLabel_, BorderLayout.WEST);
        row.add(radios, BorderLayout.CENTER);
        return row;
    }

    private DDLabelBorder buildCopyrightSection() {
        DDLabelBorder panel = section("copyrightcrawling");

        copyrightOwner_ = new OptionText(null, "copyrightowner", STYLE, dummy_, 200, null, 350);
        panel.add(copyrightOwner_);

        int currentYear = Year.now().getValue();
        copyrightYear_ = new OptionInteger(null, "copyrightyear", STYLE, dummy_, currentYear, 1800, currentYear, 0);
        copyrightYear_.setEditable(true);

        // Tuck Allow Crawling into the year row's free CENTER slot (the spinner + its
        // left label live in WEST) so the two share a line instead of stacking.
        copyrightYear_.setBorderLayoutGap(0, 25);
        allowCrawling_ = new OptionBoolean(null, "allowcrawling", STYLE, dummy_);
        copyrightYear_.add(allowCrawling_, BorderLayout.CENTER);
        panel.add(copyrightYear_);

        return panel;
    }

    private DDLabelBorder buildHtmlSection() {
        DDLabelBorder panel = section("htmlcontent");

        siteTitleHtml_ = new OptionTextArea(null, "sitetitlehtml", STYLE, null, dummy_,
                1000, null, 2, 350);
        panel.add(siteTitleHtml_);

        siteSubtitleHtml_ = new OptionTextArea(null, "sitesubtitlehtml", STYLE, null, dummy_,
                1000, null, 3, 350);
        panel.add(siteSubtitleHtml_);

        siteOverviewHtml_ = new OptionTextArea(null, "siteoverviewhtml", STYLE, null, dummy_,
                2000, null, 5, 350);
        panel.add(siteOverviewHtml_);

        return panel;
    }

    private DDLabelBorder buildHeroSection() {
        DDLabelBorder panel = gridSection("sitehero");

        // Validator delegates to the same evaluation updateHeroWarnings() uses, so the field's
        // red state and the warning message can never disagree.
        Predicate<String> heroValidator = _ -> evalHero().isValid();

        heroBaseCombo_ = new DDComboBox<>(heroBaseElement_, STYLE);
        initBaseCombo(heroBaseCombo_);

        // The base display text is a full filesystem path, so the combo's natural
        // min/preferred width is huge.  Bound it: it stretches to fill via GridBag
        // when there's room and shrinks to a readable min otherwise, rather than
        // forcing the whole hero section wider than the viewport.
        Dimension comboSize = heroBaseCombo_.getPreferredSize();
        heroBaseCombo_.setPreferredSize(new Dimension(Math.min(comboSize.width, 380), comboSize.height));
        heroBaseCombo_.setMinimumSize(new Dimension(200, comboSize.height));
        heroBaseCombo_.addActionListener(_ -> {
            // re-trigger validation now that the base (and thus resolution) changed
            heroImage_.setCustomValidator(heroValidator);
            checkButtons();
        });
        heroBaseLabel_ = new DDLabel("siteherobase", STYLE);

        heroImage_ = new OptionFileChooser(null, "siteheroimage", STYLE, dummy_, 500, 350, null);
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
        heroTopRadio_    = new DDRadioButton("siteherotop",    STYLE);
        heroCenterRadio_ = new DDRadioButton("siteherocenter", STYLE);
        heroBottomRadio_ = new DDRadioButton("siteherobottom", STYLE);
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

        int row = 0;
        row = addRow(panel, heroBaseLabel_, heroBaseCombo_, null, row);
        row = addSpanRow(panel, heroImage_, row);
        row = addRow(panel, heroCropLabel_, cropPanel, null, row);
        row = addSpanRow(panel, heroWarningArea_, row);
        row = addSpanRow(panel, heroPreview_, row);

        // Absorb any slack vertical space at the bottom so the GridBag keeps its rows
        // top-aligned instead of centering them (which opens a gap above Base and
        // pushes the preview out of view when the panel is tall and narrow).
        GridBagConstraints glue = new GridBagConstraints();
        glue.gridx = 0; glue.gridy = row; glue.gridwidth = 3;
        glue.weighty = 1.0;
        glue.fill = GridBagConstraints.BOTH;
        panel.add(Box.createGlue(), glue);

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

    private static DDLabelBorder section(String name) {
        DDLabelBorder panel = new DDLabelBorder(name, STYLE);
        panel.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 4, VerticalFlowLayout.FILL));
        panel.setBorder(BorderFactory.createCompoundBorder(
                panel.getBorder(),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return panel;
    }

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
    // Site loading
    // -------------------------------------------------------------------------

    public void loadSite(Site site) {
        editing_ = false;
        currentSite_ = site;
        originalSettings_ = null;

        if (site == null) {
            descriptionsFile_.setStartDirSupplier(null);
            descriptionsFile_.setPickedPathProcessor(null);
            descriptionsFile_.setCustomValidator(null);
            rebuildHeroBaseList();
            populate(new AlbumsSettings());
            setReadOnly(true);
            editBtn_.setEnabled(false);
            basesPanel_.loadSite(null);
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
        setReadOnly(true);
        editBtn_.setEnabled(true);
        basesPanel_.loadSite(site);
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
        editing_ = true;
        originalSettings_ = settingsFromFields();
        setReadOnly(false);
        checkButtons();
        fireEditModeChanged();
    }

    @Override
    protected void cancelEdit() {
        loadSite(currentSite_);
        fireEditModeChanged();
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
            logger.error("Failed to save albums file: {}{}", currentSite_.getAlbumsFilePath(), Utils.formatExceptionText(e));
            EngineUtils.displayErrorDialog(context_, e.getMessage(), "msg.windowtitle.saveError", null);
            return;
        }

        editing_ = false;
        originalSettings_ = null;
        setReadOnly(true);
        siteBar_.refreshCombo(currentSite_);
        fireEditModeChanged();
    }

    // -------------------------------------------------------------------------
    // Read-only toggle
    // -------------------------------------------------------------------------

    private void setReadOnly(boolean readOnly) {
        albumId_.setDisplayOnly(readOnly);
        siteName_.setDisplayOnly(readOnly);
        siteUrl_.setDisplayOnly(readOnly);
        siteDescription_.setDisplayOnly(readOnly);
        descriptionsFile_.setDisplayOnly(readOnly);
        copyrightOwner_.setDisplayOnly(readOnly);
        copyrightYear_.setDisplayOnly(readOnly);
        allowCrawling_.setDisplayOnly(readOnly);
        themeLightRadio_.setDisplayOnly(readOnly);
        themeDarkRadio_.setDisplayOnly(readOnly);
        siteTitleHtml_.setDisplayOnly(readOnly);
        siteSubtitleHtml_.setDisplayOnly(readOnly);
        siteOverviewHtml_.setDisplayOnly(readOnly);
        heroBaseCombo_.setDisplayOnly(readOnly);
        heroImage_.setDisplayOnly(readOnly);
        heroTopRadio_.setDisplayOnly(readOnly);
        heroCenterRadio_.setDisplayOnly(readOnly);
        heroBottomRadio_.setDisplayOnly(readOnly);

        editBtn_.setVisible(readOnly);
        saveBtn_.setVisible(!readOnly);
        cancelBtn_.setVisible(!readOnly);
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
        return editing_ && originalSettings_ != null
                && !settingsFromFields().equals(originalSettings_);
    }

    @Override
    public boolean isEditing() {
        return editing_;
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
        if (!editing_) return;
        boolean valid = validatables_.stream().allMatch(DDValidatable::isValidData);
        if (valid) valid = !settingsFromFields().equals(originalSettings_);
        saveBtn_.setEnabled(valid);
    }
}
