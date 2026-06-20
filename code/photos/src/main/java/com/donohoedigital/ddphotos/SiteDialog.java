package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Utils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.SitesFile;
import com.donohoedigital.ddphotos.config.SitesFileException;
import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class SiteDialog extends PhotosDialog
{
    private static final Logger logger = LogManager.getLogger(SiteDialog.class);

    public static final String PARAM_SITES_FILE           = "sites-file";
    public static final String PARAM_SITE                 = "site";
    public static final String PARAM_INITIAL_DIR          = "initial-dir";
    public static final String PARAM_INITIAL_DISPLAY_NAME = "initial-display-name";

    private static final int PREFERRED_WIDTH = 720;

    private SitesFile sitesFile_;
    private Site siteBeingEdited_;
    private Site originalSite_;

    private DDTextField displayNameField_;
    private DDTextField dirPathField_;
    private DDCheckBox  configOverrideCheck_;
    private DDTextField configPathField_;
    private String      savedConfigPath_ = "";

    // -------------------------------------------------------------------------
    // DialogPhase API
    // -------------------------------------------------------------------------

    @Override
    public JComponent createDialogContents()
    {
        sitesFile_       = (SitesFile) phase_.getObject(PARAM_SITES_FILE);
        siteBeingEdited_ = (Site)  phase_.getObject(PARAM_SITE);

        displayNameField_ = new DDTextField("sitedisplayname", STYLE);
        displayNameField_.setRegExp(".+");
        displayNameField_.setTextLengthLimit(50);

        dirPathField_ = new DDTextField("sitedirpath", STYLE);
        dirPathField_.setCustomValidator(text -> {
            Path path = Path.of(text);
            if (!Files.isDirectory(path)) return false;
            // when no override, <site-dir>/config must contain albums.yaml
            if (configOverrideCheck_ == null || !configOverrideCheck_.isSelected()) {
                return Files.exists(path.resolve("config").resolve("albums.yaml"));
            }
            return true;
        });
        dirPathField_.setRegExp(".+");
        dirPathField_.setTextLengthLimit(500);

        configPathField_ = new DDTextField("siteconfigpath", STYLE);
        configPathField_.setCustomValidator(text -> {
            if (configOverrideCheck_ == null || !configOverrideCheck_.isSelected()) return true;
            if (text.isEmpty()) return false;
            Path p = Path.of(text);
            if (!Files.isDirectory(p) || !Files.exists(p.resolve("albums.yaml"))) return false;
            // reject if same as the default config dir (<siteDir>/config)
            String siteDir = dirPathField_ != null ? dirPathField_.getText().trim() : "";
            return Site.isCustomConfigPath(siteDir, text);
        });
        configPathField_.setRegExp(".*");
        configPathField_.setTextLengthLimit(500);

        // browse buttons created before checkbox so the action listener can reference them
        DDButton browseDirBtn    = new DDButton("browsedirpath",    STYLE);
        DDButton browseConfigBtn = new DDButton("browseconfigpath", STYLE);
        browseDirBtn.addActionListener(   _ -> browseSiteDir());
        browseConfigBtn.addActionListener(_ -> browseFolder(configPathField_));
        DDIconButtons.makeFolderIcon(browseDirBtn);
        DDIconButtons.makeFolderIcon(browseConfigBtn);

        // checkbox — off by default; toggles config field + browse enabled state
        configOverrideCheck_ = new DDCheckBox("configoverride", STYLE);
        configOverrideCheck_.setSelected(false);
        configPathField_.setEnabled(false);
        browseConfigBtn.setEnabled(false);

        configOverrideCheck_.addActionListener(_ -> {
            boolean on = configOverrideCheck_.isSelected();
            configPathField_.setEnabled(on);
            browseConfigBtn.setEnabled(on);
            if (on) {
                configPathField_.setText(savedConfigPath_);
            } else {
                savedConfigPath_ = configPathField_.getText().trim();
                configPathField_.setText("");
            }
            dirPathField_.setRegExp(".+");   // re-trigger: rule depends on override state
            configPathField_.setRegExp(".*"); // re-trigger: required when override is on
            checkButtons();
        });

        // form layout
        DDPanel form = new DDPanel();
        form.setLayout(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 8, 4, 8));

        int row = 0;
        row = addFieldRow(form, "sitedisplayname", displayNameField_, null,         row);
        row = addFieldRow(form, "sitedirpath",     dirPathField_,     browseDirBtn, row);

        // config override row: [checkbox] [textfield] [browse]
        GridBagConstraints cc = new GridBagConstraints();
        cc.gridx = 0; cc.gridy = row;
        cc.anchor = GridBagConstraints.EAST;
        cc.insets = new Insets(4, 4, 4, 8);
        form.add(configOverrideCheck_, cc);

        GridBagConstraints cf = new GridBagConstraints();
        cf.gridx = 1; cf.gridy = row;
        cf.fill = GridBagConstraints.HORIZONTAL;
        cf.weightx = 1.0;
        cf.insets = new Insets(4, 0, 4, 2);
        form.add(configPathField_, cf);

        GridBagConstraints cb = new GridBagConstraints();
        cb.gridx = 2; cb.gridy = row;
        cb.insets = new Insets(4, 2, 4, 4);
        form.add(browseConfigBtn, cb);

        // pre-fill for add mode from wizard
        if (siteBeingEdited_ == null) {
            Path initialDir = (Path) phase_.getObject(PARAM_INITIAL_DIR);
            if (initialDir != null) {
                dirPathField_.setText(initialDir.toString());
            }
            String initialDisplayName = phase_.getString(PARAM_INITIAL_DISPLAY_NAME);
            if (initialDisplayName != null) {
                displayNameField_.setText(initialDisplayName);
            }
        }

        // pre-fill for edit mode
        if (siteBeingEdited_ != null) {
            originalSite_ = new Site(siteBeingEdited_);
            displayNameField_.setText(siteBeingEdited_.getDisplayName());
            dirPathField_.setText(siteBeingEdited_.getDirPath() != null
                    ? siteBeingEdited_.getDirPath() : "");

            String existingConfig = siteBeingEdited_.getConfigPath();
            if (existingConfig != null && !existingConfig.isEmpty()) {
                configOverrideCheck_.setSelected(true);
                configPathField_.setEnabled(true);
                browseConfigBtn.setEnabled(true);
                configPathField_.setText(existingConfig);
            }
        }

        return wrapWithInstructions("sitedialoginstruct",
                PropertyConfig.getMessage("msg.sitedialog.instructions"), form, PREFERRED_WIDTH);
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
        return displayNameField_;
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
    // Button state
    // -------------------------------------------------------------------------

    @Override
    protected void checkButtons()
    {
        boolean valid = validatables_.stream().allMatch(DDValidatable::isValidData);
        if (valid && originalSite_ != null) valid = !siteFromFields().equals(originalSite_);
        if (okayButton_ != null) okayButton_.setEnabled(valid);
    }

    private Site siteFromFields()
    {
        String configPath = configOverrideCheck_.isSelected()
                          ? configPathField_.getText().trim() : null;
        return new Site(
                displayNameField_.getText().trim(),
                dirPathField_.getText().trim(),
                configPath != null && configPath.isEmpty() ? null : configPath);
    }

    // -------------------------------------------------------------------------
    // Folder pickers
    // -------------------------------------------------------------------------

    private void browseSiteDir()
    {
        String chosen = FolderChooser.pickFolder(context_.getFrame(), dirPathField_.getText().trim());
        if (chosen != null) dirPathField_.setText(chosen);
    }

    private void browseFolder(DDTextField target)
    {
        String chosen = FolderChooser.pickFolder(context_.getFrame(), dirPathField_.getText().trim());
        if (chosen != null) target.setText(chosen);
    }

    // -------------------------------------------------------------------------
    // Apply
    // -------------------------------------------------------------------------

    private void apply()
    {
        Site current = siteFromFields();

        if (siteBeingEdited_ != null) {
            siteBeingEdited_.setDisplayName(current.getDisplayName());
            siteBeingEdited_.setDirPath(current.getDirPath());
            siteBeingEdited_.setConfigPath(current.getConfigPath());
            sitesFile_.sortSites();
        } else {
            sitesFile_.addSite(current);
        }

        try {
            sitesFile_.save();
        } catch (SitesFileException e) {
            logger.error("Failed to save sites file: {}{}", sitesFile_.getPath(), Utils.formatExceptionText(e));
            EngineUtils.displayErrorDialog(context_, e.getMessage(), "msg.windowtitle.saveError", null);
        }
    }
}
