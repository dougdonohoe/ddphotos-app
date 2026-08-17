package com.donohoedigital.ddphotos.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class PhotogenFileTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Representative content: entries, a subfolder placeholder, blank lines, and comments. */
    private static final String SAMPLE =
            """
            # Uganda captions
            img_148_d Our first game drive.
            img_179_d A Ugandan kob (aka an antelope).

            # gorillas below
            img_653_d This is Rwansigazi, the dominant silverback.
            subfolder
            """;

    // ── round-trip tests ────────────────────────────────────────────────────

    @Test
    public void roundTrip_isByteExact() throws Exception {
        Path dir = write(SAMPLE);
        PhotogenFile pf = new PhotogenFile(dir).load();
        pf.save();
        assertEquals("unchanged round-trip must be byte-for-byte identical", SAMPLE, read(dir));
    }

    @Test
    public void roundTrip_noTrailingNewline() throws Exception {
        String noNl = "img_1 One\nimg_2 Two";
        Path dir = write(noNl);
        new PhotogenFile(dir).load().save();
        assertEquals(noNl, read(dir));
    }

    @Test
    public void roundTrip_emptyFile() throws Exception {
        Path dir = write("");
        new PhotogenFile(dir).load().save();
        assertEquals("", read(dir));
    }

    // ── lookup tests ────────────────────────────────────────────────────────

    @Test
    public void getCaption_matchesWithAndWithoutExtension() throws Exception {
        Path dir = write("img_148_d A caption.\n");
        PhotogenFile pf = new PhotogenFile(dir).load();
        assertEquals("A caption.", pf.getCaption("img_148_d"));
        assertEquals("A caption.", pf.getCaption("img_148_d.jpg"));
        assertEquals("A caption.", pf.getCaption("IMG_148_D.JPG")); // case-insensitive
        assertTrue(pf.hasEntry("img_148_d"));
        assertNull(pf.getCaption("nope"));
        assertFalse(pf.hasEntry("nope"));
    }

    @Test
    public void getCaption_matchesVideoWithAndWithoutExtension() throws Exception {
        Path dir = write("clip A caption.\n");
        PhotogenFile pf = new PhotogenFile(dir).load();
        assertEquals("A caption.", pf.getCaption("clip"));
        assertEquals("A caption.", pf.getCaption("clip.mov"));
        assertEquals("A caption.", pf.getCaption("CLIP.MOV"));
        assertEquals("A caption.", pf.getCaption("clip.mp4"));
        assertTrue(pf.hasEntry("clip.m4v"));
    }

    @Test
    public void getCaption_subfolderPlaceholderHasEmptyCaption() throws Exception {
        Path dir = write("subfolder\n");
        PhotogenFile pf = new PhotogenFile(dir).load();
        assertTrue(pf.hasEntry("subfolder"));
        assertEquals("", pf.getCaption("subfolder"));
    }

    // ── quoted-name tests ───────────────────────────────────────────────────

    @Test
    public void getCaption_quotedNameWithSpaces() throws Exception {
        Path dir = write("\"Chicago 2009 001.jpg\" Millennium Park.\n");
        PhotogenFile pf = new PhotogenFile(dir).load();
        assertEquals(List.of("Chicago 2009 001.jpg"), pf.getEntryKeys());
        assertEquals("Millennium Park.", pf.getCaption("Chicago 2009 001.jpg"));
        assertEquals("Millennium Park.", pf.getCaption("chicago 2009 001")); // ext/case-insensitive
    }

    @Test
    public void getCaption_quotedNameWithNoCaption() throws Exception {
        Path dir = write("\"Chicago 2009\"\n");
        PhotogenFile pf = new PhotogenFile(dir).load();
        assertTrue(pf.hasEntry("Chicago 2009"));
        assertEquals("", pf.getCaption("Chicago 2009"));
    }

    @Test
    public void parse_unterminatedQuoteFallsBackToFirstSpaceSplit() throws Exception {
        Path dir = write("\"Chicago 2009.jpg A caption.\n");
        PhotogenFile pf = new PhotogenFile(dir).load();
        assertEquals(List.of("\"Chicago"), pf.getEntryKeys());
        assertEquals("2009.jpg A caption.", pf.getCaption("\"Chicago"));
    }

    @Test
    public void setCaption_quotesOnlyNamesContainingSpaces() throws Exception {
        Path dir = write("img_1 One\n");
        PhotogenFile pf = new PhotogenFile(dir).load();
        pf.setCaption("Chicago 2009 001.jpg", "Millennium Park.");
        pf.setCaption("img_2", "Two");
        pf.setCaption("Chicago 2009 002.jpg", ""); // no caption
        pf.save();
        assertEquals("""
                     img_1 One
                     "Chicago 2009 001.jpg" Millennium Park.
                     img_2 Two
                     "Chicago 2009 002.jpg"
                     """, read(dir));
    }

    @Test
    public void quotedEntry_survivesEditRoundTrip() throws Exception {
        Path dir = write("\"Chicago 2009 001.jpg\" Millennium Park.\n\"Chicago 2009 002.jpg\" The Bean.\n");
        PhotogenFile pf = new PhotogenFile(dir).load();
        pf.setCaption("chicago 2009 002", "Cloud Gate.");
        pf.save();
        assertEquals("\"Chicago 2009 001.jpg\" Millennium Park.\n\"Chicago 2009 002.jpg\" Cloud Gate.\n",
                     read(dir));
    }

    // ── mutation tests ──────────────────────────────────────────────────────

    @Test
    public void setCaption_editsExistingLeavingCommentsAndBlanksUntouched() throws Exception {
        Path dir = write(SAMPLE);
        PhotogenFile pf = new PhotogenFile(dir).load();
        pf.setCaption("img_179_d", "An updated antelope caption.");
        pf.save();

        String out = read(dir);
        assertTrue(out.contains("img_179_d An updated antelope caption."));
        assertFalse(out.contains("A Ugandan kob"));
        // surrounding structure preserved exactly
        assertTrue(out.contains("# Uganda captions"));
        assertTrue(out.contains("# gorillas below"));
        assertTrue(out.contains("\n\n# gorillas below")); // blank line preserved
        assertEquals("Our first game drive.", new PhotogenFile(dir).load().getCaption("img_148_d"));
    }

    @Test
    public void setCaption_appendsNewEntry() throws Exception {
        Path dir = write("img_1 One\n");
        PhotogenFile pf = new PhotogenFile(dir).load();
        pf.setCaption("img_2", "Two");
        pf.save();
        assertEquals("img_1 One\nimg_2 Two\n", read(dir));
    }

    @Test
    public void removeEntry_dropsMatchingLine() throws Exception {
        Path dir = write("img_1 One\nimg_2 Two\nimg_3 Three\n");
        PhotogenFile pf = new PhotogenFile(dir).load();
        pf.removeEntry("img_2.jpg"); // extension-insensitive
        pf.save();
        assertEquals("img_1 One\nimg_3 Three\n", read(dir));
    }

    @Test
    public void getEntryKeys_inFileOrder() throws Exception {
        Path dir = write(SAMPLE);
        List<String> keys = new PhotogenFile(dir).load().getEntryKeys();
        assertEquals(List.of("img_148_d", "img_179_d", "img_653_d", "subfolder"), keys);
    }

    @Test
    public void setEntryOrder_reordersEntriesKeepingAnchors() throws Exception {
        Path dir = write("img_1 One\nimg_2 Two\nimg_3 Three\n");
        PhotogenFile pf = new PhotogenFile(dir).load();
        pf.setEntryOrder(List.of("img_3", "img_1", "img_2"));
        pf.save();
        assertEquals("img_3 Three\nimg_1 One\nimg_2 Two\n", read(dir));
    }

    // ── save-guard test ─────────────────────────────────────────────────────

    @Test
    public void save_doesNotCreateFileThatWasAbsent() throws Exception {
        Path dir = tmp.newFolder("empty").toPath();
        PhotogenFile pf = new PhotogenFile(dir).load();
        assertFalse(pf.existsOnDisk());
        pf.setCaption("img_1", "One"); // mutate in memory
        pf.save();
        assertFalse("save() must not create an absent photogen.txt", Files.exists(pf.getPath()));
    }

    // ── saveOrCreate tests ──────────────────────────────────────────────────

    @Test
    public void saveOrCreate_createsFileWhenAbsentWithContent() throws Exception {
        Path dir = tmp.newFolder("new").toPath();
        PhotogenFile pf = new PhotogenFile(dir).load();
        assertFalse(pf.existsOnDisk());
        pf.setCaption("img_1", "One");
        pf.saveOrCreate();
        assertTrue(Files.exists(pf.getPath()));
        assertEquals("img_1 One\n", read(dir));
    }

    @Test
    public void saveOrCreate_doesNothingWhenAbsentAndEmpty() throws Exception {
        Path dir = tmp.newFolder("empty").toPath();
        PhotogenFile pf = new PhotogenFile(dir).load();
        pf.saveOrCreate();
        assertFalse("empty absent folder must not gain a photogen.txt", Files.exists(pf.getPath()));
    }

    @Test
    public void saveOrCreate_isByteExactForUnchangedExistingFile() throws Exception {
        Path dir = write(SAMPLE);
        new PhotogenFile(dir).load().saveOrCreate();
        assertEquals(SAMPLE, read(dir));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Path write(String content) throws Exception {
        Path dir = tmp.newFolder().toPath();
        Files.writeString(dir.resolve(PhotogenFile.FILE_NAME), content, StandardCharsets.UTF_8);
        return dir;
    }

    private String read(Path dir) throws Exception {
        return Files.readString(dir.resolve(PhotogenFile.FILE_NAME), StandardCharsets.UTF_8);
    }
}
