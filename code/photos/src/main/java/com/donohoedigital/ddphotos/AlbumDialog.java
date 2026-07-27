package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;

public class AlbumDialog extends PhotosDialog
{
    private static final Logger logger = LogManager.getLogger(AlbumDialog.class);

    public static final String PARAM_SITE = "site";

    private static final int PREFERRED_WIDTH = 500;

    private Site site_;

    private DDTextField slugField_;
    private DDTextField nameField_;

    // -------------------------------------------------------------------------
    // DialogPhase API
    // -------------------------------------------------------------------------

    @Override
    public JComponent createDialogContents()
    {
        site_ = (Site) phase_.getObject(PARAM_SITE);
        AlbumsFile af = site_ != null ? site_.getOrCreateAlbumsFile() : null;

        slugField_ = new DDTextField("albumslug", STYLE);
        slugField_.setRegExp("^[a-zA-Z0-9][a-zA-Z0-9_-]*$");
        slugField_.setTextLengthLimit(64);
        slugField_.setCustomValidator(text -> {
            if (af == null) return true;
            return af.getAlbums().stream().noneMatch(a -> text.equals(a.getSlug()));
        });

        nameField_ = new DDTextField("albumname", STYLE);
        nameField_.setRegExp(".+");
        nameField_.setTextLengthLimit(200);

        GridBagForm form = GridBagForm.dialog(STYLE)
                .row("albumslug", slugField_, null)
                .row("albumname", nameField_, null);

        return wrapWithInstructions("addalbuminstruct",
                PropertyConfig.getMessage("msg.addalbum.instructions"), form.panel(), PREFERRED_WIDTH);
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
        return slugField_;
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
        if (okayButton_ != null) okayButton_.setEnabled(valid);
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
        entry.setName(nameField_.getText().trim());
        entry.setSource(PropertyConfig.getMessage("msg.addalbum.source.placeholder"));
        af.getAlbums().add(entry);

        try {
            site_.saveAlbumsFile();
        } catch (AlbumsFileException e) {
            logger.error("Failed to save albums file: {}", site_.getAlbumsFilePath(), e);
            PhotosUtils.showSaveError(context_, site_.getAlbumsFilePath(), e);
        }
    }
}
