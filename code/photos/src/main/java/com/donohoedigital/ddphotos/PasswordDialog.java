package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.PasswordsFile;
import com.donohoedigital.ddphotos.config.PasswordsFileException;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;

/**
 * Edits the {@code passwords.yaml} file's {@code key} plus one password/hint pair — the
 * site-wide entry, or one album's, depending on {@link #PARAM_ALBUM_SLUG}.
 *
 * <p>The passwords file is re-read on every invocation so the dialog never shows stale data.
 * The {@code key} is protected behind an "Edit Key" checkbox because changing it renames every
 * encrypted photo (it seeds the HMAC used for their filenames), forcing a full re-upload.
 */
public class PasswordDialog extends PhotosDialog
{
    private static final Logger logger = LogManager.getLogger(PasswordDialog.class);

    public static final String PARAM_SITE       = "site";
    public static final String PARAM_ALBUM_SLUG = "album-slug";   // null = site mode

    /**
     * The key shipped in the {@code ddphotos init} template.  It is public in the ddphotos
     * repo, so every site scaffolded from it starts out sharing the same secret.
     */
    static final String INIT_KEY = "ddphotos-init-secret-password";

    /** "do not show again" preference key for the key-change warning. */
    private static final String NO_SHOW_KEY_EDIT = "password.key.edit";

    private static final String REGEXP_KEY = "^.+$";

    private static final int PREFERRED_WIDTH = 700;

    private Site           site_;
    private AlbumsFile     albumsFile_;
    private PasswordsFile  passwords_;
    private String         albumSlug_;        // null in site mode
    private boolean        initKeyReplaced_;

    private String originalPassword_;
    private String originalHint_;
    private String originalKey_;
    private String savedKey_;                 // stashed while "Edit Key" is off

    private DDTextField keyField_;
    private DDTextField passwordField_;
    private DDTextField hintField_;
    private DDCheckBox  editKeyCheck_;

    // -------------------------------------------------------------------------
    // DialogPhase API
    // -------------------------------------------------------------------------

    @Override
    public JComponent createDialogContents()
    {
        site_      = (Site)   phase_.getObject(PARAM_SITE);
        albumSlug_ = (String) phase_.getObject(PARAM_ALBUM_SLUG);

        loadPasswords();

        originalKey_      = nvl(passwords_ != null ? passwords_.getKey() : null, "");
        originalPassword_ = nvl(currentPassword(), "");
        originalHint_     = nvl(currentHint(), "");
        savedKey_         = originalKey_;

        keyField_ = new DDTextField("passwordkey", STYLE);
        keyField_.setTextLengthLimit(200);
        keyField_.setText(originalKey_);
        keyField_.setEnabled(false);
        // Only enforce a value while the field is editable; otherwise a file with no key yet
        // would show as invalid on a screen the user can't act on.
        keyField_.setCustomValidator(text -> !isEditingKey() || !text.isBlank());
        keyField_.setRegExp(REGEXP_KEY);

        passwordField_ = new DDTextField("passwordvalue", STYLE);
        passwordField_.setTextLengthLimit(200);
        passwordField_.setText(originalPassword_);
        // Blank (clears the password) or at least photogen's minimum.
        passwordField_.setRegExp("^(.{" + PasswordsFile.MIN_PASSWORD_LENGTH + ",})?$");

        hintField_ = new DDTextField("passwordhint", STYLE);
        hintField_.setTextLengthLimit(200);
        hintField_.setText(originalHint_);
        hintField_.setRegExp("^.*$");

        editKeyCheck_ = new DDCheckBox("editpasswordkey", STYLE);
        editKeyCheck_.setSelected(false);
        editKeyCheck_.addActionListener(_ -> onEditKeyToggled());

        GridBagForm form = GridBagForm.dialog(STYLE)
                .row("passwordkey",   keyField_,      editKeyCheck_)
                .row("passwordvalue", passwordField_, null)
                .row("passwordhint",  hintField_,     null);

        return wrapWithInstructions("passwordinstruct", instructions(), form.panel(), PREFERRED_WIDTH);
    }

    @Override
    protected void opened()
    {
        super.opened();
        // Deferred to opened() so the notice appears over the dialog rather than behind it.
        if (initKeyReplaced_) {
            EngineUtils.displayInformationDialog(context_,
                    PropertyConfig.getMessage("msg.password.initkey.replaced"),
                    "msg.windowtitle.passwordKey", null);
        }
        checkButtons();
    }

    @Override
    protected Component getFocusComponent()
    {
        return passwordField_;
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
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Re-reads the passwords file from disk (the design calls for fresh data on every
     * invocation), creating an in-memory one when the site has none yet.
     */
    private void loadPasswords()
    {
        if (site_ == null) return;
        albumsFile_ = site_.getOrCreateAlbumsFile();
        if (albumsFile_ == null) return;

        albumsFile_.reloadPasswordsFile();
        passwords_ = albumsFile_.getOrCreatePasswordsFile();
        if (passwords_ == null) return;

        if (INIT_KEY.equals(passwords_.getKey())) {
            // Shared demo key from `ddphotos init` — replace it and persist immediately, so it
            // is gone even if the user cancels out of this dialog.
            passwords_.setKey(PasswordsFile.generateKey(siteId()));
            saveFiles();
            initKeyReplaced_ = true;
        } else if (!passwords_.hasKey()) {
            // A new file needs a key before any password can be saved; give it one up front so
            // the user never meets the "key is required" validation error.
            passwords_.setKey(PasswordsFile.generateKey(siteId()));
        }
    }

    private String currentPassword()
    {
        if (passwords_ == null) return null;
        return albumSlug_ == null ? passwords_.getSitePassword() : passwords_.getAlbumPassword(albumSlug_);
    }

    private String currentHint()
    {
        if (passwords_ == null) return null;
        return albumSlug_ == null ? passwords_.getSiteHint() : passwords_.getAlbumHint(albumSlug_);
    }

    private String instructions()
    {
        if (albumSlug_ != null) {
            boolean siteProtected = passwords_ != null && passwords_.isSiteProtected();
            return PropertyConfig.getMessage(
                    siteProtected ? "msg.password.instructions.album.sitewide"
                                  : "msg.password.instructions.album",
                    albumSlug_);
        }
        return PropertyConfig.getMessage("msg.password.instructions.site");
    }

    private String siteId()
    {
        return albumsFile_ != null && albumsFile_.getSettings() != null
                ? albumsFile_.getSettings().getId() : null;
    }

    // -------------------------------------------------------------------------
    // Edit Key checkbox
    // -------------------------------------------------------------------------

    private boolean isEditingKey()
    {
        return editKeyCheck_ != null && editKeyCheck_.isSelected();
    }

    /**
     * Mirrors SiteDialog's config-override checkbox: toggle the field, stash/restore its value,
     * and re-trigger validation since the field's rule depends on the checkbox state.
     */
    private void onEditKeyToggled()
    {
        boolean on = isEditingKey();
        keyField_.setEnabled(on);
        if (on) {
            savedKey_ = keyField_.getText().trim();
            EngineUtils.displayInformationDialog(context_,
                    PropertyConfig.getMessage("msg.password.keychange.warning"),
                    "msg.windowtitle.passwordKey", NO_SHOW_KEY_EDIT, "noshowkeywarn");
        } else {
            keyField_.setText(savedKey_);
        }
        keyField_.setRegExp(REGEXP_KEY);   // re-trigger: rule depends on the checkbox
        checkButtons();
    }

    // -------------------------------------------------------------------------
    // Button state
    // -------------------------------------------------------------------------

    @Override
    protected void checkButtons()
    {
        boolean valid = validatables_.stream().allMatch(DDValidatable::isValidData);
        if (valid) valid = isDirty();
        if (okayButton_ != null) okayButton_.setEnabled(valid);
    }

    /** True once any field differs from what was loaded — nothing to save otherwise. */
    private boolean isDirty()
    {
        if (passwords_ == null) return false;
        if (!passwordField_.getText().trim().equals(originalPassword_)) return true;
        if (!hintField_.getText().trim().equals(originalHint_)) return true;
        return isEditingKey() && !keyField_.getText().trim().equals(originalKey_);
    }

    // -------------------------------------------------------------------------
    // Apply
    // -------------------------------------------------------------------------

    private void apply()
    {
        if (passwords_ == null) return;

        if (isEditingKey()) {
            passwords_.setKey(keyField_.getText().trim());
        }

        String password = passwordField_.getText().trim();
        String hint     = hintField_.getText().trim();

        // A blank password removes the entry entirely (site block or album entry), so the
        // album falls back to the site password and no orphaned hint is left behind.
        if (albumSlug_ == null) {
            passwords_.setSitePassword(password);
            passwords_.setSiteHint(hint);
        } else {
            passwords_.setAlbumPassword(albumSlug_, password);
            passwords_.setAlbumHint(albumSlug_, hint);
        }

        saveFiles();
    }

    private void saveFiles()
    {
        try {
            albumsFile_.savePasswordsFile();
        } catch (PasswordsFileException e) {
            logger.error("Failed to save passwords file: {}{}",
                    albumsFile_.resolvePasswordsPath(), Utils.formatExceptionText(e));
            EngineUtils.displayErrorDialog(context_, e.getMessage(), "msg.windowtitle.saveError", null);
            return;
        }

        // Creating the passwords file defaults settings.passwords, which lives in albums.yaml.
        if (albumsFile_.isPasswordsSettingUnsaved()) {
            try {
                site_.saveAlbumsFile();
            } catch (AlbumsFileException e) {
                logger.error("Failed to save albums file: {}{}",
                        site_.getAlbumsFilePath(), Utils.formatExceptionText(e));
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
