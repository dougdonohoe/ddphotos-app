package com.donohoedigital.ddphotos.config;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class CssFileTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Matches infra/photos/donohoe/custom.css, which has no trailing newline. */
    private static final String SAMPLE =
            "/* 25th.jpg is odd size, so change it to fit */\n" +
            ".album-card[data-slug='25th'] img {\n" +
            "  object-fit: contain;\n" +
            "}";

    // ── round trip ──────────────────────────────────────────────────────────

    @Test
    public void roundTrip_isByteExact() throws Exception {
        Path f = write(SAMPLE + "\n");
        new CssFile(f).load().save();
        assertEquals(SAMPLE + "\n", read(f));
    }

    @Test
    public void roundTrip_noTrailingNewline() throws Exception {
        Path f = write(SAMPLE);
        new CssFile(f).load().save();
        assertEquals(SAMPLE, read(f));
    }

    @Test
    public void roundTrip_emptyFile() throws Exception {
        Path f = write("");
        CssFile css = new CssFile(f).load();
        assertTrue(css.isEmpty());
        css.save();
        assertEquals("", read(f));
    }

    /**
     * A CRLF file stays CRLF.  Swing normalizes line endings the moment the text lands in the
     * editor, so the model normalizes too and restores the file's own separator on the way out.
     */
    @Test
    public void roundTrip_preservesCrlf() throws Exception {
        Path f = write("a {\r\n  color: red;\r\n}\r\n");
        CssFile css = new CssFile(f).load();
        assertEquals("a {\n  color: red;\n}\n", css.getContent());

        css.setContent(css.getContent().replace("red", "blue"));
        css.save();
        assertEquals("a {\r\n  color: blue;\r\n}\r\n", read(f));
    }

    @Test
    public void setContent_normalizesCrlf() throws Exception {
        Path f = write(SAMPLE + "\n");
        CssFile css = new CssFile(f).load();
        css.setContent("a {\r\n}\r\n");
        assertEquals("a {\n}\n", css.getContent());
    }

    @Test
    public void setContent_nullIsEmpty() throws Exception {
        CssFile css = absent();
        css.setContent(null);
        assertEquals("", css.getContent());
        assertTrue(css.isEmpty());
    }

    // ── load ────────────────────────────────────────────────────────────────

    @Test
    public void load_readsContentVerbatim() throws Exception {
        CssFile css = new CssFile(write(SAMPLE)).load();
        assertTrue(css.existsOnDisk());
        assertEquals(SAMPLE, css.getContent());
        assertFalse(css.isEmpty());
    }

    @Test
    public void load_absentFileGivesEmptyModel() throws Exception {
        CssFile css = absent();
        assertFalse(css.existsOnDisk());
        assertEquals("", css.getContent());
        assertTrue(css.isEmpty());
    }

    @Test
    public void loadInternal_reportsUnreadableFile() throws Exception {
        Path dir = tmp.newFolder().toPath();
        // A directory where a file is expected: readString fails, and the message names the path.
        Files.createDirectory(dir.resolve(CssFile.FILE_NAME));
        try {
            new CssFile(dir.resolve(CssFile.FILE_NAME)).loadInternal();
            fail("expected TextFileException");
        } catch (TextFileException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("read "));
        }
    }

    // ── save guards ─────────────────────────────────────────────────────────

    @Test
    public void save_doesNotCreateFileThatWasAbsent() throws Exception {
        CssFile css = absent();
        css.setContent("a { color: red; }");
        css.save();
        assertFalse(Files.exists(css.getPath()));
        assertFalse(css.existsOnDisk());
    }

    @Test
    public void saveOrCreate_doesNothingWhenEmpty() throws Exception {
        CssFile css = absent();
        css.saveOrCreate();
        assertFalse(Files.exists(css.getPath()));
    }

    /** Whitespace-only content is "nothing to say" too - it must not create a file. */
    @Test
    public void saveOrCreate_doesNothingWhenBlank() throws Exception {
        CssFile css = absent();
        css.setContent("  \n\n  ");
        css.saveOrCreate();
        assertFalse(Files.exists(css.getPath()));
    }

    @Test
    public void saveOrCreate_writesNewFileWithTrailingNewline() throws Exception {
        CssFile css = absent();
        css.setContent("a { color: red; }");
        css.saveOrCreate();

        assertTrue(Files.exists(css.getPath()));
        assertTrue(css.existsOnDisk());
        assertEquals("a { color: red; }\n", read(css.getPath()));
        // The model matches what landed on disk, so a re-save is a no-op rather than a change.
        assertEquals("a { color: red; }\n", css.getContent());
    }

    /** Emptying an existing file writes the empty file; the file (and settings.css) stay put. */
    @Test
    public void saveOrCreate_writesEmptyContentToExistingFile() throws Exception {
        Path f = write(SAMPLE + "\n");
        CssFile css = new CssFile(f).load();
        css.setContent("");
        css.saveOrCreate();

        assertTrue(Files.exists(f));
        assertEquals("", read(f));
    }

    @Test
    public void save_writesEditedContent() throws Exception {
        Path f = write(SAMPLE + "\n");
        CssFile css = new CssFile(f).load();
        css.setContent(".album-card { object-fit: cover; }\n");
        css.save();
        assertEquals(".album-card { object-fit: cover; }\n", read(f));
    }

    // ── real files ──────────────────────────────────────────────────────────

    /**
     * Round-trips the real stylesheets found on this machine, asserting the saved copy is
     * byte-for-byte identical.  Each file is copied into the temp folder first - the originals are
     * never written to.  Skipped when none are present (CI).
     */
    @Test
    public void roundTripRealFiles() throws Exception {
        Path[] files = {
                Paths.get("/Users/donohoe/work/infra/photos/donohoe/custom.css"),
                Paths.get("/Users/donohoe/work/infra/photos/manly-man/custom.css"),
        };

        boolean anyExists = false;
        for (Path p : files) anyExists |= Files.exists(p);
        Assume.assumeTrue("Skipping real-file round-trip: none of the source files found (CI?)", anyExists);

        for (Path originalPath : files) {
            if (!Files.exists(originalPath)) {
                System.out.println("[SKIP] " + originalPath + " not found");
                continue;
            }
            Path copy = tmp.newFolder().toPath().resolve(CssFile.FILE_NAME);
            Files.copy(originalPath, copy);
            String original = read(copy);

            new CssFile(copy).load().save();

            assertEquals("round-trip differs: " + originalPath, original, read(copy));
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Path write(String content) throws Exception {
        Path f = tmp.newFolder().toPath().resolve(CssFile.FILE_NAME);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** A CssFile pointing at a path that does not exist. */
    private CssFile absent() throws Exception {
        return new CssFile(tmp.newFolder().toPath().resolve(CssFile.FILE_NAME)).load();
    }
}
