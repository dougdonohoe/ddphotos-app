package com.donohoedigital.ddphotos;

import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.gui.*;

import javax.swing.*;
import java.awt.*;

/**
 * Chooses which commands {@link PublishController} runs for one site - the checkboxes for
 * {@code photogen} and {@code build}, and the target the run ends with.
 *
 * <p>Unlike the other dialogs there is no Cancel: {@link OptionBoolean} and {@link OptionRadio}
 * write their preference the moment they are clicked, so by the time any Cancel could be pressed
 * the choice is already stored.  The dialog says as much and offers a single Close button rather
 * than pretending the change could still be taken back.
 *
 * <p>Closing it also marks the site as configured, which is what un-grays the Publish menu item -
 * see {@link PublishSettings#isConfigured}.  The Publish button is a shortcut past that menu for
 * the common case of setting this up and publishing straight away; the run itself is started by
 * the caller once this dialog is gone - see {@code PhotosBasePhase.doPublishSettings}.
 */
public class PublishSettingsDialog extends PhotosDialog
{
    public static final String PARAM_SITE = "site";

    /** Button that asks the caller to publish once this dialog closes. */
    public static final String BUTTON_PUBLISH = "publishnow";

    private static final int PREFERRED_WIDTH = 620;

    /** Indent under the Export radio, so the two uploaders read as belonging to it. */
    private static final int UPLOADER_INDENT = 24;

    private Site site_;

    private OptionRadio export_;
    private OptionRadio wrangler_;
    private OptionRadio surge_;

    // -------------------------------------------------------------------------
    // DialogPhase API
    // -------------------------------------------------------------------------

    @Override
    public JComponent createDialogContents()
    {
        site_ = (Site) phase_.getObject(PARAM_SITE);

        String node = PublishSettings.prefsNode(site_);
        TypedHashMap dummy = new TypedHashMap();   // the widgets persist to prefs, not to a map

        OptionBoolean photogen = new OptionBoolean(node, PublishSettings.OPT_PHOTOGEN, STYLE, dummy);
        OptionBoolean build = new OptionBoolean(node, PublishSettings.OPT_BUILD, STYLE, dummy);

        ButtonGroup targetGroup = new ButtonGroup();
        OptionRadio deploy = radio(node, PublishSettings.KEY_TARGET, PublishSettings.OPT_DEPLOY,
                        targetGroup, PublishSettings.Target.DEPLOY.ordinal(), dummy);
        export_ = radio(node, PublishSettings.KEY_TARGET, PublishSettings.OPT_EXPORT,
                        targetGroup, PublishSettings.Target.EXPORT.ordinal(), dummy);

        ButtonGroup uploaderGroup = new ButtonGroup();
        wrangler_ = radio(node, PublishSettings.KEY_UPLOADER, PublishSettings.OPT_WRANGLER,
                          uploaderGroup, PublishSettings.Uploader.WRANGLER.ordinal(), dummy);
        surge_ = radio(node, PublishSettings.KEY_UPLOADER, PublishSettings.OPT_SURGE,
                       uploaderGroup, PublishSettings.Uploader.SURGE.ordinal(), dummy);

        // The uploaders only apply to an export, and OptionRadio only fires when it becomes the
        // selected one - so both target radios have to be listened to.
        deploy.getRadioButton().addActionListener(_ -> updateUploaderEnabled());
        export_.getRadioButton().addActionListener(_ -> updateUploaderEnabled());
        updateUploaderEnabled();

        GridBagForm form = GridBagForm.dialog(STYLE)
                .row(new DDLabel("publishsteps", STYLE), photogen, null)
                .row(blank(), build, null)
                .row(new DDLabel("publishfinish", STYLE), deploy, null)
                .row(blank(), export_, null)
                .row(blank(), indent(wrangler_), null)
                .row(blank(), indent(surge_), null);

        return wrapWithInstructions("publishsettingsinstruct", instructions(), form.panel(),
                                    PREFERRED_WIDTH);
    }

    @Override
    public boolean processButton(AppButton button)
    {
        // Every choice is already stored; closing at all is what says the user has seen this
        // screen, which is what the Publish menu item waits for.
        PublishSettings.setConfigured(site_);
        // Which button was pressed is how the caller knows whether to publish - publishing from
        // in here would leave this dialog sitting under the run's own dialogs.
        setResult(button);
        removeDialog();
        return true;
    }

    /** Nothing to validate - both buttons are always available. */
    @Override
    protected void checkButtons() {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OptionRadio radio(String node, String prefsKey, String widgetName,
                              ButtonGroup group, int value, TypedHashMap map)
    {
        return new OptionRadio(node, prefsKey, STYLE, map, widgetName, group, value);
    }

    private String instructions()
    {
        return PropertyConfig.getMessage("msg.publish.settings.instructions",
                                         site_ != null ? site_.getDisplayName() : "");
    }

    /** Empty first column, so a row's widget lines up under the one above it. */
    private DDLabel blank()
    {
        return new DDLabel(GuiManager.DEFAULT, STYLE);
    }

    private JComponent indent(OptionRadio radio)
    {
        DDPanel panel = new DDPanel();
        panel.add(radio, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createEmptyBorder(0, UPLOADER_INDENT, 0, 0));
        return panel;
    }

    private void updateUploaderEnabled()
    {
        boolean export = export_.getRadioButton().isSelected();
        wrangler_.setEnabled(export);
        surge_.setEnabled(export);
    }
}
