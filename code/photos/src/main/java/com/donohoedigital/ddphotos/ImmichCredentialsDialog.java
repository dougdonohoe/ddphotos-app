package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.ImmichCredentialsFile;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.sync.ConnectionTest;
import com.donohoedigital.ddphotos.sync.ImmichClient;
import com.donohoedigital.ddphotos.sync.SyncException;
import com.donohoedigital.gui.DDButton;
import com.donohoedigital.gui.DDHtmlArea;
import com.donohoedigital.gui.DDTextField;
import com.donohoedigital.gui.DDValidatable;
import com.donohoedigital.gui.GuiUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.IOException;

/**
 * Edits a site's {@code immich.env}: the Immich instance URL and API key photogen syncs with.
 * <b>Test</b> checks the values as typed, before they are saved, so a typo is caught here rather
 * than on the next photogen run.
 *
 * <p>The phase result is {@link Boolean#TRUE} when the file was saved; see
 * {@link SyncUi#editCredentials}.
 */
public class ImmichCredentialsDialog extends PhotosDialog
{
    private static final Logger logger = LogManager.getLogger(ImmichCredentialsDialog.class);

    public static final String PARAM_SITE = "site";

    private static final int PREFERRED_WIDTH = 540;
    /** Lines kept free below the fields for the Test result. */
    private static final int RESULT_LINES = 2;
    /** Immich keys are about 43 characters; sized for a little more. */
    private static final String SAMPLE_API_KEY = "oeW00smAhMkRo0GYb7zAAQWr8mUFNvYSxO7P0TIegXXX";

    private ImmichCredentialsFile file_;

    private DDTextField urlField_;
    private DDTextField keyField_;
    private DDButton testBtn_;
    private DDHtmlArea testResult_;

    /** Bumped on every Test and every edit, so a slow answer to an old Test is dropped. */
    private int testGeneration_;

    // -------------------------------------------------------------------------
    // DialogPhase API
    // -------------------------------------------------------------------------

    @Override
    public JComponent createDialogContents()
    {
        Site site = (Site) phase_.getObject(PARAM_SITE);
        AlbumsFile af = site != null ? site.getOrCreateAlbumsFile() : null;
        if (af != null) {
            // Always start from what is on disk: the file may have been edited by hand.
            af.reloadImmichCredentialsFile();
            file_ = af.getImmichCredentialsFile();
        }

        urlField_ = new DDTextField("immichurl", STYLE);
        urlField_.setRegExp(PhotosConstants.REGEXP_URL);
        urlField_.setTextLengthLimit(PhotosConstants.MAX_PATH_LENGTH);

        keyField_ = new DDTextField("immichapikey", STYLE);
        keyField_.setRegExp(PhotosConstants.REGEXP_IMMICH_API_KEY);
        keyField_.setTextLengthLimit(128);
        // Wide enough for a whole key, measured in the field's own font.
        Insets in = keyField_.getInsets();
        GuiUtils.setPreferredWidth(keyField_, keyField_.getFontMetrics(keyField_.getFont()).stringWidth(SAMPLE_API_KEY)
                                              + in.left + in.right + 8);

        testBtn_ = new DDButton("immichtest", STYLE);
        testBtn_.addActionListener(_ -> runTest());

        testResult_ = new DDHtmlArea("immichtestresult", STYLE);
        testResult_.setDisplayOnly(true);
        testResult_.setOpaque(false);
        testResult_.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        if (file_ != null) {
            urlField_.setText(nvl(file_.getUrl()));
            keyField_.setText(nvl(file_.getApiKey()));
        }

        GridBagForm form = GridBagForm.dialog(STYLE)
                .row("immichurl", urlField_, null)
                .row("immichapikey", keyField_, testBtn_)
                .span(testResult_);

        String path = file_ != null ? file_.getPath().toString() : ImmichCredentialsFile.FILE_NAME;
        // The wrapper fixes the dialog's width, so let it grow to whatever the key row needs.
        JPanel panel = form.panel();
        int width = Math.max(PREFERRED_WIDTH, panel.getPreferredSize().width);

        // Permanent room for the Test result, so a message never moves the fields around.  Fixed
        // at the form's width so the HTML wraps rather than asking for one long line.
        Insets fi = panel.getInsets();
        int lineHeight = testResult_.getFontMetrics(testResult_.getFont()).getHeight();
        Insets ri = testResult_.getInsets();
        testResult_.setPreferredSize(new Dimension(width - fi.left - fi.right,
                                                   RESULT_LINES * lineHeight + ri.top + ri.bottom));

        return wrapWithInstructions("immichinstruct",
                PropertyConfig.getMessage("msg.immichcreds.instructions", path), panel, width);
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
        return urlField_.getText().isBlank() ? urlField_ :
                keyField_.getText().isBlank() ? keyField_ : testBtn_;
    }

    @Override
    public boolean processButton(AppButton button)
    {
        if ("save".equals(button.getName()) && !apply()) {
            return true;  // keep the dialog open so the user can fix what failed
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
        if (okayButton_ != null) okayButton_.setEnabled(valid && file_ != null && isChanged());
        testBtn_.setEnabled(valid);
        // An edit makes any shown result stale.
        testGeneration_++;
        testResult_.setText("");
    }

    private boolean isChanged()
    {
        return !file_.existsOnDisk()
                || !urlField_.getText().strip().equals(nvl(file_.getUrl()))
                || !keyField_.getText().strip().equals(nvl(file_.getApiKey()));
    }

    // -------------------------------------------------------------------------
    // Test
    // -------------------------------------------------------------------------

    private void runTest()
    {
        int generation = ++testGeneration_;
        String url = urlField_.getText().strip();
        String key = keyField_.getText().strip();
        testResult_.setText(PropertyConfig.getMessage("msg.immichcreds.testing"));
        testBtn_.setEnabled(false);

        Thread.ofVirtual().name("immich-test").start(() -> {
            String html;
            try {
                ImmichClient client = new ImmichClient(url, key);
                ConnectionTest result = client.test();
                html = result.isComplete()
                        ? PropertyConfig.getMessage("msg.immichcreds.test.ok", PhotosUtils.escapeHtml(client.getBaseUrl()))
                        : PropertyConfig.getMessage("msg.immichcreds.test.missing",
                                                    PhotosUtils.escapeHtml(String.join(", ", result.missingPermissions())));
            } catch (SyncException e) {
                html = PropertyConfig.getMessage("msg.immichcreds.test.failed", PhotosUtils.escapeHtml(e.getMessage()));
            } catch (RuntimeException e) {
                // Anything unexpected must still be reported, or the dialog stays on "Testing...".
                logger.error("Immich connection test failed", e);
                html = PropertyConfig.getMessage("msg.immichcreds.test.failed", PhotosUtils.escapeHtml(e.toString()));
            }
            String finalHtml = html;
            SwingUtilities.invokeLater(() -> {
                if (generation != testGeneration_) return;
                testResult_.setText(finalHtml);
                testBtn_.setEnabled(validatables_.stream().allMatch(DDValidatable::isValidData));
            });
        });
    }

    // -------------------------------------------------------------------------
    // Apply
    // -------------------------------------------------------------------------

    /** Saves the file; false (after telling the user) when it could not be written. */
    private boolean apply()
    {
        if (file_ == null) return true;
        file_.setUrl(urlField_.getText());
        file_.setApiKey(keyField_.getText());
        try {
            file_.save();
        } catch (IOException e) {
            logger.error("Failed to save {}", file_.getPath(), e);
            PhotosUtils.showSaveError(context_, file_.getPath(), e);
            return false;
        }
        setResult(Boolean.TRUE);
        return true;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String nvl(String s)
    {
        return s != null ? s : "";
    }
}
