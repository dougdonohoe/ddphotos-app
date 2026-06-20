package com.donohoedigital.ddphotos.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.*;

public class SitesFileTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

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
        Path out = tmp.newFile("sites.yaml").toPath();
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
        Path out = tmp.newFile("sites.yaml").toPath();
        SitesFile sf = new SitesFile(out);
        sf.save();

        SitesFile reloaded = new SitesFile(out).load();
        assertTrue(reloaded.getSites().isEmpty());
    }

    // ── round-trip tests ────────────────────────────────────────────────────

    @Test
    public void roundTrip_multipleSites() throws Exception {
        Path out = tmp.newFile("sites.yaml").toPath();
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

    // ── helpers ─────────────────────────────────────────────────────────────

    private Path writeYaml(String content) throws Exception {
        File f = tmp.newFile("test.yaml");
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
        return f.toPath();
    }
}
