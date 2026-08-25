package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.base.Version;
import com.donohoedigital.config.PropertyConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.SwingWorker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Tells the user when a newer DD Photos has been released.  There is no in-app updater - the app
 * is installed from an installer on GitHub Releases - so all this does is notice and point at the
 * download links in the README.
 *
 * <p>How it asks: {@code github.com/.../releases/latest} answers a bare {@code HEAD} with a 302 to
 * {@code .../releases/tag/<tag>}, and the tags this project publishes are exactly
 * {@link Version#toString} output (1.0.6, 1.0.0b8).  So the whole check is one request with
 * redirects turned off, and the last path segment of the {@code Location} header handed to
 * {@link Version#parse}.  That is smaller than the JSON API, needs no JSON parser (none is on the
 * classpath), and has no unauthenticated rate limit; like the API, it already skips drafts and
 * prereleases.
 *
 * <p>Nothing here is allowed to get in the user's way.  The request runs on a
 * {@link SwingWorker} so startup never waits on it, and the automatic check says nothing at all
 * when it fails - an offline laptop is not an error the user needs a dialog about.  The manual
 * {@code Help > Check for Updates...} does report a failure, because there someone asked.
 */
public class UpdateCheck {

    private static final Logger logger = LogManager.getLogger(UpdateCheck.class);

    /** Redirects to the newest non-draft, non-prerelease release's tag page. */
    private static final String LATEST_URL = "https://github.com/dougdonohoe/ddphotos-app/releases/latest";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    // Both flags are read and written on the EDT only - checkAtStartup and checkFromMenu are
    // called from there, and SwingWorker.done() runs there too.

    /**
     * The automatic check runs once per launch.  Finishing the setup wizard re-enters
     * {@link PhotosBasePhase}, which builds the editor - and so would ask a second time.
     */
    private static boolean checkedThisSession_;

    /** Set while any check is in flight, so a stabbed menu item does not stack up workers. */
    private static boolean running_;

    private UpdateCheck() {}

    /**
     * The check made when the editor opens.  Silent unless there is something to say: a newer
     * release the user has not already been told about.
     *
     * @param busy true when something else owns the main UI (the wizard, the tour, a publish
     *             run).  Checked when the answer comes back, not when the request goes out - the
     *             user can start a tour while it is in flight.
     */
    public static void checkAtStartup(AppContext context, BooleanSupplier busy) {
        if (checkedThisSession_) return;
        checkedThisSession_ = true;

        fetchAsync(latest -> {
            if (latest == null) return; // offline, firewalled, GitHub down - not worth a dialog

            // Nothing may interrupt the wizard, the tour or a publish run.  Deliberately without
            // recording the version, so the next launch offers the news again.
            if (busy.getAsBoolean()) return;

            if (!latest.isNewerThan(PhotosConstants.VERSION)) return;
            if (latest.toString().equals(lastNotified())) return; // already said so, once is enough

            setLastNotified(latest);
            showAvailable(context, latest);
        });
    }

    /**
     * The check behind {@code Help > Check for Updates...}.  Always reports, and ignores both the
     * once-per-launch and once-per-version guards - someone asked, so answer.
     */
    public static void checkFromMenu(AppContext context) {
        fetchAsync(latest -> {
            if (latest == null) {
                EngineUtils.displayInformationDialog(context,
                        PropertyConfig.getMessage("msg.update.failed"),
                        "msg.windowtitle.checkUpdate", null);
            } else if (latest.isNewerThan(PhotosConstants.VERSION)) {
                // Record it here too: having been shown the news, the user does not need it
                // repeated at the next launch.
                setLastNotified(latest);
                showAvailable(context, latest);
            } else {
                EngineUtils.displayInformationDialog(context,
                        PropertyConfig.getMessage("msg.update.none", PhotosConstants.VERSION),
                        "msg.windowtitle.checkUpdate", null);
            }
        });
    }

    private static void showAvailable(AppContext context, Version latest) {
        EngineUtils.displayInformationDialog(context,
                PropertyConfig.getMessage("msg.update.available", latest, PhotosConstants.VERSION),
                "msg.windowtitle.updateAvailable", null);
    }

    /** Runs {@link #fetchLatest} off the EDT and hands the result (possibly null) back on it. */
    private static void fetchAsync(Consumer<Version> onResult) {
        if (running_) return;
        running_ = true;

        new SwingWorker<Version, Void>() {
            @Override
            protected Version doInBackground() {
                return fetchLatest();
            }

            @Override
            protected void done() {
                running_ = false;
                Version latest;
                try {
                    latest = get();
                } catch (Exception e) {
                    // fetchLatest swallows its own failures, so this is the worker itself going
                    // wrong (interrupted, say) - same outcome either way.
                    logger.debug("update check worker failed: {}", e.toString());
                    latest = null;
                }
                onResult.accept(latest);
            }
        }.execute();
    }

    /**
     * Asks GitHub for the newest release and returns its version.
     *
     * @return the latest released version, or null if it could not be determined for any reason -
     * no network, an unexpected response, a tag that is not a version.  Never throws.
     */
    static Version fetchLatest() {
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER) // the redirect *is* the answer
                .connectTimeout(CONNECT_TIMEOUT)
                .build()) {

            HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_URL))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", PhotosConstants.APP_DISPLAY_NAME + "/" + PhotosConstants.VERSION)
                    .timeout(REQUEST_TIMEOUT)
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            String location = response.headers().firstValue("location").orElse(null);
            if (location == null) {
                logger.info("update check: no redirect from {} (status {})", LATEST_URL, response.statusCode());
                return null;
            }

            Version latest = Version.parse(tagFrom(location));
            if (latest == null) logger.info("update check: unrecognized release tag in {}", location);
            return latest;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        catch (Exception e) {
            // Offline is the common case and is not worth a stack trace in the user's log.
            logger.debug("update check failed: {}", e.toString());
            return null;
        }
    }

    /** The tag out of a {@code .../releases/tag/1.0.6} redirect target. */
    private static String tagFrom(String location) {
        int slash = location.lastIndexOf('/');
        return slash < 0 ? location : location.substring(slash + 1);
    }

    private static String lastNotified() {
        return prefs().get(PhotosConstants.PREFS_KEY_UPDATE_NOTIFIED, null);
    }

    private static void setLastNotified(Version v) {
        prefs().put(PhotosConstants.PREFS_KEY_UPDATE_NOTIFIED, v.toString());
    }

    private static Preferences prefs() {
        return PhotosConstants.getAppPreferences();
    }
}
