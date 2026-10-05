package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.gui.DDButton;
import com.donohoedigital.gui.DDIconButtons;
import com.donohoedigital.gui.DDTextField;
import com.donohoedigital.gui.GuiUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shows the size and location of the thumbnail cache (see {@link Thumbs}) and lets the user
 * clear it.  Opened from File &gt; Thumbnail Cache.
 */
public class ThumbnailCacheDialog extends PhotosDialog
{
    private static final Logger logger = LogManager.getLogger(ThumbnailCacheDialog.class);

    private static final String PHASE_NAME = "ThumbnailCacheDialog";
    private static final int PREFERRED_WIDTH = 560;
    /** The most the dialog widens to fit the cache path; a longer path scrolls in its field. */
    private static final int MAX_WIDTH = 1100;

    private DDTextField sizeField_;
    private DDButton clearBtn_;
    private long size_;

    /** Opens the dialog; returns once it is closed. */
    public static void open(AppContext context)
    {
        if (context == null) return;
        context.processPhaseNow(PHASE_NAME, new TypedHashMap());
    }

    // -------------------------------------------------------------------------
    // DialogPhase API
    // -------------------------------------------------------------------------

    @Override
    public JComponent createDialogContents()
    {
        sizeField_ = displayOnlyField("thumbcachesize");
        clearBtn_ = new DDButton("thumbcacheclear", STYLE);
        clearBtn_.addActionListener(_ -> clear());

        String path = Thumbs.cacheDir().toString();
        DDTextField locationField = displayOnlyField("thumbcachelocation");
        locationField.setText(path);
        locationField.setCaretPosition(0);
        // Wide enough for the whole path, measured in the field's own font.
        Insets in = locationField.getInsets();
        GuiUtils.setPreferredWidth(locationField, locationField.getFontMetrics(locationField.getFont()).stringWidth(path)
                                                  + in.left + in.right + 8);
        DDButton folderBtn = new DDButton("thumbcachefolder", STYLE);
        folderBtn.addActionListener(_ -> showFolder());
        DDIconButtons.makeFolderIcon(folderBtn);

        GridBagForm form = GridBagForm.dialog(STYLE)
                .row("thumbcachesize", sizeField_, clearBtn_)
                .row("thumbcachelocation", locationField, folderBtn);

        refreshSize();

        // The wrapper fixes the dialog's width, so let it grow to whatever the location row needs.
        JPanel panel = form.panel();
        int width = Math.clamp(panel.getPreferredSize().width, PREFERRED_WIDTH, MAX_WIDTH);
        return wrapWithInstructions("thumbcacheinstruct",
                PropertyConfig.getMessage("msg.thumbcache.instructions"), panel, width);
    }

    @Override
    protected Component getFocusComponent()
    {
        return okayButton_;
    }

    @Override
    public boolean processButton(AppButton button)
    {
        removeDialog();
        return true;
    }

    /** Nothing to validate: the only dialog button is OK. */
    @Override
    protected void checkButtons()
    {
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void refreshSize()
    {
        size_ = Thumbs.cacheSize();
        sizeField_.setText(Utils.formatBytes(size_));
        clearBtn_.setEnabled(size_ > 0);
    }

    private void clear()
    {
        if (!EngineUtils.displayConfirmationDialog(context_,
                PropertyConfig.getMessage("msg.confirm.thumbcacheclear", Utils.formatBytes(size_)))) {
            return;
        }
        Thumbs.clearCache();
        refreshSize();
    }

    /**
     * Opens the cache folder in the OS file manager, creating it first: it does not exist until
     * the first thumbnail is written.  See {@link PhotosBasePhase} for why the folder is checked
     * before {@link Utils#openFolder} is trusted.
     */
    private void showFolder()
    {
        Path dir = Thumbs.cacheDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            logger.warn("Failed to create thumbnail cache folder: {}", dir);
        }
        if (!Files.isDirectory(dir) || !Utils.openFolder(dir.toFile())) {
            EngineUtils.displayErrorDialog(context_,
                    PropertyConfig.getMessage("msg.error.openfolder", dir.toString()));
        }
    }

    private DDTextField displayOnlyField(String name)
    {
        DDTextField f = new DDTextField(name, STYLE);
        f.setDisplayOnly(true);
        return f;
    }
}
