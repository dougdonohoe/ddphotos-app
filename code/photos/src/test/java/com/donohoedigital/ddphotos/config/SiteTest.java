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
