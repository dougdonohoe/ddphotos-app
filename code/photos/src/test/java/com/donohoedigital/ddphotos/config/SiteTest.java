package com.donohoedigital.ddphotos.config;

import com.donohoedigital.base.ApplicationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SiteTest {

    @TempDir
    Path tmp;

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

    // ── saveCssSetting() ────────────────────────────────────────────────────

    @Test
    public void saveCssSetting_afterModelReloaded() throws Exception {
        // The CSS editor keeps the model it opened with; a base change reloads the site's model
        // while it is open.  The defaulted settings.css has to reach the model that is saved.
        Path dir = tmp.resolve("site");
        Files.createDirectories(dir.resolve("config"));
        Files.writeString(dir.resolve("config/albums.yaml"), "settings:\n  id: site\nalbums: []\n");
        Site site = new Site("Site", dir.toString(), null);

        AlbumsFile editorModel = site.getOrCreateAlbumsFile();
        TextFile css = editorModel.getOrCreateCssFile();
        site.reloadAlbumsFile();
        assertNotSame(editorModel, site.getAlbumsFile(), "precondition: the model was replaced");

        css.setContent("body { color: red; }\n");
        editorModel.saveCssFile();
        site.saveCssSetting(editorModel);

        assertEquals(CssFile.FILE_NAME, AlbumsFile.load(dir.resolve("config/albums.yaml")).getSettings().getCss());
    }

    @Test
    public void saveCssSetting_sameModel() throws Exception {
        Path dir = tmp.resolve("site");
        Files.createDirectories(dir.resolve("config"));
        Files.writeString(dir.resolve("config/albums.yaml"), "settings:\n  id: site\nalbums: []\n");
        Site site = new Site("Site", dir.toString(), null);

        AlbumsFile model = site.getOrCreateAlbumsFile();
        model.getOrCreateCssFile().setContent("body { color: red; }\n");
        model.saveCssFile();
        site.saveCssSetting(model);

        assertEquals(CssFile.FILE_NAME, AlbumsFile.load(dir.resolve("config/albums.yaml")).getSettings().getCss());
        assertFalse(model.isCssSettingUnsaved());
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
        Path configDir = Files.createDirectory(tmp.resolve("custom-config"));
        writeMinimalAlbums(configDir, "my-site");

        Site site = new Site("My Site", "/nonexistent/dir", configDir.toString());
        AlbumsFile af = site.getAlbumsFile();
        assertNotNull(af);
        assertEquals("my-site", af.getSettings().getId());
    }

    @Test
    public void getAlbumsFile_fromDirPath() throws Exception {
        Path configDir = Files.createDirectories(tmp.resolve("site").resolve("config"));
        writeMinimalAlbums(configDir, "site-id");

        Path siteDir = configDir.getParent();
        Site site = new Site("My Site", siteDir.toString(), null);
        AlbumsFile af = site.getAlbumsFile();
        assertNotNull(af);
        assertEquals("site-id", af.getSettings().getId());
    }

    @Test
    public void getAlbumsFile_configPathTakesPrecedenceOverDirPath() throws Exception {
        Path defaultConfigDir = Files.createDirectories(tmp.resolve("site").resolve("config"));
        writeMinimalAlbums(defaultConfigDir, "default-id");

        Path customConfigDir = Files.createDirectory(tmp.resolve("custom-config"));
        writeMinimalAlbums(customConfigDir, "custom-id");

        Path siteDir = defaultConfigDir.getParent();
        Site site = new Site("My Site", siteDir.toString(), customConfigDir.toString());
        AlbumsFile af = site.getAlbumsFile();
        assertNotNull(af);
        assertEquals("custom-id", af.getSettings().getId());
    }

    @Test
    public void getAlbumsFile_fileAbsent_returnsNull() throws Exception {
        Path siteDir = Files.createDirectory(tmp.resolve("site"));
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
        Path configDir = Files.createDirectory(tmp.resolve("config"));
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
        Path configDir = Files.createDirectory(tmp.resolve("config"));
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
        Path configDir = Files.createDirectory(tmp.resolve("config"));
        writeMinimalAlbums(configDir, "good");

        Site site = new Site("My Site", "/irrelevant", configDir.toString());
        AlbumsFile before = site.getAlbumsFile();

        Files.writeString(configDir.resolve("albums.yaml"), ":\nnot: [valid", StandardCharsets.UTF_8);

        assertFalse(site.tryReloadAlbumsFile());
        assertSame(before, site.getAlbumsFile(), "the previous model must survive a failed reload");
        assertEquals("good", site.getAlbumsFile().getSettings().getId());
    }

    @Test
    public void tryReload_recoversOnceTheFileIsWholeAgain() throws Exception {
        Path configDir = Files.createDirectory(tmp.resolve("config"));
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
        Path siteDir = Files.createDirectory(tmp.resolve("site"));
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

    // ── addAlbum() / removeAlbum() ──────────────────────────────────────────
    // When the save fails, the in-memory album list must go back to what is on disk, so a later
    // save cannot write a change the user was told had failed.  An album with no name or source
    // does not validate, which makes the save fail.

    private static final String TWO_ALBUMS = """
            settings:
              id: my-photos

            albums:
              - slug: uganda
                name: Uganda
                source: /tmp/uganda
              - slug: kenya
                name: Kenya
                source: /tmp/kenya
            """;

    @Test
    public void addAlbum_savesIt() throws Exception {
        Path configDir = writeTwoAlbums();
        Site site = new Site("My Site", "/irrelevant", configDir.toString());

        site.addAlbum(album("peru", "Peru", "/tmp/peru"));

        assertEquals(List.of("uganda", "kenya", "peru"), slugs(site));
        assertTrue(readAlbums(configDir).contains("slug: peru"));
    }

    @Test
    public void addAlbum_saveFails_albumIsTakenBackOut() throws Exception {
        Path configDir = writeTwoAlbums();
        Site site = new Site("My Site", "/irrelevant", configDir.toString());

        assertThrows(AlbumsFileException.class, () -> site.addAlbum(album("peru", null, null)));

        assertEquals(List.of("uganda", "kenya"), slugs(site));
        assertEquals(TWO_ALBUMS, readAlbums(configDir));
    }

    @Test
    public void removeAlbum_savesIt() throws Exception {
        Path configDir = writeTwoAlbums();
        Site site = new Site("My Site", "/irrelevant", configDir.toString());

        site.removeAlbum(site.getAlbumsFile().getAlbums().getFirst());

        assertEquals(List.of("kenya"), slugs(site));
        assertFalse(readAlbums(configDir).contains("slug: uganda"));
    }

    @Test
    public void removeAlbum_saveFails_albumIsPutBackInPlace() throws Exception {
        Path configDir = writeTwoAlbums();
        Site site = new Site("My Site", "/irrelevant", configDir.toString());
        List<AlbumEntry> albums = site.getAlbumsFile().getAlbums();
        AlbumEntry uganda = albums.getFirst();
        albums.add(album("peru", null, null));   // makes every save fail

        assertThrows(AlbumsFileException.class, () -> site.removeAlbum(uganda));

        assertEquals(List.of("uganda", "kenya", "peru"), slugs(site));
        assertSame(uganda, albums.getFirst(), "the same entry goes back, at its old index");
        assertEquals(TWO_ALBUMS, readAlbums(configDir));
    }

    // ── setDirPath() / setConfigPath() ──────────────────────────────────────
    // A change that moves the site to a different albums.yaml must drop the cached model, or the
    // next save writes the old site's albums over the new site's file.

    @Test
    public void setDirPath_toAnotherSite_loadsThatSitesAlbums() throws Exception {
        Path oldConfig = Files.createDirectories(tmp.resolve("old").resolve("config"));
        Path newConfig = Files.createDirectories(tmp.resolve("new").resolve("config"));
        writeMinimalAlbums(oldConfig, "old-site");
        writeMinimalAlbums(newConfig, "new-site");

        Site site = new Site("My Site", oldConfig.getParent().toString(), null);
        assertEquals("old-site", site.getAlbumsFile().getSettings().getId());

        site.setDirPath(newConfig.getParent().toString());
        assertEquals("new-site", site.getAlbumsFile().getSettings().getId());

        site.saveAlbumsFile();
        assertTrue(readAlbums(newConfig).contains("id: new-site"));
    }

    @Test
    public void setConfigPath_toAnotherFolder_loadsThatFolder() throws Exception {
        Path oldConfig = Files.createDirectory(tmp.resolve("old-config"));
        Path newConfig = Files.createDirectory(tmp.resolve("new-config"));
        writeMinimalAlbums(oldConfig, "old-site");
        writeMinimalAlbums(newConfig, "new-site");

        Site site = new Site("My Site", "/irrelevant", oldConfig.toString());
        assertEquals("old-site", site.getAlbumsFile().getSettings().getId());

        site.setConfigPath(newConfig.toString());
        assertEquals("new-site", site.getAlbumsFile().getSettings().getId());

        site.saveAlbumsFile();
        assertTrue(readAlbums(newConfig).contains("id: new-site"));
    }

    @Test
    public void setDirPath_withConfigOverride_keepsTheModelAndUpdatesItsSiteDir() throws Exception {
        // The albums.yaml is the same file, so in-memory edits stay; only base resolution moves.
        Path config = Files.createDirectory(tmp.resolve("config"));
        writeMinimalAlbums(config, "my-site");
        Path newSiteDir = Files.createDirectory(tmp.resolve("moved"));

        Site site = new Site("My Site", tmp.resolve("site").toString(), config.toString());
        AlbumsFile before = site.getAlbumsFile();

        site.setDirPath(newSiteDir.toString());
        assertSame(before, site.getAlbumsFile());
        assertEquals(newSiteDir, before.getSiteDir());
    }

    @Test
    public void setDirPath_toTheSameValue_keepsTheModel() throws Exception {
        Path config = Files.createDirectories(tmp.resolve("site").resolve("config"));
        writeMinimalAlbums(config, "my-site");

        Site site = new Site("My Site", config.getParent().toString(), null);
        AlbumsFile before = site.getAlbumsFile();

        site.setDirPath(config.getParent().toString());
        site.setConfigPath(null);
        assertSame(before, site.getAlbumsFile());
    }

    // ── saveAlbumsFile() ────────────────────────────────────────────────────

    @Test
    public void saveAlbumsFile_refusesWhenTheModelBelongsToAnotherFile() throws Exception {
        // Backstop for any path that swaps the site's location without going through the setters.
        Path config = Files.createDirectory(tmp.resolve("config"));
        Path other = Files.createDirectory(tmp.resolve("other"));
        writeMinimalAlbums(config, "my-site");
        writeMinimalAlbums(other, "other-site");

        Site site = new Site("My Site", "/irrelevant", config.toString());
        site.getAlbumsFile().save(other.resolve("albums.yaml"));   // model now points at other/

        AlbumsFileException e = assertThrows(AlbumsFileException.class, site::saveAlbumsFile);
        assertTrue(e.getMessage().contains(other.resolve("albums.yaml").toString()), e.getMessage());
        assertEquals("settings:\n  id: my-site\n", readAlbums(config));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Path writeTwoAlbums() throws Exception {
        Path configDir = Files.createDirectory(tmp.resolve("config"));
        Files.writeString(configDir.resolve("albums.yaml"), TWO_ALBUMS, StandardCharsets.UTF_8);
        return configDir;
    }

    private static String readAlbums(Path configDir) throws Exception {
        return Files.readString(configDir.resolve("albums.yaml"), StandardCharsets.UTF_8);
    }

    private static AlbumEntry album(String slug, String name, String source) {
        AlbumEntry e = new AlbumEntry();
        e.setSlug(slug);
        e.setName(name);
        e.setSource(source);
        return e;
    }

    private static List<String> slugs(Site site) {
        return site.getAlbumsFile().getAlbums().stream().map(AlbumEntry::getSlug).toList();
    }

    private void writeMinimalAlbums(Path dir, String id) throws Exception {
        Files.writeString(dir.resolve("albums.yaml"),
                "settings:\n  id: " + id + "\n",
                StandardCharsets.UTF_8);
    }
}
