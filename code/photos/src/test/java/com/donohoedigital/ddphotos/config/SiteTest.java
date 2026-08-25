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

    // ── getSiteEnvPath() ────────────────────────────────────────────────────

    @Test
    public void getSiteEnvPath_withConfigPath() {
        Site site = new Site("My Site", "/data/site", "/data/site/custom-config");
        assertEquals(Path.of("/data/site/custom-config/site.env"), site.getSiteEnvPath());
    }

    @Test
    public void getSiteEnvPath_withDirPath_noConfigPath() {
        Site site = new Site("My Site", "/data/site", null);
        assertEquals(Path.of("/data/site/config/site.env"), site.getSiteEnvPath());
    }

    @Test
    public void getSiteEnvPath_noDirPath_noConfigPath() {
        assertNull(new Site("My Site", null, null).getSiteEnvPath());
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

    // ── tryReloadAlbumsFile() ───────────────────────────────────────────────

    @Test
    public void tryReload_picksUpAnExternalEdit() throws Exception {
        Path configDir = tmp.newFolder("config").toPath();
        writeMinimalAlbums(configDir, "before");

        Site site = new Site("My Site", "/irrelevant", configDir.toString());
        assertEquals("before", site.getAlbumsFile().getSettings().getId());

        writeMinimalAlbums(configDir, "after");
        assertTrue(site.tryReloadAlbumsFile());
        assertEquals("after", site.getAlbumsFile().getSettings().getId());
    }

    @Test
    public void tryReload_keepsInMemoryCopyWhenTheFileIsBroken() throws Exception {
        // The half-written-file case: an outside editor saves in stages, and we catch it midway.
        // Blowing up (as getAlbumsFile does) would be wrong for a reload nobody asked for.
        Path configDir = tmp.newFolder("config").toPath();
        writeMinimalAlbums(configDir, "good");

        Site site = new Site("My Site", "/irrelevant", configDir.toString());
        AlbumsFile before = site.getAlbumsFile();

        Files.writeString(configDir.resolve("albums.yaml"), ":\nnot: [valid", StandardCharsets.UTF_8);

        assertFalse(site.tryReloadAlbumsFile());
        assertSame("the previous model must survive a failed reload", before, site.getAlbumsFile());
        assertEquals("good", site.getAlbumsFile().getSettings().getId());
    }

    @Test
    public void tryReload_recoversOnceTheFileIsWholeAgain() throws Exception {
        Path configDir = tmp.newFolder("config").toPath();
        writeMinimalAlbums(configDir, "good");

        Site site = new Site("My Site", "/irrelevant", configDir.toString());
        site.getAlbumsFile();

        Files.writeString(configDir.resolve("albums.yaml"), ":\nnot: [valid", StandardCharsets.UTF_8);
        assertFalse(site.tryReloadAlbumsFile());

        writeMinimalAlbums(configDir, "fixed");
        assertTrue(site.tryReloadAlbumsFile());
        assertEquals("fixed", site.getAlbumsFile().getSettings().getId());
    }

    @Test
    public void tryReload_noFile_isANoOp() {
        Site site = new Site("My Site", null, null);
        assertFalse(site.tryReloadAlbumsFile());
    }

    @Test
    public void tryReload_setsDirsOnTheFreshModel() throws Exception {
        // Easy to lose in a reload: without setDirsOn, relative bases stop resolving.
        Path siteDir = tmp.newFolder("site").toPath();
        Path configDir = Files.createDirectory(siteDir.resolve("config"));
        writeMinimalAlbums(configDir, "one");

        Site site = new Site("My Site", siteDir.toString(), null);
        site.getAlbumsFile();

        writeMinimalAlbums(configDir, "two");
        assertTrue(site.tryReloadAlbumsFile());

        AlbumsFile af = site.getAlbumsFile();
        assertEquals(siteDir, af.getSiteDir());
        assertEquals(configDir, af.getConfigDir());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void writeMinimalAlbums(Path dir, String id) throws Exception {
        Files.writeString(dir.resolve("albums.yaml"),
                "settings:\n  id: " + id + "\n",
                StandardCharsets.UTF_8);
    }
}
