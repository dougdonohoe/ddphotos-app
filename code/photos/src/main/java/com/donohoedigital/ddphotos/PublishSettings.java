package com.donohoedigital.ddphotos;

import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.gui.DDOption;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Which commands {@link PublishController} runs for one site, and where they are remembered.
 *
 * <p>Stored per site, like the runner tabs' flags (see {@code AbstractRunnerPanel.buildFlags}) -
 * one site deploys over rsync while the next pushes to Cloudflare, so the choice belongs to the
 * site, not the app.  {@link PublishSettingsDialog} writes these preferences through the
 * {@code Option*} widgets, which is why the constants below are both the widget names (they key
 * the labels, defaults and help in client.properties) and the preference keys.
 *
 * <p>The two radio groups store the selected {@link Target} / {@link Uploader} as an ordinal,
 * which is what {@link com.donohoedigital.gui.OptionRadio} persists - so the enum order is part
 * of the stored format and constants must only ever be appended.
 */
public record PublishSettings(boolean photogen, boolean build, Target target, Uploader uploader) {

    /** What the last step publishes with: straight to the server, or an export dir to upload. */
    public enum Target { DEPLOY, EXPORT }

    /** Which uploader consumes the export dir when {@link Target#EXPORT} is chosen. */
    public enum Uploader { WRANGLER, SURGE }

    /** One command in a publish run, in the order they can appear. */
    public enum Step { PHOTOGEN, BUILD, DEPLOY, EXPORT, WRANGLER, SURGE }

    // Widget names in client.properties (option.<name>.label/.default/.help) and, for the
    // checkboxes, the preference key each one writes.
    static final String OPT_PHOTOGEN = "publish.photogen";
    static final String OPT_BUILD    = "publish.build";
    static final String OPT_DEPLOY   = "publish.deploy";
    static final String OPT_EXPORT   = "publish.export";
    static final String OPT_WRANGLER = "publish.wrangler";
    static final String OPT_SURGE    = "publish.surge";

    // Preference keys the two radio groups share (OptionRadio stores one int per group).
    static final String KEY_TARGET   = "publish.target";
    static final String KEY_UPLOADER = "publish.uploader";

    /** Set once the user has been through the settings dialog - gates the Publish menu item. */
    private static final String KEY_CONFIGURED = "publish.configured";

    /**
     * The commands to run, in order.  Never empty: a target is always chosen, so Publish always
     * does something even with both checkboxes cleared.
     */
    public List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        if (photogen) steps.add(Step.PHOTOGEN);
        if (build) steps.add(Step.BUILD);
        if (target == Target.DEPLOY) {
            steps.add(Step.DEPLOY);
        } else {
            // Export writes the directory that the uploader then pushes.
            steps.add(Step.EXPORT);
            steps.add(uploader == Uploader.SURGE ? Step.SURGE : Step.WRANGLER);
        }
        return List.copyOf(steps);
    }

    // -------------------------------------------------------------------------
    // Preferences
    // -------------------------------------------------------------------------

    /**
     * The preference node this site's settings live in.  Keyed by the {@code albums.yaml} id
     * (the same identity the runner tabs use), falling back to "TBD" for a site that has none
     * yet - such a site can't run any of these commands anyway.
     */
    static String prefsNode(Site site) {
        return "publish." + (site != null ? site.getIdOrDefault() : "");
    }

    static Preferences prefs(Site site) {
        return DDOption.getOptionPrefs(prefsNode(site));
    }

    /** Reads what the settings dialog last wrote, falling back to the defaults in client.properties. */
    public static PublishSettings load(Site site) {
        Preferences prefs = prefs(site);
        return new PublishSettings(
                prefs.getBoolean(OPT_PHOTOGEN, defaultBoolean(OPT_PHOTOGEN)),
                prefs.getBoolean(OPT_BUILD, defaultBoolean(OPT_BUILD)),
                Target.values()[selected(prefs, KEY_TARGET, OPT_DEPLOY, OPT_EXPORT)],
                Uploader.values()[selected(prefs, KEY_UPLOADER, OPT_WRANGLER, OPT_SURGE)]);
    }

    /**
     * True once the settings dialog has been closed for this site.  The Publish item stays
     * disabled until then - the defaults are a guess at what the site publishes with, and
     * silently deploying somewhere the user never confirmed is not a guess worth making.
     */
    public static boolean isConfigured(Site site) {
        return site != null && prefs(site).getBoolean(KEY_CONFIGURED, false);
    }

    public static void setConfigured(Site site) {
        if (site != null) prefs(site).putBoolean(KEY_CONFIGURED, true);
    }

    /**
     * Mirrors {@link com.donohoedigital.gui.OptionRadio#resetToPrefs()}: the stored ordinal when
     * there is one, otherwise the radio whose {@code option.<name>.default} is true (and the
     * first radio if none is, so an unset property can't leave the group unselected).
     */
    private static int selected(Preferences prefs, String key, String... radioNames) {
        int saved = prefs.getInt(key, -1);
        if (saved >= 0 && saved < radioNames.length) return saved;
        for (int i = 0; i < radioNames.length; i++) {
            if (defaultBoolean(radioNames[i])) return i;
        }
        return 0;
    }

    private static boolean defaultBoolean(String widgetName) {
        return PropertyConfig.getBooleanProperty(DDOption.GetDefaultKey(widgetName), false);
    }
}
