package com.donohoedigital.ddphotos;

import com.donohoedigital.ddphotos.config.ConfigFile;
import com.donohoedigital.ddphotos.config.FileStamp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Notices when a config file the app is holding gets rewritten by something else - a Claude
 * session editing albums, a vim session fixing a caption - so the app can pick the change up
 * instead of sitting on a stale copy and eventually overwriting it.
 *
 * <p><b>Why polling and not {@link java.nio.file.WatchService}.</b>  On macOS the JDK has no
 * native backend for it: {@code BsdFileSystem.newWatchService()} returns
 * {@code sun.nio.fs.PollingWatchService}, which polls every 2 seconds and can no longer be tuned
 * ({@code SensitivityWatchEventModifier} was removed).  So on the platform this app mostly runs on,
 * {@code WatchService} <em>is</em> a poller - just one that costs a parked thread, only watches
 * whole directories, needs re-registering when a directory is replaced, and would hand us every
 * sibling event to filter, including the {@code .tmp&lt;pid&gt;} files
 * {@link com.donohoedigital.ddphotos.config.AtomicWrite} creates on the way to every save of our
 * own.  Stamping the exact paths we loaded sidesteps all of that: our own writes cannot register
 * as changes because {@link ConfigFile#restamp()} runs as part of saving.
 *
 * <p>Shaped like {@link DockerStatus}: a Swing {@link Timer} on the EDT, the filesystem work on a
 * virtual thread, results back through {@code invokeLater}.  Everything below the stat runs on the
 * EDT, so listeners may touch Swing freely.
 */
public final class ConfigWatcher {

    private static final Logger logger = LogManager.getLogger(ConfigWatcher.class);

    /** Matches what the JDK's own poller uses on macOS; fast enough to feel immediate. */
    private static final int POLL_INTERVAL_MS = 2_000;

    private ConfigWatcher() {}

    /** Told that the watched file has been rewritten by something other than this app. */
    public interface Listener {
        void onFileChangedOnDisk();
    }

    /**
     * A live watch.  Whoever creates one is responsible for closing it.
     *
     * <p>A watch reports any given state of the file exactly once.  Whatever the listener does
     * with it - reload, ask the user and be told no, fail to parse a half-written file - it is not
     * told about that same state again; only a further write brings it back.  That is what keeps a
     * declined prompt from reappearing every two seconds, and it means a listener has nothing to
     * remember between calls.
     */
    public interface Registration {

        /** Stops watching.  Safe to call more than once. */
        void close();
    }

    // -------------------------------------------------------------------------
    // Registry
    // -------------------------------------------------------------------------

    private static final List<Watch> WATCHES = new ArrayList<>();
    private static Timer timer_;

    /**
     * Watches whatever {@code file} currently yields.  A supplier rather than a fixed instance
     * because the model gets replaced out from under the watcher - reloading {@code albums.yaml}
     * builds a new {@link ConfigFile}, and switching sites swaps in another site's altogether.
     * Returning null means "nothing to watch just now", which is normal for a site whose
     * {@code albums.yaml} does not exist yet.
     */
    public static Registration watch(Supplier<ConfigFile> file, Listener listener) {
        Watch w = new Watch(file, listener);
        // Seed from what is there now, so registering never immediately reports a change.
        w.last = w.current();
        WATCHES.add(w);
        startIfNeeded();
        return w;
    }

    private static void startIfNeeded() {
        if (timer_ != null || WATCHES.isEmpty()) return;
        timer_ = new Timer(POLL_INTERVAL_MS, _ -> tick());
        timer_.start();
    }

    private static void stopIfIdle() {
        if (timer_ == null || !WATCHES.isEmpty()) return;
        timer_.stop();
        timer_ = null;
    }

    // -------------------------------------------------------------------------
    // Polling
    // -------------------------------------------------------------------------

    /**
     * True while a listener is running.  A confirmation dialog here is modal by way of a
     * {@code SecondaryLoop} (see {@code BaseFrame.Modal}), which keeps pumping events - so without
     * this the timer would go on firing behind the dialog and stack up prompts about the same
     * change.
     */
    private static boolean busy_;

    private static void tick() {
        if (busy_ || WATCHES.isEmpty()) return;

        // Collect the paths on the EDT, stat them off it: a config file can live on a network
        // share or an external drive, where a stat is not guaranteed to be instant.
        List<Watch> due = new ArrayList<>(WATCHES.size());
        List<Path> paths = new ArrayList<>(WATCHES.size());
        for (Watch w : WATCHES) {
            ConfigFile f = w.current();
            if (f != w.last) {
                // The model was swapped: another site selected, or albums.yaml reloaded into a
                // fresh instance.  Either way the new model is by definition in step with disk,
                // so re-baseline rather than report a change.
                //
                // The exception is a watch that had nothing to compare against.  f differs from
                // w.last, so a null w.last means f is non-null: a file that was not there when we
                // last looked has since arrived, and that is worth reporting.
                boolean appeared = (w.last == null);
                w.last = f;
                w.suppressed = null;
                if (!appeared) continue;

                fire(w, FileStamp.of(f.getPath()));
                return;   // one prompt at a time; the rest are picked up next tick
            }
            if (f == null || f.getPath() == null) continue;
            due.add(w);
            paths.add(f.getPath());
        }
        if (due.isEmpty()) return;

        Thread.ofVirtual().start(() -> {
            List<FileStamp> stamps = new ArrayList<>(paths.size());
            for (Path p : paths) stamps.add(FileStamp.of(p));
            SwingUtilities.invokeLater(() -> report(due, stamps));
        });
    }

    /** Back on the EDT with fresh stamps: decide which watch, if any, has news. */
    private static void report(List<Watch> due, List<FileStamp> stamps) {
        if (busy_) return;
        for (int i = 0; i < due.size(); i++) {
            Watch w = due.get(i);
            // Re-check against the registry: the watch may have been closed, or its model
            // replaced, while the stat was in flight.
            if (!WATCHES.contains(w)) continue;
            ConfigFile f = w.current();
            if (f != w.last || f == null) continue;
            if (!f.isChangedOnDisk()) continue;
            if (stamps.get(i).equals(w.suppressed)) continue;

            logger.info("changed on disk: {}", f.getPath());
            fire(w, stamps.get(i));
            return;   // one at a time
        }
    }

    /**
     * Hands one change to its listener, having first written down the state being reported so the
     * same one is never offered twice.  Recorded up front rather than afterward because the
     * listener runs a modal dialog, and the file could move again while it is up - that later
     * write is news, and must not be swallowed by a suppression recorded after the fact.
     */
    private static void fire(Watch w, FileStamp seen) {
        w.suppressed = seen;
        busy_ = true;
        try {
            w.listener.onFileChangedOnDisk();
        } catch (RuntimeException e) {
            // A listener blowing up must not take the timer down with it, or the app stops
            // noticing changes for the rest of the session with nothing to show for it.
            logger.error("listener failed for {}", pathOf(w), e);
        } finally {
            busy_ = false;
        }
    }

    private static Path pathOf(Watch w) {
        ConfigFile f = w.current();
        return f == null ? null : f.getPath();
    }

    private static final class Watch implements Registration {

        private final Supplier<ConfigFile> file;
        private final Listener listener;

        /** The model seen last tick, so a swapped-out instance re-baselines instead of reporting. */
        private ConfigFile last;

        /** The last on-disk state handed to the listener; not offered a second time. */
        private FileStamp suppressed;

        Watch(Supplier<ConfigFile> file, Listener listener) {
            this.file = file;
            this.listener = listener;
        }

        /**
         * The model to watch right now, or null when there is none to be had.
         *
         * <p>Suppliers reach into the app to find their file, and that can fail: asking a
         * {@link com.donohoedigital.ddphotos.config.Site} for an {@code albums.yaml} it has not
         * loaded yet parses it on the spot, and throws if it is malformed.  Letting that escape
         * would kill the timer for the rest of the session, and it would be thrown afresh every
         * couple of seconds until it did.  Whatever the problem is, it was reported when the app
         * first ran into it - here it just means "nothing to compare against".
         */
        ConfigFile current() {
            try {
                return file.get();
            } catch (RuntimeException e) {
                logger.debug("supplier failed, skipping this watch: {}", e.toString());
                return null;
            }
        }

        @Override
        public void close() {
            WATCHES.remove(this);
            stopIfIdle();
        }
    }
}
