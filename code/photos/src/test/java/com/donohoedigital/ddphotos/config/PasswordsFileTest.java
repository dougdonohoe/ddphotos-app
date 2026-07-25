package com.donohoedigital.ddphotos.config;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class PasswordsFileTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Matches sample/config/passwords-all.yaml: header comments, site, and two albums. */
    private static final String SAMPLE_ALL =
            """
            # WARNING: This file is a DEMO ONLY and is intentionally committed for testing purposes.
            # WARNING: Do NOT commit real passwords.

            key: ddphotos-sample-key-all

            site:
              password: allgood
              hint: What say you now?

            albums:
              uganda:
                password: gorilla
                hint: A big brown ape
              antarctica:
                password: penguin
            """;

    /** Matches sample/config/passwords-uganda.yaml: no site block. */
    private static final String SAMPLE_ALBUMS_ONLY =
            """
            # WARNING: This file is a DEMO ONLY and is intentionally committed for testing purposes.
            # WARNING: Do NOT commit real passwords.

            key: ddphotos-sample-key-uganda

            albums:
              uganda:
                password: gorilla
                hint: A big brown ape
            """;

    /** Matches infra/photos/manly-man/passwords.yaml: no comments, no albums block. */
    private static final String SAMPLE_SITE_ONLY =
            """
            key: manly-man-super-secret-photos-key

            site:
              password: bobandtonics
              hint: What do Manly Men drink?
            """;

    // ── round-trip tests ────────────────────────────────────────────────────

    @Test
    public void roundTrip_isByteExact() throws Exception {
        Path f = write(SAMPLE_ALL);
        new PasswordsFile(f).load().save();
        assertEquals("unchanged round-trip must be byte-for-byte identical", SAMPLE_ALL, read(f));
    }

    @Test
    public void roundTrip_albumsOnly() throws Exception {
        Path f = write(SAMPLE_ALBUMS_ONLY);
        new PasswordsFile(f).load().save();
        assertEquals(SAMPLE_ALBUMS_ONLY, read(f));
    }

    @Test
    public void roundTrip_siteOnly() throws Exception {
        Path f = write(SAMPLE_SITE_ONLY);
        new PasswordsFile(f).load().save();
        assertEquals(SAMPLE_SITE_ONLY, read(f));
    }

    @Test
    public void roundTrip_keyOnly() throws Exception {
        String keyOnly = "key: just-a-key\n";
        Path f = write(keyOnly);
        new PasswordsFile(f).load().save();
        assertEquals(keyOnly, read(f));
    }

    @Test
    public void roundTrip_emptyFile() throws Exception {
        Path f = write("");
        PasswordsFile pf = new PasswordsFile(f).load();
        assertTrue(pf.existsOnDisk());
        assertTrue(pf.isEmpty());
        pf.save();
        assertEquals("", read(f));
    }

    @Test
    public void roundTrip_preservesComments() throws Exception {
        PasswordsFile pf = loadFixture("testdata/passwords.yaml");
        Path out = tmp.newFile("out.yaml").toPath();
        PasswordsFile copy = new PasswordsFile(out);
        // Re-save the fixture through a fresh path to confirm comments survive the node tree.
        Files.copy(pf.getPath(), out, StandardCopyOption.REPLACE_EXISTING);
        copy.load();
        copy.setSiteHint("A different hint");
        copy.save();
        String saved = read(out);
        assertTrue("header comment should survive round-trip", saved.contains("# Test fixture"));
        assertTrue("edited value should be written", saved.contains("A different hint"));
    }

    // ── load tests ──────────────────────────────────────────────────────────

    @Test
    public void load_valid() throws Exception {
        PasswordsFile pf = new PasswordsFile(write(SAMPLE_ALL)).load();

        assertEquals("ddphotos-sample-key-all", pf.getKey());
        assertTrue(pf.hasKey());

        assertTrue(pf.isSiteProtected());
        assertEquals("allgood", pf.getSitePassword());
        assertEquals("What say you now?", pf.getSiteHint());

        assertEquals(Set.of("uganda", "antarctica"), pf.getAlbumSlugs());
        assertEquals("[uganda, antarctica]", pf.getAlbumSlugs().toString()); // file order

        assertEquals("gorilla", pf.getAlbumPassword("uganda"));
        assertEquals("A big brown ape", pf.getAlbumHint("uganda"));
        assertEquals("penguin", pf.getAlbumPassword("antarctica"));
        assertNull(pf.getAlbumHint("antarctica"));
    }

    @Test
    public void load_albumProtectionFallsBackToSite() throws Exception {
        PasswordsFile pf = new PasswordsFile(write(SAMPLE_ALL)).load();

        assertTrue(pf.hasAlbumEntry("uganda"));
        assertEquals("gorilla", pf.getEffectiveAlbumPassword("uganda"));

        // No entry of its own, so it inherits the site password.
        assertFalse(pf.hasAlbumEntry("nepal"));
        assertTrue(pf.isAlbumProtected("nepal"));
        assertEquals("allgood", pf.getEffectiveAlbumPassword("nepal"));
    }

    @Test
    public void load_noSiteMeansUnlistedAlbumsAreUnprotected() throws Exception {
        PasswordsFile pf = new PasswordsFile(write(SAMPLE_ALBUMS_ONLY)).load();

        assertFalse(pf.isSiteProtected());
        assertNull(pf.getSitePassword());
        assertTrue(pf.isAlbumProtected("uganda"));
        assertFalse(pf.isAlbumProtected("nepal"));
        assertNull(pf.getEffectiveAlbumPassword("nepal"));
    }

    @Test
    public void load_missingFile() {
        try {
            new PasswordsFile(Paths.get("/nonexistent/passwords.yaml")).loadInternal();
            fail("expected PasswordsFileException");
        } catch (PasswordsFileException e) {
            assertTrue(e.getMessage().contains("read"));
        }
    }

    @Test
    public void load_invalidYaml() throws Exception {
        Path bad = write(":\nthis: [is: {not valid");
        try {
            new PasswordsFile(bad).loadInternal();
            fail("expected PasswordsFileException");
        } catch (PasswordsFileException e) {
            assertTrue(e.getMessage().contains("parse"));
        }
    }

    @Test
    public void load_rootIsNotAMapping() throws Exception {
        Path bad = write("- one\n- two\n");
        try {
            new PasswordsFile(bad).loadInternal();
            fail("expected PasswordsFileException");
        } catch (PasswordsFileException e) {
            assertTrue(e.getMessage().contains("expected mapping at root"));
        }
    }

    // ── absent-file tests ───────────────────────────────────────────────────

    @Test
    public void load_absentFileGivesEmptyModel() throws Exception {
        PasswordsFile pf = absent();
        assertFalse(pf.existsOnDisk());
        assertTrue(pf.isEmpty());
        assertFalse(pf.hasKey());
        assertNull(pf.getSitePassword());
        assertTrue(pf.getAlbumSlugs().isEmpty());
    }

    @Test
    public void save_doesNotCreateFileThatWasAbsent() throws Exception {
        PasswordsFile pf = absent();
        pf.setKey("some-key");
        pf.setSitePassword("hunter2");
        pf.save();
        assertFalse("save() must not create an absent passwords.yaml", Files.exists(pf.getPath()));
    }

    @Test
    public void saveOrCreate_doesNothingWhenEmpty() throws Exception {
        PasswordsFile pf = absent();
        pf.saveOrCreate();
        assertFalse("an untouched site must not get a stray passwords.yaml", Files.exists(pf.getPath()));
    }

    @Test
    public void saveOrCreate_writesNewFile() throws Exception {
        PasswordsFile pf = absent();
        pf.setKey("brand-new-key");
        pf.setSitePassword("hunter2");
        pf.setSiteHint("The usual");
        pf.setAlbumPassword("uganda", "gorilla");
        pf.saveOrCreate();

        assertTrue(Files.exists(pf.getPath()));
        assertTrue(pf.existsOnDisk());
        assertEquals("""
                     key: brand-new-key

                     site:
                       password: hunter2
                       hint: The usual

                     albums:
                       uganda:
                         password: gorilla
                     """, read(pf.getPath()));

        // And it reads back identically.
        PasswordsFile reloaded = new PasswordsFile(pf.getPath()).load();
        assertEquals("brand-new-key", reloaded.getKey());
        assertEquals("hunter2", reloaded.getSitePassword());
        assertEquals("gorilla", reloaded.getAlbumPassword("uganda"));
    }

    // ── mutation tests ──────────────────────────────────────────────────────

    @Test
    public void setSitePassword_blankClearsWholeSiteBlock() throws Exception {
        Path f = write(SAMPLE_ALL);
        PasswordsFile pf = new PasswordsFile(f).load();
        pf.setSitePassword("");
        assertFalse(pf.isSiteProtected());
        assertNull(pf.getSiteHint());
        pf.save();

        String saved = read(f);
        assertFalse("site block should be gone", saved.contains("site:"));
        assertFalse("orphaned site hint should be gone", saved.contains("What say you now?"));
        assertTrue("albums should be untouched", saved.contains("uganda:"));
        assertTrue("comments should survive", saved.contains("# WARNING"));
    }

    @Test
    public void setAlbumPassword_editsInPlacePreservingNeighbours() throws Exception {
        Path f = write(SAMPLE_ALL);
        PasswordsFile pf = new PasswordsFile(f).load();
        pf.setAlbumPassword("uganda", "silverback");
        pf.save();

        String saved = read(f);
        assertTrue(saved.contains("password: silverback"));
        assertTrue("existing hint untouched", saved.contains("hint: A big brown ape"));
        assertTrue("other album untouched", saved.contains("password: penguin"));
        assertTrue("comments survive an edit", saved.contains("# WARNING"));
    }

    @Test
    public void setAlbumPassword_blankRemovesEntry() throws Exception {
        Path f = write(SAMPLE_ALL);
        PasswordsFile pf = new PasswordsFile(f).load();
        pf.setAlbumPassword("uganda", null);

        assertFalse(pf.hasAlbumEntry("uganda"));
        assertTrue("removing an entry falls back to the site password", pf.isAlbumProtected("uganda"));
        pf.save();

        String saved = read(f);
        assertFalse(saved.contains("uganda:"));
        assertTrue(saved.contains("antarctica:"));
    }

    @Test
    public void removeAlbum_lastEntryDropsAlbumsKey() throws Exception {
        Path f = write(SAMPLE_ALL);
        PasswordsFile pf = new PasswordsFile(f).load();
        pf.removeAlbum("uganda");
        pf.removeAlbum("antarctica");
        pf.save();

        String saved = read(f);
        assertFalse("empty albums node should not be emitted", saved.contains("albums:"));
        assertTrue(saved.contains("site:"));
    }

    @Test
    public void addAlbum_appendsToExistingBlock() throws Exception {
        Path f = write(SAMPLE_ALL);
        PasswordsFile pf = new PasswordsFile(f).load();
        pf.setAlbumPassword("nepal", "everest");
        pf.setAlbumHint("nepal", "The big one");
        pf.save();

        PasswordsFile reloaded = new PasswordsFile(f).load();
        assertEquals("[uganda, antarctica, nepal]", reloaded.getAlbumSlugs().toString());
        assertEquals("everest", reloaded.getAlbumPassword("nepal"));
        assertEquals("The big one", reloaded.getAlbumHint("nepal"));
    }

    @Test
    public void addAlbumsBlock_toFileThatHadNone() throws Exception {
        Path f = write(SAMPLE_SITE_ONLY);
        PasswordsFile pf = new PasswordsFile(f).load();
        pf.setAlbumPassword("uganda", "gorilla");
        pf.save();

        assertEquals(SAMPLE_SITE_ONLY + """

                     albums:
                       uganda:
                         password: gorilla
                     """, read(f));
    }

    @Test
    public void addSiteBlock_toFileThatHadNone() throws Exception {
        Path f = write(SAMPLE_ALBUMS_ONLY);
        PasswordsFile pf = new PasswordsFile(f).load();
        pf.setSitePassword("allgood");
        pf.save();

        PasswordsFile reloaded = new PasswordsFile(f).load();
        assertEquals("allgood", reloaded.getSitePassword());
        String saved = read(f);
        assertTrue("site: must come before albums:", saved.indexOf("site:") < saved.indexOf("albums:"));
    }

    @Test
    public void setSiteHint_withoutPasswordDoesNotProtectSite() throws Exception {
        PasswordsFile pf = absent();
        pf.setSiteHint("");
        assertFalse(pf.isSiteProtected());
        assertTrue(pf.isEmpty());
    }

    @Test
    public void clearSite_removesBlock() throws Exception {
        PasswordsFile pf = new PasswordsFile(write(SAMPLE_ALL)).load();
        pf.clearSite();
        assertFalse(pf.isSiteProtected());
        assertNull(pf.getSite());
        assertFalse("albums with no entry lose protection too", pf.isAlbumProtected("nepal"));
    }

    // ── key tests ───────────────────────────────────────────────────────────

    @Test
    public void generateKey_usesSiteIdPrefix() {
        String key = PasswordsFile.generateKey("manly-man");
        assertTrue(key.startsWith("manly-man-"));
        assertNotEquals(key, PasswordsFile.generateKey("manly-man"));
    }

    @Test
    public void generateKey_fallsBackToBareUuid() {
        for (String siteId : new String[] { null, "", "   " }) {
            String key = PasswordsFile.generateKey(siteId);
            assertFalse(key.isBlank());
            assertEquals("bare UUID expected for site id " + siteId, 36, key.length());
        }
    }

    @Test
    public void setKey_blankClearsIt() throws Exception {
        PasswordsFile pf = absent();
        pf.setKey("  spaced-key  ");
        assertEquals("spaced-key", pf.getKey());
        pf.setKey("");
        assertFalse(pf.hasKey());
        assertNull(pf.getKey());
    }

    // ── validation tests ────────────────────────────────────────────────────

    @Test
    public void validate_keyOnlyIsValid() throws Exception {
        PasswordsFile pf = absent();
        pf.setKey("just-a-key");
        pf.validate();
    }

    @Test
    public void validate_emptyIsValid() throws Exception {
        absent().validate();
    }

    @Test
    public void validate_keyRequiredWhenSiteEncrypted() throws Exception {
        PasswordsFile pf = absent();
        pf.setSitePassword("hunter2");
        assertValidationFails(pf, "key is required");
    }

    @Test
    public void validate_keyRequiredWhenAlbumEncrypted() throws Exception {
        PasswordsFile pf = absent();
        pf.setAlbumPassword("uganda", "gorilla");
        assertValidationFails(pf, "key is required");
    }

    @Test
    public void validate_sitePasswordTooShort() throws Exception {
        PasswordsFile pf = absent();
        pf.setKey("a-key");
        pf.setSitePassword("shrt");
        assertValidationFails(pf, "site: password must be at least 5 characters");
    }

    @Test
    public void validate_albumPasswordTooShort() throws Exception {
        PasswordsFile pf = absent();
        pf.setKey("a-key");
        pf.setAlbumPassword("uganda", "abcd");
        assertValidationFails(pf, "album \"uganda\": password must be at least 5 characters");
    }

    @Test
    public void validate_albumHintWithoutPasswordIsRejected() throws Exception {
        // photogen rejects an album entry with an empty password: it silently overrides the
        // site password with no encryption.  A hand-edited file can contain one.
        PasswordsFile pf = new PasswordsFile(write("""
                key: a-key

                albums:
                  uganda:
                    hint: A big brown ape
                """)).load();
        assertTrue(pf.hasAlbumEntry("uganda"));
        assertValidationFails(pf, "album \"uganda\": password is required");
    }

    @Test
    public void save_rejectsInvalidAndLeavesFileUntouched() throws Exception {
        Path f = write(SAMPLE_ALL);
        PasswordsFile pf = new PasswordsFile(f).load();
        pf.setAlbumPassword("uganda", "shrt");
        try {
            pf.save();
            fail("expected PasswordsFileException");
        } catch (PasswordsFileException e) {
            assertTrue(e.getMessage().contains("must be at least"));
        }
        assertEquals("a rejected save must not touch the file", SAMPLE_ALL, read(f));
    }

    // ── real-file round-trip ────────────────────────────────────────────────

    /**
     * Round-trips the real passwords files found on this machine, asserting the saved copy is
     * byte-for-byte identical.  Each file is copied into the temp folder first — the originals
     * are never written to.  Skipped when none are present (CI).
     */
    @Test
    public void roundTripRealFiles() throws Exception {
        Map<String, Path> files = new LinkedHashMap<>();
        files.put("sample-all",    Paths.get("/Users/donohoe/work/ddphotos/sample/config/passwords-all.yaml"));
        files.put("sample-uganda", Paths.get("/Users/donohoe/work/ddphotos/sample/config/passwords-uganda.yaml"));
        files.put("docker-init",   Paths.get("/Users/donohoe/work/ddphotos/docker/init/passwords.yaml"));
        files.put("manly-man",     Paths.get("/Users/donohoe/work/infra/photos/manly-man/passwords.yaml"));

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

            Path copy = tmp.newFolder(name).toPath().resolve(PasswordsFile.FILE_NAME);
            Files.copy(originalPath, copy);
            String original = read(copy);

            new PasswordsFile(copy).load().save();

            if (original.equals(read(copy))) {
                System.out.printf("[%s]  ✓ identical (%s)%n", name, originalPath);
            } else {
                System.out.printf("[%s]  ✗ differs (%s)%n", name, originalPath);
                failures.add(name);
            }
        }

        if (!failures.isEmpty()) {
            fail("Round-trip differences in: " + String.join(", ", failures) + " — see stdout above");
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Path write(String content) throws Exception {
        Path f = tmp.newFolder().toPath().resolve(PasswordsFile.FILE_NAME);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** A PasswordsFile pointing at a path that does not exist. */
    private PasswordsFile absent() throws Exception {
        return new PasswordsFile(tmp.newFolder().toPath().resolve(PasswordsFile.FILE_NAME)).load();
    }

    private PasswordsFile loadFixture(String resourcePath) throws Exception {
        URL url = getClass().getClassLoader().getResource(resourcePath);
        assertNotNull("test resource not found: " + resourcePath, url);
        return new PasswordsFile(Paths.get(url.toURI())).load();
    }

    private void assertValidationFails(PasswordsFile pf, String expectedMessage) {
        try {
            pf.validate();
            fail("expected PasswordsFileException: " + expectedMessage);
        } catch (PasswordsFileException e) {
            assertTrue("expected message containing \"" + expectedMessage + "\", got: " + e.getMessage(),
                       e.getMessage().contains(expectedMessage));
        }
    }
}
