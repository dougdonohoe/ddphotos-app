package com.donohoedigital.ddphotos;

import com.donohoedigital.ddphotos.PhotoChooserDialog.Entry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.donohoedigital.ddphotos.PhotoChooserDialog.canGoUp;
import static com.donohoedigital.ddphotos.PhotoChooserDialog.listEntries;
import static org.junit.Assert.*;

/**
 * Unit tests for the photo chooser's listing and navigation rules.  These are the Swing-free part
 * of {@link PhotoChooserDialog}, exercised directly against on-disk fixtures.
 */
public class PhotoChooserTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path dir;

    @Before
    public void setUp() throws IOException {
        dir = tmp.newFolder("photos").toPath();
    }

    private void file(String name) throws IOException {
        assertTrue(dir.resolve(name).toFile().createNewFile());
    }

    private void folder(String name) throws IOException {
        assertTrue(dir.resolve(name).toFile().mkdir());
    }

    private static List<String> names(List<Entry> entries) {
        return entries.stream().map(Entry::name).toList();
    }

    // -- listEntries ---------------------------------------------------------

    @Test
    public void listEntries_putsFoldersBeforeImages() throws IOException {
        file("zebra.jpg");
        folder("alpha");
        file("apple.png");
        folder("zulu");

        assertEquals(List.of("alpha", "zulu", "apple.png", "zebra.jpg"), names(listEntries(dir)));
    }

    @Test
    public void listEntries_sortsCaseInsensitively() throws IOException {
        file("Banana.jpg");
        file("apple.jpg");
        file("Cherry.jpg");

        assertEquals(List.of("apple.jpg", "Banana.jpg", "Cherry.jpg"), names(listEntries(dir)));
    }

    @Test
    public void listEntries_keepsEveryRecognizedImageExtension() throws IOException {
        file("a.jpg");
        file("b.JPEG");
        file("c.png");
        file("d.webp");
        file("e.tif");
        file("f.tiff");
        file("g.heic");
        file("h.heif");

        assertEquals(8, listEntries(dir).size());
    }

    @Test
    public void listEntries_excludesNonImagesAndDotfiles() throws IOException {
        file("keep.jpg");
        file("photogen.txt");
        file("notes.md");
        file(".hidden.jpg");
        file(".DS_Store");

        assertEquals(List.of("keep.jpg"), names(listEntries(dir)));
    }

    @Test
    public void listEntries_marksFoldersAndFiles() throws IOException {
        folder("sub");
        file("pic.jpg");

        List<Entry> entries = listEntries(dir);
        assertTrue(entries.get(0).folder());
        assertFalse(entries.get(1).folder());
        assertEquals(dir.resolve("pic.jpg"), entries.get(1).path());
    }

    @Test
    public void listEntries_returnsEmptyForAMissingDirectory() {
        assertTrue(listEntries(dir.resolve("nope")).isEmpty());
    }

    // -- canGoUp -------------------------------------------------------------

    @Test
    public void canGoUp_isFalseAtTheRoot() {
        assertFalse(canGoUp(dir, dir));
    }

    @Test
    public void canGoUp_isTrueBelowTheRoot() throws IOException {
        Path sub = dir.resolve("sub");
        folder("sub");
        assertTrue(canGoUp(sub, dir));
    }

    @Test
    public void canGoUp_ignoresUnnormalizedRootSpelling() {
        assertFalse(canGoUp(dir, dir.resolve("sub").resolve("..")));
    }

    @Test
    public void canGoUp_isTrueAnywhereWithoutARoot() {
        assertTrue(canGoUp(dir, null));
        assertTrue(canGoUp(dir.getParent(), null));
    }

    @Test
    public void canGoUp_isFalseAtTheFilesystemRoot() {
        assertFalse(canGoUp(dir.getRoot(), null));
        assertFalse(canGoUp(null, null));
    }
}
