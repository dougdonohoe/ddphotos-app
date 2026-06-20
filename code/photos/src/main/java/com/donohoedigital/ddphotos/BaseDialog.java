package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.base.Utils;
import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.HeroEntry;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class BaseDialog extends PhotosDialog
{
    private static final Logger logger = LogManager.getLogger(BaseDialog.class);

    public static final String PARAM_SITE = "site";
    public static final String PARAM_BASE_NAME = "base-name";  // null = add mode

    private static final int PREFERRED_WIDTH = 520;

    private Site site_;
    private AlbumsFile albumsFile_;    // obtained from site_
    private String     editingName_;   // null in add mode
    private String     originalPath_;  // path before edit, for dirty check

    private DDTextField nameField_;
    private DDTextField pathField_;

    // -------------------------------------------------------------------------
    // DialogPhase API
    // -------------------------------------------------------------------------

    @Override
    public JComponent createDialogContents()
    {
        site_    = (Site) phase_.getObject(PARAM_SITE);
        albumsFile_  = site_ != null ? site_.getOrCreateAlbumsFile() : null;
        editingName_ = (String)   phase_.getObject(PARAM_BASE_NAME);
        originalPath_ = (editingName_ != null && albumsFile_ != null)
                      ? albumsFile_.getBases().get(editingName_) : null;

        nameField_ = new DDTextField("basename", STYLE);
        nameField_.setRegExp("^[a-z0-9][a-z0-9-]*$");
        nameField_.setTextLengthLimit(64);
        nameField_.setCustomValidator(text -> {
            if (albumsFile_ == null) return true;
            // In edit mode the user may keep the same name unchanged
            if (text.equals(editingName_)) return true;
            return !albumsFile_.getBases().containsKey(text);
        });

        pathField_ = new DDTextField("basepath", STYLE);
        pathField_.setRegExp(".+");
        pathField_.setTextLengthLimit(500);
        pathField_.setCustomValidator(text -> Files.isDirectory(resolveBasePath(text)));

        DDButton browseBtn = new DDButton("browsepath", STYLE);
        browseBtn.addActionListener(_ -> browsePath());
        DDIconButtons.makeFolderIcon(browseBtn);

        DDPanel form = new DDPanel();
        form.setLayout(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 8, 4, 8));

        int row = 0;
        row = addFieldRow(form, "basename", nameField_, null,      row);
            addFieldRow(form,   "basepath", pathField_, browseBtn, row);

        // Pre-fill for edit mode
        if (editingName_ != null) {
            nameField_.setText(editingName_);
            if (originalPath_ != null) pathField_.setText(originalPath_);
        }

        return wrapWithInstructions("addbaseinstruct",
                PropertyConfig.getMessage("msg.addbase.instructions"), form, PREFERRED_WIDTH);
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
        return nameField_;
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
        if (valid && editingName_ != null) {
            // In edit mode, require at least one field to have changed
            boolean nameUnchanged = nameField_.getText().trim().equals(editingName_);
            boolean pathUnchanged = pathField_.getText().trim().equals(nvl(originalPath_, ""));
            if (nameUnchanged && pathUnchanged) valid = false;
        }
        if (okayButton_ != null) okayButton_.setEnabled(valid);
    }

    // -------------------------------------------------------------------------
    // Folder picker
    // -------------------------------------------------------------------------

    private void browsePath()
    {
        String current = pathField_.getText().trim();
        String startDir = current.isBlank() ? "" : resolveBasePath(current).toString();
        String chosen = FolderChooser.pickFolder(context_.getFrame(), startDir.isBlank() ? null : startDir);
        if (chosen != null) {
            pathField_.setText(albumsFile_ != null ? albumsFile_.toRelativeBasePath(chosen) : chosen);
        }
    }

    /**
     * Resolves a base path string the same way it will be resolved at runtime:
     * absolute paths as-is, relative paths against the site directory.
     */
    private Path resolveBasePath(String text)
    {
        Path p = Path.of(text.trim());
        if (!p.isAbsolute() && albumsFile_ != null && albumsFile_.getSiteDir() != null) {
            return albumsFile_.getSiteDir().resolve(p).normalize();
        }
        return p;
    }

    // -------------------------------------------------------------------------
    // Apply
    // -------------------------------------------------------------------------

    private void apply()
    {
        String newName = nameField_.getText().trim();
        String newPath = albumsFile_ != null
                ? albumsFile_.toRelativeBasePath(pathField_.getText().trim())
                : pathField_.getText().trim();

        if (editingName_ != null && !editingName_.equals(newName)) {
            // Rename: rebuild bases map preserving insertion order
            Map<String, String> rebuilt = new LinkedHashMap<>();
            albumsFile_.getBases().forEach((k, v) ->
                    rebuilt.put(k.equals(editingName_) ? newName : k,
                                k.equals(editingName_) ? newPath : v));
            albumsFile_.setBases(rebuilt);

            // Propagate rename to album and hero base references
            for (AlbumEntry a : albumsFile_.getAlbums()) {
                if (editingName_.equals(a.getBase())) a.setBase(newName);
            }
            HeroEntry hero = albumsFile_.getSettings().getHero();
            if (hero != null && editingName_.equals(hero.getBase())) hero.setBase(newName);
        } else {
            // Add (editingName_ == null) or path-only edit
            String key = editingName_ != null ? editingName_ : newName;
            albumsFile_.getBases().put(key, newPath);
        }

        if (site_ != null) {
            try {
                site_.saveAlbumsFile();
            } catch (AlbumsFileException e) {
                logger.error("Failed to save albums file: {}{}", site_.getAlbumsFilePath(), Utils.formatExceptionText(e));
                EngineUtils.displayErrorDialog(context_, e.getMessage(), "msg.windowtitle.saveError", null);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String nvl(String s, String fallback) {
        return s != null ? s : fallback;
    }
}
