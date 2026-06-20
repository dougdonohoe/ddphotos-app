package com.donohoedigital.ddphotos.config;

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
                "settings:\n  id: site\n" +
                "albums:\n  - slug: a\n    name: A\n    source: rel\n    base: t7\n" +
                "bases:\n  t7: /Volumes/T7/Photos\n");
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

            Path out = Path.of("/tmp/albums-roundtrip-" + name + ".yaml");
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
        AlbumsFile af = new AlbumsFile();
        assertEquals(
                Path.of("/Users/example/photos/2024"),
                af.resolveSourcePath(albumWith("a", null, "/Users/example/photos/2024")));
    }

    @Test
    public void resolveSourcePath_relativeWithAbsoluteBase() {
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
