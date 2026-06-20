package com.donohoedigital.ddphotos.config;

import com.donohoedigital.base.ApplicationError;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SiteTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ── getAlbumsFilePath() ─────────────────────────────────────────────────

    @Test
    public void getAlbumsFilePath_withConfigPath() {
        Site site = new Site("My Site", "/data/site", "/data/site/custom-config");
        assertEquals(Path.of("/data/site/custom-config/albums.yaml"), site.getAlbumsFilePath());
    }

    @Test
    public void getAlbumsFilePath_withDirPath_noConfigPath() {
        Site site = new Site("My Site", "/data/site", null);
        assertEquals(Path.of("/data/site/config/albums.yaml"), site.getAlbumsFilePath());
    }

    @Test
    public void getAlbumsFilePath_withDirPath_emptyConfigPath() {
        Site site = new Site("My Site", "/data/site", "");
        assertEquals(Path.of("/data/site/config/albums.yaml"), site.getAlbumsFilePath());
    }

    @Test
    public void getAlbumsFilePath_noDirPath_noConfigPath() {
        assertNull(new Site("My Site", null, null).getAlbumsFilePath());
    }

    @Test
    public void getAlbumsFilePath_emptyDirPath_noConfigPath() {
        assertNull(new Site("My Site", "", null).getAlbumsFilePath());
    }

    // ── getActualConfigPath() ────────────────────────────────────────────────

    @Test
    public void getActualConfigPath_withConfigPath() {
        Site site = new Site("My Site", "/data/site", "/data/site/custom-config");
        assertEquals("/data/site/custom-config", site.getActualConfigPath());
    }

    @Test
    public void getActualConfigPath_withDirPath_noConfigPath() {
        Site site = new Site("My Site", "/data/site", null);
        assertEquals("/data/site/config", site.getActualConfigPath());
    }

    @Test
    public void getActualConfigPath_withDirPath_emptyConfigPath() {
        Site site = new Site("My Site", "/data/site", "");
        assertEquals("/data/site/config", site.getActualConfigPath());
    }

    @Test
    public void getActualConfigPath_noDirPath_noConfigPath() {
        assertNull(new Site("My Site", null, null).getActualConfigPath());
    }

    @Test
    public void getActualConfigPath_emptyDirPath_noConfigPath() {
        assertNull(new Site("My Site", "", null).getActualConfigPath());
    }

    // ── getAlbumsFile() ──────────────────────────────────────────────────────

    @Test
    public void getAlbumsFile_fromConfigPath() throws Exception {
        Path configDir = tmp.newFolder("custom-config").toPath();
        writeMinimalAlbums(configDir, "my-site");

        Site site = new Site("My Site", "/nonexistent/dir", configDir.toString());
        AlbumsFile af = site.getAlbumsFile();
        assertNotNull(af);
        assertEquals("my-site", af.getSettings().getId());
    }

    @Test
    public void getAlbumsFile_fromDirPath() throws Exception {
        Path configDir = tmp.newFolder("site", "config").toPath();
        writeMinimalAlbums(configDir, "site-id");

        Path siteDir = configDir.getParent();
        Site site = new Site("My Site", siteDir.toString(), null);
        AlbumsFile af = site.getAlbumsFile();
        assertNotNull(af);
        assertEquals("site-id", af.getSettings().getId());
    }

    @Test
    public void getAlbumsFile_configPathTakesPrecedenceOverDirPath() throws Exception {
        Path defaultConfigDir = tmp.newFolder("site", "config").toPath();
        writeMinimalAlbums(defaultConfigDir, "default-id");

        Path customConfigDir = tmp.newFolder("custom-config").toPath();
        writeMinimalAlbums(customConfigDir, "custom-id");

        Path siteDir = defaultConfigDir.getParent();
        Site site = new Site("My Site", siteDir.toString(), customConfigDir.toString());
        AlbumsFile af = site.getAlbumsFile();
        assertNotNull(af);
        assertEquals("custom-id", af.getSettings().getId());
    }

    @Test
    public void getAlbumsFile_fileAbsent_returnsNull() throws Exception {
        Path siteDir = tmp.newFolder("site").toPath();
        // No config subdir or albums.yaml created
        Site site = new Site("My Site", siteDir.toString(), null);
        assertNull(site.getAlbumsFile());
    }

    @Test
    public void getAlbumsFile_nullPath_returnsNull() {
        assertNull(new Site("My Site", null, null).getAlbumsFile());
    }

    @Test
    public void getAlbumsFile_invalidYaml_throwsApplicationError() throws Exception {
        Path configDir = tmp.newFolder("config").toPath();
        Files.writeString(configDir.resolve("albums.yaml"), ":\nnot: [valid", StandardCharsets.UTF_8);

        Site site = new Site("My Site", "/irrelevant", configDir.toString());
        try {
            site.getAlbumsFile();
            fail("expected ApplicationError");
        } catch (ApplicationError e) {
            // expected
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void writeMinimalAlbums(Path dir, String id) throws Exception {
        Files.writeString(dir.resolve("albums.yaml"),
                "settings:\n  id: " + id + "\n",
                StandardCharsets.UTF_8);
    }
}
