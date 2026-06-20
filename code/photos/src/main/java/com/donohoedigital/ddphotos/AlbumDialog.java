package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.ddphotos.config.AlbumEntry;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.AlbumsFileException;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.gui.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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

        DDPanel form = new DDPanel();
        form.setLayout(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 8, 4, 8));

        int row = 0;
        row = addFieldRow(form, "albumslug", slugField_, null, row);
              addFieldRow(form, "albumname", nameField_, null, row);

        return wrapWithInstructions("addalbuminstruct",
                PropertyConfig.getMessage("msg.addalbum.instructions"), form, PREFERRED_WIDTH);
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
            logger.error("Failed to save albums file: {}{}", site_.getAlbumsFilePath(), Utils.formatExceptionText(e));
            EngineUtils.displayErrorDialog(context_, e.getMessage(), "msg.windowtitle.saveError", null);
        }
    }
}
