package com.donohoedigital.ddphotos.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.Assert.*;

/**
 * {@link ConfigFile}'s own contract, exercised through a bare subclass so it is tested apart from
 * any particular file format.  The real classes' obligation - restamping as part of loading and
 * saving - is covered in their own tests.
 */
public class ConfigFileTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** The minimum a subclass has to provide. */
    private static final class Stub extends ConfigFile {
        private final Path path_;
        Stub(Path path) { path_ = path; }
        @Override public Path getPath() { return path_; }
    }

    private Path write(String name, String content) throws Exception {
        Path p = tmp.getRoot().toPath().resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    /** Rewrites a file so its stamp is unmistakably different. */
    private void touch(Path p, String content) throws Exception {
        Files.writeString(p, content, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(p, FileTime.fromMillis(
                Files.getLastModifiedTime(p).toMillis() + 5_000));
    }

    @Test
    public void unstamped_readsAsChanged() throws Exception {
        Path p = write("a.yaml", "one");
        // Never restamped, so the model has no claim to be in step with the file.
        assertTrue(new Stub(p).isChangedOnDisk());
    }

    @Test
    public void afterRestamp_readsAsUnchanged() throws Exception {
        Path p = write("a.yaml", "one");
        Stub f = new Stub(p);
        f.restamp();
        assertFalse(f.isChangedOnDisk());
    }

    @Test
    public void afterExternalWrite_readsAsChanged() throws Exception {
        Path p = write("a.yaml", "one");
        Stub f = new Stub(p);
        f.restamp();

        touch(p, "two");
        assertTrue(f.isChangedOnDisk());
    }

    @Test
    public void restampAgain_acceptsTheNewState() throws Exception {
        Path p = write("a.yaml", "one");
        Stub f = new Stub(p);
        f.restamp();
        touch(p, "two");
        assertTrue(f.isChangedOnDisk());

        f.restamp();
        assertFalse(f.isChangedOnDisk());
    }

    @Test
    public void deletedFile_readsAsChanged() throws Exception {
        Path p = write("a.yaml", "one");
        Stub f = new Stub(p);
        f.restamp();

        Files.delete(p);
        assertTrue(f.isChangedOnDisk());
    }

    @Test
    public void restampOverAbsentFile_settles() throws Exception {
        // A model whose file has gone must be able to stop reporting the same news forever;
        // this is what keeps a deleted file from being re-reported on every poll.
        Path p = write("a.yaml", "one");
        Stub f = new Stub(p);
        f.restamp();
        Files.delete(p);

        f.restamp();
        assertFalse(f.isChangedOnDisk());
    }

    @Test
    public void appearingFile_readsAsChanged() throws Exception {
        Path p = tmp.getRoot().toPath().resolve("later.yaml");
        Stub f = new Stub(p);
        f.restamp();          // stamped while absent
        assertFalse(f.isChangedOnDisk());

        Files.writeString(p, "now here", StandardCharsets.UTF_8);
        assertTrue(f.isChangedOnDisk());
    }

    @Test
    public void noPath_isNeverChanged() {
        // A model with nowhere to live (a new AlbumsFile that has never been saved) has nothing
        // it could disagree with.
        Stub f = new Stub(null);
        assertFalse(f.isChangedOnDisk());
        f.restamp();
        assertFalse(f.isChangedOnDisk());
    }
}
