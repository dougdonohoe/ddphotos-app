package com.donohoedigital.ddphotos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SitesFileTest {

    @TempDir
    Path tmp;

    // ── load tests ──────────────────────────────────────────────────────────

    @Test
    public void load_valid() throws Exception {
        Path f = writeYaml(
                """
                        sites:
                          - display_name: Site One
                            dir_path: /data/site1
                            config_path: /data/site1/config
                          - display_name: Site Two
                            dir_path: /data/site2
                            config_path: /etc/site2-config
                        """);

        SitesFile sf = new SitesFile(f).load();
        List<Site> sites = sf.getSites();
        assertEquals(2, sites.size());

        assertEquals("Site One", sites.getFirst().getDisplayName());
        assertEquals("/data/site1", sites.getFirst().getDirPath());
        assertEquals("/data/site1/config", sites.getFirst().getConfigPath());

        assertEquals("Site Two", sites.get(1).getDisplayName());
        assertEquals("/data/site2", sites.get(1).getDirPath());
        assertEquals("/etc/site2-config", sites.get(1).getConfigPath());
    }

    @Test
    public void load_emptySites() throws Exception {
        Path f = writeYaml("sites: []\n");
        SitesFile sf = new SitesFile(f).load();
        assertTrue(sf.getSites().isEmpty());
    }

    @Test
    public void load_missingSitesKey() throws Exception {
        Path f = writeYaml("other: value\n");
        SitesFile sf = new SitesFile(f).load();
        assertTrue(sf.getSites().isEmpty());
    }

    @Test
    public void load_missingFile() {
        try {
            new SitesFile(Paths.get("/nonexistent/sites.yaml")).loadInternal();
            fail("expected SitesFileException");
        } catch (SitesFileException e) {
            assertTrue(e.getMessage().contains("read"));
        }
    }

    @Test
    public void load_invalidYaml() throws Exception {
        Path bad = writeYaml(":\nthis: [is: {not valid");
        try {
            new SitesFile(bad).loadInternal();
            fail("expected SitesFileException");
        } catch (SitesFileException e) {
            assertTrue(e.getMessage().contains("parse"));
        }
    }

    @Test
    public void load_missingDisplayName() throws Exception {
        Path f = writeYaml(
                """
                        sites:
                          - dir_path: /data/site1
                            config_path: /data/site1/config
                        """);
        try {
            new SitesFile(f).loadInternal();
            fail("expected SitesFileException");
        } catch (SitesFileException e) {
            assertTrue(e.getMessage().contains("display_name is required"));
        }
    }

    // ── save tests ──────────────────────────────────────────────────────────

    @Test
    public void saveNewFile() throws Exception {
        Path out = Files.createFile(tmp.resolve("sites.yaml"));
        SitesFile sf = new SitesFile(out);
        sf.getSites().add(new Site("My Site", "/data/mysite", "/data/mysite/config"));

        sf.save();

        String yaml = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(yaml.contains("display_name"));
        assertTrue(yaml.contains("My Site"));
        assertTrue(yaml.contains("dir_path"));
        assertTrue(yaml.contains("/data/mysite"));
        assertTrue(yaml.contains("config_path"));
        assertTrue(yaml.contains("/data/mysite/config"));
    }

    @Test
    public void saveEmptySites() throws Exception {
        Path out = Files.createFile(tmp.resolve("sites.yaml"));
        SitesFile sf = new SitesFile(out);
        sf.save();

        SitesFile reloaded = new SitesFile(out).load();
        assertTrue(reloaded.getSites().isEmpty());
    }

    // ── round-trip tests ────────────────────────────────────────────────────

    @Test
    public void roundTrip_multipleSites() throws Exception {
        Path out = Files.createFile(tmp.resolve("sites.yaml"));
        SitesFile sf = new SitesFile(out);
        sf.getSites().add(new Site("Trip One", "/photos/trip1", "/photos/trip1/config"));
        sf.getSites().add(new Site("Trip Two", "/photos/trip2", "/etc/trip2-config"));

        sf.save();

        SitesFile reloaded = new SitesFile(out).load();
        assertEquals(2, reloaded.getSites().size());
        assertEquals("Trip One", reloaded.getSites().getFirst().getDisplayName());
        assertEquals("/photos/trip1", reloaded.getSites().getFirst().getDirPath());
        assertEquals("/photos/trip1/config", reloaded.getSites().getFirst().getConfigPath());
        assertEquals("Trip Two", reloaded.getSites().get(1).getDisplayName());
        assertEquals("/photos/trip2", reloaded.getSites().get(1).getDirPath());
        assertEquals("/etc/trip2-config", reloaded.getSites().get(1).getConfigPath());
    }

    @Test
    public void roundTrip_addSite() throws Exception {
        Path f = writeYaml(
                """
                        sites:
                          - display_name: Site One
                            dir_path: /data/site1
                            config_path: /data/site1/config
                        """);

        SitesFile sf = new SitesFile(f).load();
        sf.getSites().add(new Site("Site Two", "/data/site2", "/data/site2/config"));

        sf.save();

        SitesFile reloaded = new SitesFile(f).load();
        assertEquals(2, reloaded.getSites().size());
        assertEquals("Site One", reloaded.getSites().getFirst().getDisplayName());
        assertEquals("/data/site1", reloaded.getSites().getFirst().getDirPath());
        assertEquals("Site Two", reloaded.getSites().get(1).getDisplayName());
        assertEquals("/data/site2", reloaded.getSites().get(1).getDirPath());
    }

    @Test
    public void roundTrip_removeSite() throws Exception {
        Path f = writeYaml(
                """
                        sites:
                          - display_name: Site One
                            dir_path: /data/site1
                            config_path: /data/site1/config
                          - display_name: Site Two
                            dir_path: /data/site2
                            config_path: /data/site2/config
                        """);

        SitesFile sf = new SitesFile(f).load();
        sf.getSites().removeFirst();

        sf.save();

        SitesFile reloaded = new SitesFile(f).load();
        assertEquals(1, reloaded.getSites().size());
        assertEquals("Site Two", reloaded.getSites().getFirst().getDisplayName());
        assertEquals("/data/site2", reloaded.getSites().getFirst().getDirPath());
    }

    // ── findDuplicate tests ─────────────────────────────────────────────────

    @Test
    public void findDuplicate_sameDirPath_bothConfigsBlank() {
        SitesFile sf = sitesFileWith(new Site("Site One", "/data/site1", null));

        Site found = sf.findDuplicate(new Site("Different Name", "/data/site1", null), null);
        assertNotNull(found);
        assertEquals("Site One", found.getDisplayName());
    }

    @Test
    public void findDuplicate_ignoresTrailingSlashAndDotSegments() {
        SitesFile sf = sitesFileWith(new Site("Site One", "/data/site1", null));

        assertNotNull(sf.findDuplicate(new Site("Other", "/data/site1/", null), null));
        assertNotNull(sf.findDuplicate(new Site("Other", "/data/./site1", null), null));
    }

    @Test
    public void findDuplicate_blankConfigMatchesExplicitDefaultConfig() {
        SitesFile sf = sitesFileWith(new Site("Site One", "/data/site1", null));

        // config_path spelled out as the default <dir>/config resolves to the same config folder
        assertNotNull(sf.findDuplicate(new Site("Other", "/data/site1", "/data/site1/config"), null));
    }

    @Test
    public void findDuplicate_sameDirDifferentCustomConfig_returnsNull() {
        SitesFile sf = sitesFileWith(new Site("Site One", "/data/site1", "/etc/site1-config"));

        assertNull(sf.findDuplicate(new Site("Other", "/data/site1", "/etc/other-config"), null));
    }

    @Test
    public void findDuplicate_differentDirPath_returnsNull() {
        SitesFile sf = sitesFileWith(new Site("Site One", "/data/site1", null));

        assertNull(sf.findDuplicate(new Site("Other", "/data/site2", null), null));
    }

    @Test
    public void findDuplicate_excludedSiteDoesNotMatchItself() {
        Site existing = new Site("Site One", "/data/site1", null);
        SitesFile sf = sitesFileWith(existing);

        // edit mode: only the display name changed, so the site must not match itself
        assertNull(sf.findDuplicate(new Site("Renamed", "/data/site1", null), existing));
    }

    @Test
    public void findDuplicate_excludedSiteStillMatchesSibling() {
        Site editing = new Site("Site Two", "/data/site2", null);
        SitesFile sf = sitesFileWith(new Site("Site One", "/data/site1", null), editing);

        // edit mode: pointing site two at site one's folder is still a duplicate
        Site found = sf.findDuplicate(new Site("Site Two", "/data/site1", null), editing);
        assertNotNull(found);
        assertEquals("Site One", found.getDisplayName());
    }

    @Test
    public void findDuplicate_emptySitesFile_returnsNull() {
        SitesFile sf = new SitesFile(Paths.get("/tmp/does-not-matter.yaml"));

        assertNull(sf.findDuplicate(new Site("Site One", "/data/site1", null), null));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private SitesFile sitesFileWith(Site... sites) {
        SitesFile sf = new SitesFile(Paths.get("/tmp/does-not-matter.yaml"));
        for (Site site : sites) {
            sf.addSite(site);
        }
        return sf;
    }

    // ── a file that failed to load is never overwritten ─────────────────────

    @Test
    public void loadFailed_saveRefusesAndLeavesFileUntouched() throws Exception {
        String broken = """
                sites:
                  - display_name: Site One
                    dir_path: /data/site1
                  - dir_path: /data/site2
                """;
        Path f = writeYaml(broken);

        SitesFile sf = new SitesFile(f).load();
        assertNotNull(sf.getLoadError());
        assertTrue(sf.getSites().isEmpty(), "sites read before the error must not be kept");

        sf.addSite(new Site("New", "/data/new", null));
        SitesFileException e = assertThrows(SitesFileException.class, sf::save);
        assertTrue(e.getMessage().contains("could not be read"), e.getMessage());
        assertEquals(broken, Files.readString(f, StandardCharsets.UTF_8));
    }

    // ── removeUnreadableSites ───────────────────────────────────────────────

    @Test
    public void removeUnreadableSites_dropsOnlySitesWithBrokenAlbums() throws Exception {
        Path good = siteDir("good", "settings:\n  id: good\nalbums: []\n");
        Path broken = siteDir("broken", "settings:\n  id: broken\n  site_name: \"Broken\n");
        Path noFile = Files.createDirectories(tmp.resolve("nofile"));
        Path f = writeYaml("sites:\n"
                + "  - display_name: Good\n    dir_path: " + good + "\n"
                + "  - display_name: Broken\n    dir_path: " + broken + "\n"
                + "  - display_name: No File\n    dir_path: " + noFile + "\n");
        SitesFile sf = new SitesFile(f).load();

        List<String> removed = sf.removeUnreadableSites();
        assertEquals(1, removed.size());
        assertTrue(removed.getFirst().startsWith("Broken: parse " + broken.resolve("config/albums.yaml")),
                removed.getFirst());
        assertEquals(List.of("Good", "No File"), sf.getSites().stream().map(Site::getDisplayName).toList());
        assertEquals("good", sf.getSites().getFirst().getIdOrDefault());

        sf.save();
        assertEquals(List.of("Good", "No File"),
                new SitesFile(f).load().getSites().stream().map(Site::getDisplayName).toList());
        assertTrue(Files.readString(broken.resolve("config/albums.yaml")).contains("\"Broken"),
                "the broken albums.yaml itself is left alone");
    }

    @Test
    public void removeUnreadableSites_dropsSiteThatFailsValidation() throws Exception {
        Path site = siteDir("noname", "albums:\n  - slug: a\n    source: /tmp/a\n");
        Path f = writeYaml("sites:\n  - display_name: No Name\n    dir_path: " + site + "\n");
        SitesFile sf = new SitesFile(f).load();

        List<String> removed = sf.removeUnreadableSites();
        assertEquals(1, removed.size());
        assertTrue(removed.getFirst().contains("name is required"), removed.getFirst());
        assertTrue(sf.getSites().isEmpty());
    }

    private Path siteDir(String name, String albumsYaml) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve(name).resolve("config")).getParent();
        Files.writeString(dir.resolve("config/albums.yaml"), albumsYaml, StandardCharsets.UTF_8);
        return dir;
    }

    private Path writeYaml(String content) throws Exception {
        File f = Files.createFile(tmp.resolve("test.yaml")).toFile();
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
        return f.toPath();
    }
}
