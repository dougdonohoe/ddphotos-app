package com.donohoedigital.ddphotos.config;

import java.nio.file.Path;

/**
 * In-memory model of a site's {@code site.env} - the shell variables the {@code deploy} command
 * sources to work out where the site is published: {@code RSYNC_HOST} / {@code RSYNC_DEST} for a
 * server, {@code S3_BUCKET} for Amazon S3, and an optional {@code CLOUDFRONT_ID}.
 *
 * <p>{@code deploy} resolves it as {@code --site-env} when that flag is given, otherwise
 * {@code <config-dir>/site.env} (see {@link Site#getSiteEnvPath()}), and fails outright when the
 * resolved file is missing.  {@code ddphotos init} scaffolds one, so most sites already have it.
 *
 * <p>Unlike {@link CssFile} nothing in {@code albums.yaml} points at this file, so there is no
 * setting to keep in step when it is created.
 */
public class SiteEnvFile extends TextFile {

    public static final String FILE_NAME = "site.env";

    /** @param path the {@code site.env} file, which may or may not exist yet. */
    public SiteEnvFile(Path path) {
        super(path);
    }

    @Override
    public SiteEnvFile load() {
        super.load();
        return this;
    }
}
