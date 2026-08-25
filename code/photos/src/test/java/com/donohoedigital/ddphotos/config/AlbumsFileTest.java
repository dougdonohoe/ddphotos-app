package com.donohoedigital.ddphotos.config;

import com.donohoedigital.base.Utils;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.*;

public class AlbumsFileTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ── load tests ──────────────────────────────────────────────────────────

    @Test
    public void load_valid() throws Exception {
        AlbumsFile af = loadFixture("testdata/albums.yaml");

        AlbumsSettings s = af.getSettings();
        assertEquals("sample", s.getId());
        assertEquals("DD Photos Test", s.getSiteName());
        assertEquals("https://photos.example.com", s.getSiteUrl());
        assertEquals("A test site.", s.getSiteDescription());
        assertEquals("Test User", s.getCopyrightOwner());
        assertEquals(2020, s.getCopyrightYear());
        assertTrue(s.isAllowCrawling());
        assertEquals("descriptions.txt", s.getDescriptions());
        assertEquals("<a href=\"https://example.com\">Test</a>", s.getSiteTitleHtml());
        assertEquals("A subtitle.", s.getSiteSubtitleHtml());

        assertNotNull(s.getHero());
        assertEquals("hero.jpg", s.getHero().getImage());
        assertEquals("t7", s.getHero().getBase());
        assertEquals("center", s.getHero().getCrop());

        Map<String, String> bases = af.getBases();
        assertEquals("/Volumes/T7/Photos", bases.get("t7"));
        assertEquals("/Users/example/Dropbox/Photos", bases.get("dropbox"));

        List<AlbumEntry> albums = af.getAlbums();
        assertEquals(3, albums.size());

        AlbumEntry a = albums.getFirst();
        assertEquals("antarctica", a.getSlug());
        assertEquals("Antarctica", a.getName());
        assertEquals("Photos from the 2004 expedition.", a.getDescription());
        assertEquals("t7", a.getBase());
        assertEquals("2004-Antarctica", a.getSource());
        assertEquals("IMG_001.jpg", a.getCover());
        assertTrue(a.isManualSortOrder());
        assertFalse(a.isRecurse());

        a = albums.get(1);
        assertEquals("nepal", a.getSlug());
        assertEquals("Nepal 2018", a.getName());
        assertEquals("t7", a.getBase());
        assertEquals("2018-Nepal", a.getSource());
        assertNull(a.getCover());
        assertFalse(a.isManualSortOrder());
        assertTrue(a.isRecurse());

        a = albums.get(2);
        assertEquals("localtest", a.getSlug());
        assertNull(a.getBase());
        assertEquals("/tmp/local-test", a.getSource());
    }

    @Test
    public void load_missingFile() {
        try {
            AlbumsFile.load(Paths.get("/nonexistent/albums.yaml"));
            fail("expected AlbumsFileException");
        } catch (AlbumsFileException e) {
            assertTrue(e.getMessage().contains("read"));
        }
    }

    @Test
    public void load_invalidYaml() throws Exception {
        Path bad = tmp.newFile("bad.yaml").toPath();
        Files.writeString(bad, ":\nthis: [is: {not valid", StandardCharsets.UTF_8);
        try {
            AlbumsFile.load(bad);
            fail("expected AlbumsFileException");
        } catch (AlbumsFileException e) {
            assertTrue(e.getMessage().contains("parse"));
        }
    }

    @Test
    public void load_emptyFile() throws Exception {
        Path empty = tmp.newFile("empty.yaml").toPath();
        Files.writeString(empty, "", StandardCharsets.UTF_8);
        try {
            AlbumsFile.load(empty);
            fail("expected AlbumsFileException");
        } catch (AlbumsFileException e) {
            assertTrue(e.getMessage().contains("empty"));
        }
    }

    // ── validation tests ────────────────────────────────────────────────────

    @Test
    public void validate_missingSlug() throws Exception {
        Path f = writeYaml("albums:\n  - name: No Slug\n    source: /tmp/p\n");
        try {
            AlbumsFile.load(f);
            fail("expected AlbumsFileException");
        } catch (AlbumsFileException e) {
            assertTrue(e.getMessage().contains("slug is required"));
        }
    }

    @Test
    public void validate_missingName() throws Exception {
        Path f = writeYaml("albums:\n  - slug: no-name\n    source: /tmp/p\n");
        try {
            AlbumsFile.load(f);
            fail("expected AlbumsFileException");
        } catch (AlbumsFileException e) {
            assertTrue(e.getMessage().contains("name is required"));
        }
    }

    @Test
    public void validate_missingSource() throws Exception {
        Path f = writeYaml("albums:\n  - slug: no-source\n    name: No Source\n");
        try {
            AlbumsFile.load(f);
            fail("expected AlbumsFileException");
        } catch (AlbumsFileException e) {
            assertTrue(e.getMessage().contains("source is required"));
        }
    }

    @Test
    public void validate_unknownBase() throws Exception {
        Path f = writeYaml("albums:\n  - slug: bad\n    name: Bad\n    source: /tmp/p\n    base: ghost\n");
        try {
            AlbumsFile.load(f);
            fail("expected AlbumsFileException");
        } catch (AlbumsFileException e) {
            assertTrue(e.getMessage().contains("not defined in bases"));
        }
    }

    @Test
    public void validate_badTheme() throws Exception {
        Path f = writeYaml("settings:\n  default_theme: purple\nalbums:\n  - slug: a\n    name: A\n    source: /tmp/p\n");
        try {
            AlbumsFile.load(f);
            fail("expected AlbumsFileException");
        } catch (AlbumsFileException e) {
            assertTrue(e.getMessage().contains("default_theme"));
        }
    }

    @Test
    public void validate_heroMissingImage() throws Exception {
        Path f = writeYaml("settings:\n  hero:\n    crop: top\nalbums:\n  - slug: a\n    name: A\n    source: /tmp/p\n");
        try {
            AlbumsFile.load(f);
            fail("expected AlbumsFileException");
        } catch (AlbumsFileException e) {
            assertTrue(e.getMessage().contains("hero: image is required"));
        }
    }

    // ── round-trip tests ────────────────────────────────────────────────────

    @Test
    public void roundTrip_preservesComments() throws Exception {
        AlbumsFile af = loadFixture("testdata/albums.yaml");
        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);
        String saved = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue("block comment should survive round-trip",
                saved.contains("# Album with no base"));
        assertTrue("header comment should survive round-trip",
                saved.contains("# Test fixture"));
    }

    @Test
    public void roundTrip_preservesFoldedStyle() throws Exception {
        AlbumsFile af = loadFixture("testdata/albums.yaml");
        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);
        String saved = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(">- style should survive round-trip", saved.contains(">-"));
    }

    @Test
    public void roundTrip_modifyField() throws Exception {
        AlbumsFile af = loadFixture("testdata/albums.yaml");
        af.getSettings().setSiteName("Updated Name");
        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);

        AlbumsFile reloaded = AlbumsFile.load(out);
        assertEquals("Updated Name", reloaded.getSettings().getSiteName());
        assertEquals("sample", reloaded.getSettings().getId()); // other fields preserved
        assertEquals(3, reloaded.getAlbums().size());
    }

    @Test
    public void roundTrip_addAlbum() throws Exception {
        AlbumsFile af = loadFixture("testdata/albums.yaml");
        AlbumEntry newAlbum = new AlbumEntry();
        newAlbum.setSlug("new-album");
        newAlbum.setName("New Album");
        newAlbum.setBase("t7");
        newAlbum.setSource("new-folder");
        af.getAlbums().add(newAlbum);

        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);

        AlbumsFile reloaded = AlbumsFile.load(out);
        assertEquals(4, reloaded.getAlbums().size());
        assertEquals("new-album", reloaded.getAlbums().get(3).getSlug());
        assertEquals("New Album", reloaded.getAlbums().get(3).getName());
    }

    @Test
    public void roundTrip_insertAtFront_preservesScalarStyles() throws Exception {
        // syncAlbums() was positional: inserting at front reused the wrong YAML nodes,
        // corrupting ScalarStyle (quoted vs plain) and key ordering for all shifted albums.
        AlbumsFile af = loadFixture("testdata/albums-reorder.yaml");

        AlbumEntry newAlbum = new AlbumEntry();
        newAlbum.setSlug("new");
        newAlbum.setName("New Album");
        newAlbum.setSource("/tmp/new");
        af.getAlbums().addFirst(newAlbum);

        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);
        String yaml = Files.readString(out, StandardCharsets.UTF_8);

        // "first"'s cover was PLAIN - must not gain quotes
        assertTrue("plain cover must stay plain", yaml.contains("cover: plain.jpg"));
        // "second"'s cover/source were DOUBLE_QUOTED - must keep quotes (contain spaces)
        assertTrue("spaced cover must stay quoted",  yaml.contains("cover: \"cover with spaces.jpg\""));
        assertTrue("spaced source must stay quoted", yaml.contains("source: \"2024 Summer Vacation/Best Photos\""));
        // Booleans are always written explicitly for clarity (e.g. the new album, which is false)
        assertTrue("false boolean must be written explicitly", yaml.contains("manual_sort_order: false"));

        AlbumsFile reloaded = AlbumsFile.load(out);
        assertEquals(4, reloaded.getAlbums().size());
        assertEquals("new",    reloaded.getAlbums().get(0).getSlug());
        assertEquals("first",  reloaded.getAlbums().get(1).getSlug());
        assertEquals("second", reloaded.getAlbums().get(2).getSlug());
        assertEquals("third",  reloaded.getAlbums().get(3).getSlug());
        assertTrue("manual_sort_order must survive reorder", reloaded.getAlbums().get(1).isManualSortOrder());
        assertEquals("cover with spaces.jpg",            reloaded.getAlbums().get(2).getCover());
        assertEquals("2024 Summer Vacation/Best Photos", reloaded.getAlbums().get(2).getSource());
    }

    @Test
    public void roundTrip_moveAlbumToFront_preservesScalarStyles() throws Exception {
        // Simulates the real-world sequence: add album at end, then move it to position 0.
        AlbumsFile af = loadFixture("testdata/albums-reorder.yaml");

        AlbumEntry newAlbum = new AlbumEntry();
        newAlbum.setSlug("new");
        newAlbum.setName("New Album");
        newAlbum.setSource("/tmp/new");
        af.getAlbums().add(newAlbum);              // add at end
        List<AlbumEntry> albums = af.getAlbums();
        albums.addFirst(albums.removeLast());        // move to front

        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);
        String yaml = Files.readString(out, StandardCharsets.UTF_8);

        assertTrue("plain cover must stay plain",   yaml.contains("cover: plain.jpg"));
        assertTrue("spaced cover must stay quoted", yaml.contains("cover: \"cover with spaces.jpg\""));
        assertTrue("false boolean must be written explicitly", yaml.contains("manual_sort_order: false"));

        AlbumsFile reloaded = AlbumsFile.load(out);
        assertEquals(4, reloaded.getAlbums().size());
        assertEquals("new",   reloaded.getAlbums().get(0).getSlug());
        assertEquals("first", reloaded.getAlbums().get(1).getSlug());
        assertEquals("cover with spaces.jpg", reloaded.getAlbums().get(2).getCover());
        assertTrue("manual_sort_order must survive reorder", reloaded.getAlbums().get(1).isManualSortOrder());
    }

    @Test
    public void roundTrip_removeAlbum() throws Exception {
        AlbumsFile af = loadFixture("testdata/albums.yaml");
        af.getAlbums().remove(1); // remove nepal

        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);

        AlbumsFile reloaded = AlbumsFile.load(out);
        assertEquals(2, reloaded.getAlbums().size());
        assertEquals("antarctica", reloaded.getAlbums().get(0).getSlug());
        assertEquals("localtest", reloaded.getAlbums().get(1).getSlug());
    }

    @Test
    public void roundTrip_addBase() throws Exception {
        AlbumsFile af = loadFixture("testdata/albums.yaml");
        af.getBases().put("nas", "/mnt/nas/Photos");

        AlbumEntry extra = new AlbumEntry();
        extra.setSlug("nas-album");
        extra.setName("NAS Album");
        extra.setBase("nas");
        extra.setSource("trip2024");
        af.getAlbums().add(extra);

        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);

        AlbumsFile reloaded = AlbumsFile.load(out);
        assertEquals("/mnt/nas/Photos", reloaded.getBases().get("nas"));
        assertEquals("nas-album", reloaded.getAlbums().get(3).getSlug());
    }

    @Test
    public void roundTrip_basesAddedBeforeAlbumsWithBlankLine() throws Exception {
        // A file with settings + albums but no bases: adding a base must place the
        // bases block before albums (not appended at the end) with a blank line after it.
        Path f = writeYaml("settings:\n  id: site\nalbums:\n  - slug: a\n    name: A\n    source: rel\n");
        AlbumsFile af = AlbumsFile.load(f);
        af.getBases().put("t7", "/Volumes/T7/Photos");
        af.getAlbums().getFirst().setBase("t7");

        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);
        String yaml = Files.readString(out, StandardCharsets.UTF_8);

        int basesIdx  = yaml.indexOf("\nbases:");
        int albumsIdx = yaml.indexOf("\nalbums:");
        assertTrue("bases: must be present",  basesIdx >= 0);
        assertTrue("albums: must be present", albumsIdx >= 0);
        assertTrue("bases: must come before albums:", basesIdx < albumsIdx);
        assertTrue("blank line must precede bases:", yaml.contains("\n\nbases:"));
        assertTrue("blank line must separate bases block from albums:",
                yaml.contains("/Volumes/T7/Photos\n\nalbums:"));

        // and it round-trips back correctly
        AlbumsFile reloaded = AlbumsFile.load(out);
        assertEquals("/Volumes/T7/Photos", reloaded.getBases().get("t7"));
        assertEquals(1, reloaded.getAlbums().size());
    }

    @Test
    public void roundTrip_relocatesMisplacedBasesBeforeAlbums() throws Exception {
        // A file where bases was previously written AFTER albums: saving must relocate it.
        Path f = writeYaml(
                """
                        settings:
                          id: site
                        albums:
                          - slug: a
                            name: A
                            source: rel
                            base: t7
                        bases:
                          t7: /Volumes/T7/Photos
                        """);
        AlbumsFile af = AlbumsFile.load(f);

        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);
        String yaml = Files.readString(out, StandardCharsets.UTF_8);

        int basesIdx  = yaml.indexOf("\nbases:");
        int albumsIdx = yaml.indexOf("\nalbums:");
        assertTrue("bases: must be relocated before albums:", basesIdx >= 0 && basesIdx < albumsIdx);
        assertTrue("blank line must precede bases:", yaml.contains("\n\nbases:"));
        assertTrue("blank line must separate bases block from albums:",
                yaml.contains("/Volumes/T7/Photos\n\nalbums:"));
    }

    @Test
    public void roundTrip_clearFieldViaEmptyString() throws Exception {
        AlbumsFile af = loadFixture("testdata/albums.yaml");
        assertNotNull("precondition: siteTitleHtml must be set in fixture",
                af.getSettings().getSiteTitleHtml());

        af.getSettings().setSiteTitleHtml("");
        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);

        AlbumsFile reloaded = AlbumsFile.load(out);
        assertNull("cleared field should be absent after reload", reloaded.getSettings().getSiteTitleHtml());

        String yaml = Files.readString(out, StandardCharsets.UTF_8);
        assertFalse("cleared key should not appear in YAML", yaml.contains("site_title_html"));

        // other fields must survive
        assertEquals("sample", reloaded.getSettings().getId());
        assertEquals(3, reloaded.getAlbums().size());
    }

    @Test
    public void roundTrip_clearFieldViaNullDoesNotRestoreOldValue() throws Exception {
        AlbumsFile af = loadFixture("testdata/albums.yaml");
        assertNotNull("precondition: siteSubtitleHtml must be set in fixture",
                af.getSettings().getSiteSubtitleHtml());

        af.getSettings().setSiteSubtitleHtml(null);
        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);

        AlbumsFile reloaded = AlbumsFile.load(out);
        assertNull("null field should be absent after reload", reloaded.getSettings().getSiteSubtitleHtml());
    }

    @Test
    public void saveNewFile() throws Exception {
        AlbumsFile af = new AlbumsFile();
        af.getSettings().setId("mysite");
        af.getSettings().setSiteName("My Site");
        af.getSettings().setSiteUrl("https://example.com");

        Map<String, String> bases = new LinkedHashMap<>();
        bases.put("photos", "/mnt/photos");
        af.setBases(bases);

        AlbumEntry a = new AlbumEntry();
        a.setSlug("trip");
        a.setName("Trip 2024");
        a.setBase("photos");
        a.setSource("trip2024");
        af.getAlbums().add(a);

        Path out = tmp.newFile("new.yaml").toPath();
        af.save(out);

        AlbumsFile reloaded = AlbumsFile.load(out);
        assertEquals("mysite", reloaded.getSettings().getId());
        assertEquals("My Site", reloaded.getSettings().getSiteName());
        assertEquals(1, reloaded.getAlbums().size());
        assertEquals("trip", reloaded.getAlbums().getFirst().getSlug());
    }

    @Test
    public void save_writesFalseBooleansExplicitly() throws Exception {
        AlbumsFile af = new AlbumsFile();
        af.getSettings().setId("mysite");
        af.getSettings().setAllowCrawling(false);

        AlbumEntry a = new AlbumEntry();
        a.setSlug("trip");
        a.setName("Trip 2024");
        a.setSource("/tmp/trip");
        // manual_sort_order and recurse left at their default (false)
        af.getAlbums().add(a);

        Path out = tmp.newFile("bools.yaml").toPath();
        af.save(out);

        String yaml = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue("allow_crawling: false must be written",     yaml.contains("allow_crawling: false"));
        assertTrue("manual_sort_order: false must be written",  yaml.contains("manual_sort_order: false"));
        assertTrue("recurse: false must be written",            yaml.contains("recurse: false"));

        // and round-trips back to the same values
        AlbumsFile reloaded = AlbumsFile.load(out);
        assertFalse(reloaded.getSettings().isAllowCrawling());
        assertFalse(reloaded.getAlbums().getFirst().isManualSortOrder());
        assertFalse(reloaded.getAlbums().getFirst().isRecurse());
    }

    @Test
    public void roundTrip_addsExplicitFalseToFileMissingBooleans() throws Exception {
        // localtest in the fixture has no manual_sort_order / recurse keys; saving must add them.
        AlbumsFile af = loadFixture("testdata/albums.yaml");
        Path out = tmp.newFile("out.yaml").toPath();
        af.save(out);

        String yaml = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue("manual_sort_order: false must appear for albums without it",
                yaml.contains("manual_sort_order: false"));
        assertTrue("recurse: false must appear for albums without it",
                yaml.contains("recurse: false"));
    }

    // ── real-file round-trip tests ──────────────────────────────────────────

    /**
     * Loads each real albums.yaml, round-trips it through AlbumsFile.save(), and verifies
     * that the saved file parses to the same data as the original.  Also runs diff for
     * informational purposes (shown in stdout).  Skipped when source paths don't exist.
     *
     * Note: folded-scalar (>-) fields may be reformatted as a single line on round-trip
     * due to a SnakeYAML limitation; this is intentionally accepted here and only the
     * semantic data (parsed values) is compared.
     */
    @Test
    public void roundTripRealFiles() throws Exception {
        Map<String, Path> files = new LinkedHashMap<>();
        files.put("sample",      Paths.get("/Users/donohoe/work/ddphotos/sample/config/albums.yaml"));
        files.put("docker-init", Paths.get("/Users/donohoe/work/ddphotos/docker/init/albums.yaml"));
        files.put("manly-man",   Paths.get("/Users/donohoe/work/infra/photos/manly-man/albums.yaml"));
        files.put("donohoe",     Paths.get("/Users/donohoe/work/infra/photos/donohoe/albums.yaml"));

        boolean anyExists = files.values().stream().anyMatch(Files::exists);
        Assume.assumeTrue("Skipping real-file round-trip: none of the source files found (CI?)", anyExists);

        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String name = entry.getKey();
            Path originalPath = entry.getValue();

            if (!Files.exists(originalPath)) {
                System.out.println("[SKIP] " + name + ": " + originalPath + " not found");
                continue;
            }

            Path out = tmp.newFile("albums-roundtrip-" + name + ".yaml").toPath();
            AlbumsFile orig = AlbumsFile.load(originalPath);
            orig.save(out);

            // Show diff for information (does not affect pass/fail).
            Process proc = new ProcessBuilder("diff", originalPath.toString(), out.toString())
                    .redirectErrorStream(true)
                    .start();
            String diffOutput = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = proc.waitFor();
            System.out.printf("[%s]  original → %s%n", name, originalPath);
            System.out.printf("[%s]  output   → %s%n", name, out);
            if (exitCode == 0) {
                System.out.printf("[%s]  ✓ identical%n%n", name);
            } else {
                System.out.printf("[%s]  ~ %d diff line(s) (informational):%n%s%n",
                        name, diffOutput.lines().count(), diffOutput);
            }

            // Semantic comparison: re-load the saved file and compare parsed data.
            AlbumsFile saved = AlbumsFile.load(out);
            List<String> errs = semanticDiff(name, orig, saved);
            if (!errs.isEmpty()) {
                errs.forEach(System.out::println);
                failures.add(name);
            }
        }

        if (!failures.isEmpty()) {
            fail("Semantic round-trip differences in: " + String.join(", ", failures)
                    + " — see stdout above");
        }
    }

    /** Returns a list of semantic differences between two AlbumsFile instances, or empty if equal. */
    private List<String> semanticDiff(String label, AlbumsFile a, AlbumsFile b) {
        List<String> errs = new ArrayList<>();

        AlbumsSettings as = a.getSettings(), bs = b.getSettings();
        cmp(errs, label, "settings.id",              as.getId(),              bs.getId());
        cmp(errs, label, "settings.siteName",         as.getSiteName(),        bs.getSiteName());
        cmp(errs, label, "settings.siteUrl",          as.getSiteUrl(),         bs.getSiteUrl());
        cmp(errs, label, "settings.siteDescription",  as.getSiteDescription(), bs.getSiteDescription());
        cmp(errs, label, "settings.copyrightOwner",   as.getCopyrightOwner(),  bs.getCopyrightOwner());
        cmpInt(errs, label, "settings.copyrightYear", as.getCopyrightYear(),   bs.getCopyrightYear());
        cmpBool(errs, label, "settings.allowCrawling",as.isAllowCrawling(),    bs.isAllowCrawling());
        cmp(errs, label, "settings.descriptions",     as.getDescriptions(),    bs.getDescriptions());
        cmp(errs, label, "settings.passwords",        as.getPasswords(),       bs.getPasswords());
        cmp(errs, label, "settings.css",              as.getCss(),             bs.getCss());
        // default_theme is always emitted now; an absent value is semantically "dark"
        // (photogen's default), so normalize blank → dark before comparing.
        cmp(errs, label, "settings.defaultTheme",     themeOrDark(as.getDefaultTheme()), themeOrDark(bs.getDefaultTheme()));
        // HTML fields: compare with whitespace-normalized (folded scalars collapse on load)
        cmpNorm(errs, label, "settings.siteTitleHtml",    as.getSiteTitleHtml(),    bs.getSiteTitleHtml());
        cmpNorm(errs, label, "settings.siteSubtitleHtml", as.getSiteSubtitleHtml(), bs.getSiteSubtitleHtml());
        cmpNorm(errs, label, "settings.siteOverviewHtml", as.getSiteOverviewHtml(), bs.getSiteOverviewHtml());

        if (as.getHero() == null && bs.getHero() != null) errs.add("[" + label + "] hero: was null, now present");
        if (as.getHero() != null && bs.getHero() == null) errs.add("[" + label + "] hero: was present, now null");
        if (as.getHero() != null && bs.getHero() != null) {
            cmp(errs, label, "hero.image", as.getHero().getImage(), bs.getHero().getImage());
            cmp(errs, label, "hero.base",  as.getHero().getBase(),  bs.getHero().getBase());
            cmp(errs, label, "hero.crop",  as.getHero().getCrop(),  bs.getHero().getCrop());
        }

        if (!a.getBases().equals(b.getBases()))
            errs.add("[" + label + "] bases differ: " + a.getBases() + " vs " + b.getBases());

        List<AlbumEntry> aa = a.getAlbums(), ba = b.getAlbums();
        if (aa.size() != ba.size()) {
            errs.add("[" + label + "] albums.size: " + aa.size() + " vs " + ba.size());
        } else {
            for (int i = 0; i < aa.size(); i++) {
                AlbumEntry ae = aa.get(i), be = ba.get(i);
                String pfx = "album[" + i + "](" + ae.getSlug() + ")";
                cmp(errs, label, pfx + ".slug",        ae.getSlug(),        be.getSlug());
                cmp(errs, label, pfx + ".name",        ae.getName(),        be.getName());
                cmp(errs, label, pfx + ".description", ae.getDescription(), be.getDescription());
                cmp(errs, label, pfx + ".base",        ae.getBase(),        be.getBase());
                cmp(errs, label, pfx + ".source",      ae.getSource(),      be.getSource());
                cmp(errs, label, pfx + ".cover",       ae.getCover(),       be.getCover());
                cmpBool(errs, label, pfx + ".manualSortOrder", ae.isManualSortOrder(), be.isManualSortOrder());
                cmpBool(errs, label, pfx + ".recurse",         ae.isRecurse(),         be.isRecurse());
            }
        }
        return errs;
    }

    private static String themeOrDark(String t) {
        return (t == null || t.isBlank()) ? AlbumsFile.THEME_DARK : t;
    }

    private void cmp(List<String> errs, String label, String field, String a, String b) {
        if (!Objects.equals(a, b))
            errs.add("[" + label + "] " + field + ": " + a + " → " + b);
    }

    private void cmpNorm(List<String> errs, String label, String field, String a, String b) {
        // Normalize whitespace sequences to a single space for comparison (>- folding).
        String na = a == null ? null : a.replaceAll("\\s+", " ").trim();
        String nb = b == null ? null : b.replaceAll("\\s+", " ").trim();
        if (!Objects.equals(na, nb))
            errs.add("[" + label + "] " + field + ": (normalized) " + na + " → " + nb);
    }

    private void cmpInt(List<String> errs, String label, String field, int a, int b) {
        if (a != b) errs.add("[" + label + "] " + field + ": " + a + " → " + b);
    }

    private void cmpBool(List<String> errs, String label, String field, boolean a, boolean b) {
        if (a != b) errs.add("[" + label + "] " + field + ": " + a + " → " + b);
    }

    // ── path resolution tests ────────────────────────────────────────────────

    // resolveBasePath --------------------------------------------------------

    @Test
    public void resolveBasePath_null() {
        assertNull(new AlbumsFile().resolveBasePath(null));
    }

    @Test
    public void resolveBasePath_unknownBase() {
        assertNull(new AlbumsFile().resolveBasePath("nonexistent"));
    }

    @Test
    public void resolveBasePath_absolutePath() {
        Assume.assumeFalse("test data uses Unix absolute paths, which are not absolute on Windows", Utils.ISWINDOWS);
        AlbumsFile af = new AlbumsFile();
        af.getBases().put("t7", "/Volumes/T7/Photos");
        assertEquals(Path.of("/Volumes/T7/Photos"), af.resolveBasePath("t7"));
    }

    @Test
    public void resolveBasePath_relativePathWithSiteDir() {
        AlbumsFile af = new AlbumsFile();
        af.getBases().put("sample", "sample/source");
        af.setSiteDir(Path.of("/Users/example/site"));
        assertEquals(Path.of("/Users/example/site/sample/source"), af.resolveBasePath("sample"));
    }

    @Test
    public void resolveBasePath_relativePathNoSiteDir() {
        AlbumsFile af = new AlbumsFile();
        af.getBases().put("sample", "sample/source");
        // siteDir not set — cannot resolve relative path
        assertNull(af.resolveBasePath("sample"));
    }

    // resolveSourcePath ------------------------------------------------------

    @Test
    public void resolveSourcePath_nullAlbum() {
        assertNull(new AlbumsFile().resolveSourcePath(null));
    }

    @Test
    public void resolveSourcePath_emptySource() {
        assertNull(new AlbumsFile().resolveSourcePath(albumWith("a", null, "")));
    }

    @Test
    public void resolveSourcePath_dockerPath() {
        // /ddphotos paths exist only inside Docker; not locally resolvable
        assertNull(new AlbumsFile().resolveSourcePath(albumWith("a", null, "/ddphotos/source/theway")));
    }

    @Test
    public void resolveSourcePath_absolutePath() {
        Assume.assumeFalse("test data uses Unix absolute paths, which are not absolute on Windows", Utils.ISWINDOWS);
        AlbumsFile af = new AlbumsFile();
        assertEquals(
                Path.of("/Users/example/photos/2024"),
                af.resolveSourcePath(albumWith("a", null, "/Users/example/photos/2024")));
    }

    @Test
    public void resolveSourcePath_relativeWithAbsoluteBase() {
        Assume.assumeFalse("test data uses Unix absolute paths, which are not absolute on Windows", Utils.ISWINDOWS);
        AlbumsFile af = new AlbumsFile();
        af.getBases().put("t7", "/Volumes/T7/Photos");
        assertEquals(
                Path.of("/Volumes/T7/Photos/2024-Antarctica"),
                af.resolveSourcePath(albumWith("a", "t7", "2024-Antarctica")));
    }

    @Test
    public void resolveSourcePath_relativeWithRelativeBase() {
        AlbumsFile af = new AlbumsFile();
        af.getBases().put("sample", "sample/source");
        af.setSiteDir(Path.of("/Users/example/site"));
        assertEquals(
                Path.of("/Users/example/site/sample/source/theway"),
                af.resolveSourcePath(albumWith("a", "sample", "theway")));
    }

    @Test
    public void resolveSourcePath_relativeSourceNoBase() {
        // relative source with no base set — cannot resolve
        assertNull(new AlbumsFile().resolveSourcePath(albumWith("a", null, "relative-source")));
    }

    @Test
    public void resolveSourcePath_relativeBaseNoSiteDir() {
        // base has a relative path but siteDir is not set — base cannot be resolved
        AlbumsFile af = new AlbumsFile();
        af.getBases().put("sample", "sample/source");
        assertNull(af.resolveSourcePath(albumWith("a", "sample", "theway")));
    }

    // resolveCoverPath -------------------------------------------------------

    @Test
    public void resolveCoverPath_nullAlbum() {
        assertNull(new AlbumsFile().resolveCoverPath(null));
    }

    @Test
    public void resolveCoverPath_noCover() {
        AlbumsFile af = new AlbumsFile();
        af.getBases().put("t7", "/Volumes/T7/Photos");
        assertNull(af.resolveCoverPath(albumWith("a", "t7", "2024-Trip")));
    }

    @Test
    public void resolveCoverPath_sourceNotResolvable() {
        // relative source with no base — resolveSourcePath returns null, so cover is null too
        AlbumsFile af = new AlbumsFile();
        AlbumEntry a = albumWith("a", null, "relative-source");
        a.setCover("cover.jpg");
        assertNull(af.resolveCoverPath(a));
    }

    @Test
    public void resolveCoverPath_resolved() {
        Assume.assumeFalse("test data uses Unix absolute paths, which are not absolute on Windows", Utils.ISWINDOWS);
        AlbumsFile af = new AlbumsFile();
        af.getBases().put("t7", "/Volumes/T7/Photos");
        AlbumEntry a = albumWith("a", "t7", "2024-Trip");
        a.setCover("IMG_001.jpg");
        assertEquals(
                Path.of("/Volumes/T7/Photos/2024-Trip/IMG_001.jpg"),
                af.resolveCoverPath(a));
    }

    // toRelativeBasePath -----------------------------------------------------

    @Test
    public void toRelativeBasePath_noSiteDir() {
        // Without a siteDir, absolute paths pass through unchanged
        AlbumsFile af = new AlbumsFile();
        assertEquals("/absolute/path", af.toRelativeBasePath("/absolute/path"));
    }

    @Test
    public void toRelativeBasePath_nullOrBlank() {
        AlbumsFile af = new AlbumsFile();
        af.setSiteDir(Path.of("/some/site"));
        assertNull(af.toRelativeBasePath(null));
        assertEquals("", af.toRelativeBasePath(""));
    }

    @Test
    public void toRelativeBasePath_alreadyRelative() throws Exception {
        AlbumsFile af = new AlbumsFile();
        af.setSiteDir(tmp.newFolder("site").toPath());
        assertEquals("already/relative", af.toRelativeBasePath("already/relative"));
    }

    @Test
    public void toRelativeBasePath_withinSiteDir() throws Exception {
        Assume.assumeFalse("test data uses Unix absolute paths, which are not absolute on Windows", Utils.ISWINDOWS);
        File siteDir = tmp.newFolder("site");
        File sourceDir = new File(siteDir, "sample/source");
        assertTrue(sourceDir.mkdirs());

        AlbumsFile af = new AlbumsFile();
        af.setSiteDir(siteDir.toPath());

        assertEquals("sample/source", af.toRelativeBasePath(sourceDir.getAbsolutePath()));
    }

    @Test
    public void toRelativeBasePath_outsideSiteDir() throws Exception {
        File siteDir   = tmp.newFolder("site");
        File outsideDir = tmp.newFolder("outside");

        AlbumsFile af = new AlbumsFile();
        af.setSiteDir(siteDir.toPath());

        // Path outside the site dir is returned unchanged (relative would start with "..")
        String outside = outsideDir.getAbsolutePath();
        assertEquals(outside, af.toRelativeBasePath(outside));
    }

    // ── getPhotogenFiles tests ──────────────────────────────────────────────

    @Test
    public void getPhotogenFiles_nonRecursive_rootOnly() throws Exception {
        Path site = tmp.newFolder("site").toPath();
        Path source = Files.createDirectories(site.resolve("src/album"));
        Files.writeString(source.resolve("photogen.txt"), "img_1 One\n", StandardCharsets.UTF_8);
        Files.createDirectories(source.resolve("sub")); // ignored when not recursing

        AlbumsFile af = new AlbumsFile();
        af.setSiteDir(site);
        af.getBases().put("b", "src");

        List<PhotogenFile> files = af.getPhotogenFiles(albumWith("a", "b", "album"));
        assertEquals(1, files.size());
        assertEquals(source, files.getFirst().getDir());
        assertTrue(files.getFirst().existsOnDisk());
    }

    @Test
    public void getPhotogenFiles_recursive_rootAndSubfoldersAlphabetical() throws Exception {
        Path site = tmp.newFolder("site").toPath();
        Path source = Files.createDirectories(site.resolve("src/album"));
        Files.createDirectories(source.resolve("Bravo"));
        Files.createDirectories(source.resolve("alpha")); // sorts before Bravo case-insensitively
        Files.writeString(source.resolve("alpha/photogen.txt"), "x One\n", StandardCharsets.UTF_8);

        AlbumsFile af = new AlbumsFile();
        af.setSiteDir(site);
        af.getBases().put("b", "src");
        AlbumEntry a = albumWith("a", "b", "album");
        a.setRecurse(true);

        List<PhotogenFile> files = af.getPhotogenFiles(a);
        List<String> names = files.stream().map(f -> f.getDir().getFileName().toString()).toList();
        assertEquals(List.of("album", "alpha", "Bravo"), names);
        // a subfolder without photogen.txt is still returned, loaded-but-absent
        assertFalse(files.get(2).existsOnDisk());
    }

    @Test
    public void getPhotogenFiles_unresolvableSource_empty() {
        // relative source with no base → resolveSourcePath returns null
        assertTrue(new AlbumsFile().getPhotogenFiles(albumWith("a", null, "relative")).isEmpty());
    }

    // ── passwords file tests ────────────────────────────────────────────────

    @Test
    public void resolvePasswordsPath_defaultsFileName() throws Exception {
        Path configDir = tmp.newFolder("config").toPath();
        AlbumsFile af = new AlbumsFile();
        af.setConfigDir(configDir);

        // Unset: still resolves, using the conventional name.
        assertEquals(configDir.resolve("passwords.yaml"), af.resolvePasswordsPath());

        af.getSettings().setPasswords("secrets.yaml");
        assertEquals(configDir.resolve("secrets.yaml"), af.resolvePasswordsPath());
    }

    @Test
    public void resolvePasswordsPath_absoluteAndUnknownConfigDir() {
        Assume.assumeFalse("test data uses Unix absolute paths, which are not absolute on Windows", Utils.ISWINDOWS);
        AlbumsFile af = new AlbumsFile();
        assertNull("no config dir and a relative name is unresolvable", af.resolvePasswordsPath());

        af.getSettings().setPasswords("/etc/ddphotos/passwords.yaml");
        assertEquals(Path.of("/etc/ddphotos/passwords.yaml"), af.resolvePasswordsPath());
    }

    @Test
    public void getPasswordsFile_nullWhenUnsetOrAbsent() throws Exception {
        Path configDir = tmp.newFolder("config").toPath();
        AlbumsFile af = new AlbumsFile();
        af.setConfigDir(configDir);

        assertNull("settings.passwords unset", af.getPasswordsFile());

        af.getSettings().setPasswords("passwords.yaml");
        af.reloadPasswordsFile();
        assertNull("settings.passwords set but no file on disk", af.getPasswordsFile());

        Files.writeString(configDir.resolve("passwords.yaml"), "key: a-key\n", StandardCharsets.UTF_8);
        af.reloadPasswordsFile();
        PasswordsFile pf = af.getPasswordsFile();
        assertNotNull(pf);
        assertEquals("a-key", pf.getKey());
        assertSame("subsequent calls return the cached instance", pf, af.getPasswordsFile());
    }

    @Test
    public void getOrCreatePasswordsFile_defaultsSetting() throws Exception {
        Path configDir = tmp.newFolder("config").toPath();
        AlbumsFile af = new AlbumsFile();
        af.setConfigDir(configDir);
        assertNull(af.getSettings().getPasswords());

        PasswordsFile pf = af.getOrCreatePasswordsFile();
        assertNotNull(pf);
        assertFalse(pf.existsOnDisk());
        assertEquals("passwords.yaml", af.getSettings().getPasswords());
        assertEquals(configDir.resolve("passwords.yaml"), pf.getPath());
        assertSame(pf, af.getOrCreatePasswordsFile());

        // savePasswordsFile() creates it once there is something to write.
        pf.setKey("a-key");
        pf.setSitePassword("hunter2");
        af.savePasswordsFile();
        assertEquals("key: a-key\n\nsite:\n  password: hunter2\n",
                     Files.readString(pf.getPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void getOrCreatePasswordsFile_nullWithoutConfigDir() {
        assertNull(new AlbumsFile().getOrCreatePasswordsFile());
    }

    /**
     * The PasswordDialog flow for a site that has no passwords file yet: creating one defaults
     * settings.passwords, which lives in albums.yaml and so has to be saved there too.
     */
    @Test
    public void getOrCreatePasswordsFile_settingIsPersistedToAlbumsYaml() throws Exception {
        Path siteDir = tmp.newFolder("site").toPath();
        Path configDir = Files.createDirectories(siteDir.resolve("config"));
        Files.writeString(configDir.resolve("albums.yaml"), """
                settings:
                  id: my-photos
                  site_name: My Photos

                albums:
                  - slug: uganda
                    name: Uganda
                    source: /tmp/uganda
                """, StandardCharsets.UTF_8);

        Site site = new Site("My Photos", siteDir.toString(), null);
        AlbumsFile af = site.getOrCreateAlbumsFile();
        assertNull("fixture must start with no passwords setting", af.getSettings().getPasswords());

        // First open of the dialog: the user cancels, so nothing is written.
        af.reloadPasswordsFile();
        assertNotNull(af.getOrCreatePasswordsFile());
        assertEquals("passwords.yaml", af.getSettings().getPasswords());
        assertTrue(af.isPasswordsSettingUnsaved());

        // Second open: settings.passwords is already set in memory, so the in-memory value can
        // no longer reveal that albums.yaml is still missing it.
        af.reloadPasswordsFile();
        PasswordsFile pf = af.getOrCreatePasswordsFile();
        assertTrue("albums.yaml still lacks the setting after a canceled create",
                   af.isPasswordsSettingUnsaved());

        pf.setKey("my-photos-key");
        pf.setSitePassword("hunter2");
        af.savePasswordsFile();
        assertTrue(Files.exists(configDir.resolve("passwords.yaml")));

        site.saveAlbumsFile();
        assertFalse("saving clears the flag", af.isPasswordsSettingUnsaved());

        String albums = Files.readString(configDir.resolve("albums.yaml"), StandardCharsets.UTF_8);
        assertTrue("albums.yaml must record settings.passwords:\n" + albums,
                   albums.contains("passwords: passwords.yaml"));
    }

    // ── css file tests ──────────────────────────────────────────────────────

    @Test
    public void resolveCssPath_defaultsFileName() throws Exception {
        Path configDir = tmp.newFolder("config").toPath();
        AlbumsFile af = new AlbumsFile();
        af.setConfigDir(configDir);

        // Unset: still resolves, using the conventional name.
        assertEquals(configDir.resolve("custom.css"), af.resolveCssPath());

        af.getSettings().setCss("styles/site.css");
        assertEquals(configDir.resolve("styles/site.css"), af.resolveCssPath());
    }

    @Test
    public void resolveCssPath_absoluteAndUnknownConfigDir() {
        Assume.assumeFalse("test data uses Unix absolute paths, which are not absolute on Windows", Utils.ISWINDOWS);
        AlbumsFile af = new AlbumsFile();
        assertNull("no config dir and a relative name is unresolvable", af.resolveCssPath());

        af.getSettings().setCss("/etc/ddphotos/custom.css");
        assertEquals(Path.of("/etc/ddphotos/custom.css"), af.resolveCssPath());
    }

    /**
     * Unlike the passwords file, a stylesheet at the default location is picked up even when
     * settings.css is unset - the editor should show what is really there.
     */
    @Test
    public void getCssFile_foundEvenWhenSettingUnset() throws Exception {
        Path configDir = tmp.newFolder("config").toPath();
        AlbumsFile af = new AlbumsFile();
        af.setConfigDir(configDir);

        assertNull("nothing on disk yet", af.getCssFile());

        Files.writeString(configDir.resolve("custom.css"), "a { color: red; }\n", StandardCharsets.UTF_8);
        af.reloadCssFile();
        CssFile css = af.getCssFile();
        assertNotNull(css);
        assertNull("loading must not touch the setting", af.getSettings().getCss());
        assertEquals("a { color: red; }\n", css.getContent());
        assertSame("subsequent calls return the cached instance", css, af.getCssFile());
    }

    @Test
    public void getOrCreateCssFile_leavesSettingAlone() throws Exception {
        Path configDir = tmp.newFolder("config").toPath();
        AlbumsFile af = new AlbumsFile();
        af.setConfigDir(configDir);

        CssFile css = af.getOrCreateCssFile();
        assertNotNull(css);
        assertFalse(css.existsOnDisk());
        assertEquals(configDir.resolve("custom.css"), css.getPath());
        assertSame(css, af.getOrCreateCssFile());
        // photogen fails on a settings.css naming a file that isn't there, so the setting waits
        // until the file exists.
        assertNull(af.getSettings().getCss());
        assertFalse(af.isCssSettingUnsaved());
    }

    @Test
    public void getOrCreateCssFile_nullWithoutConfigDir() {
        assertNull(new AlbumsFile().getOrCreateCssFile());
    }

    /**
     * The editor flow for a site with no stylesheet: the setting only reaches albums.yaml once
     * the file itself has been written.
     */
    @Test
    public void saveCssFile_settingIsPersistedToAlbumsYamlOnlyOnceFileExists() throws Exception {
        Path siteDir = tmp.newFolder("site").toPath();
        Path configDir = Files.createDirectories(siteDir.resolve("config"));
        Files.writeString(configDir.resolve("albums.yaml"), """
                settings:
                  id: my-photos
                  site_name: My Photos

                albums:
                  - slug: uganda
                    name: Uganda
                    source: /tmp/uganda
                """, StandardCharsets.UTF_8);

        Site site = new Site("My Photos", siteDir.toString(), null);
        AlbumsFile af = site.getOrCreateAlbumsFile();
        assertNull("fixture must start with no css setting", af.getSettings().getCss());

        // First open of the editor: the user types nothing, so nothing is written anywhere.
        af.reloadCssFile();
        CssFile css = af.getOrCreateCssFile();
        assertNotNull(css);
        af.saveCssFile();
        assertFalse(Files.exists(configDir.resolve("custom.css")));
        assertNull("a dangling settings.css would break photogen", af.getSettings().getCss());
        assertFalse(af.isCssSettingUnsaved());

        // Second open: the user actually writes a rule.
        af.reloadCssFile();
        css = af.getOrCreateCssFile();
        css.setContent(".album-card { object-fit: contain; }");
        af.saveCssFile();

        assertEquals(".album-card { object-fit: contain; }\n",
                     Files.readString(configDir.resolve("custom.css"), StandardCharsets.UTF_8));
        assertEquals("custom.css", af.getSettings().getCss());
        assertTrue(af.isCssSettingUnsaved());

        site.saveAlbumsFile();
        assertFalse("saving clears the flag", af.isCssSettingUnsaved());

        String albums = Files.readString(configDir.resolve("albums.yaml"), StandardCharsets.UTF_8);
        assertTrue("albums.yaml must record settings.css:\n" + albums,
                   albums.contains("css: custom.css"));
    }

    /** An existing settings.css is left exactly as the user wrote it. */
    @Test
    public void saveCssFile_keepsExistingSettingUntouched() throws Exception {
        Path configDir = tmp.newFolder("config").toPath();
        Files.writeString(configDir.resolve("site.css"), "a { color: red; }\n", StandardCharsets.UTF_8);

        AlbumsFile af = new AlbumsFile();
        af.setConfigDir(configDir);
        af.getSettings().setCss("site.css");

        CssFile css = af.getOrCreateCssFile();
        css.setContent("a { color: blue; }\n");
        af.saveCssFile();

        assertEquals("a { color: blue; }\n",
                     Files.readString(configDir.resolve("site.css"), StandardCharsets.UTF_8));
        assertEquals("site.css", af.getSettings().getCss());
        assertFalse("the setting was already there; albums.yaml is not dirty", af.isCssSettingUnsaved());
    }

    // ── change detection (ConfigFile) ───────────────────────────────────────

    @Test
    public void load_recordsThePathItCameFrom() throws Exception {
        Path path = writeYaml(MINIMAL);
        assertEquals(path, AlbumsFile.load(path).getPath());
    }

    @Test
    public void newFile_hasNoPathAndIsNeverChanged() {
        // Site.getOrCreateAlbumsFile() builds one of these before it has anywhere to live.
        AlbumsFile af = new AlbumsFile();
        assertNull(af.getPath());
        assertFalse(af.isChangedOnDisk());
    }

    @Test
    public void afterLoad_isNotChangedOnDisk() throws Exception {
        Path path = writeYaml(MINIMAL);
        assertFalse(AlbumsFile.load(path).isChangedOnDisk());
    }

    @Test
    public void afterOurOwnSave_isNotChangedOnDisk() throws Exception {
        // The one that matters most: saving must not leave the app thinking someone else wrote
        // the file, or every save would raise a false "changed on disk" alarm.
        Path path = writeYaml(MINIMAL);
        AlbumsFile af = AlbumsFile.load(path);

        af.getSettings().setSiteName("Renamed");
        af.save(path);

        assertFalse("our own write must not read as an external change", af.isChangedOnDisk());
    }

    @Test
    public void saveGivesAPathToAFileThatNeverHadOne() throws Exception {
        AlbumsFile af = new AlbumsFile();
        Path path = tmp.getRoot().toPath().resolve("brand-new.yaml");
        af.save(path);

        assertEquals(path, af.getPath());
        assertFalse(af.isChangedOnDisk());
    }

    @Test
    public void afterExternalWrite_isChangedOnDisk() throws Exception {
        Path path = writeYaml(MINIMAL);
        AlbumsFile af = AlbumsFile.load(path);

        Files.writeString(path, MINIMAL + "\n# someone else was here\n", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.fromMillis(
                Files.getLastModifiedTime(path).toMillis() + 5_000));

        assertTrue(af.isChangedOnDisk());
    }

    private static final String MINIMAL = """
            settings:
              id: sample
              site_name: Sample
            """;

    // ── helpers ─────────────────────────────────────────────────────────────

    private AlbumsFile loadFixture(String resourcePath) throws Exception {
        URL url = getClass().getClassLoader().getResource(resourcePath);
        assertNotNull("test resource not found: " + resourcePath, url);
        return AlbumsFile.load(Paths.get(url.toURI()));
    }

    private Path writeYaml(String content) throws Exception {
        File f = tmp.newFile("test.yaml");
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
        return f.toPath();
    }

    private static AlbumEntry albumWith(String slug, String base, String source) {
        AlbumEntry a = new AlbumEntry();
        a.setSlug(slug);
        a.setName(slug);
        a.setBase(base);
        a.setSource(source);
        return a;
    }
}
