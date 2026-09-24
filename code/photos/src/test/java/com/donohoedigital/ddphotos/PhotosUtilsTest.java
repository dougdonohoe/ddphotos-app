package com.donohoedigital.ddphotos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link PhotosUtils#renameSyncFolder} on the paths that succeed, so no error dialog (and
 * no app context) is needed, and {@link PhotosUtils#slugify}.
 */
public class PhotosUtilsTest {

    @TempDir
    Path tmp;

    private List<String> names() throws Exception {
        try (Stream<Path> s = Files.list(tmp)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    public void renameSyncFolder_movesFolderAndContents() throws Exception {
        Files.createDirectories(tmp.resolve("old"));
        Files.writeString(tmp.resolve("old").resolve("metadata.yaml"), "x");

        PhotosUtils.renameSyncFolder(null, tmp.resolve("old"), tmp.resolve("new"));

        assertEquals(List.of("new"), names(), "no temporary folder is left behind");
        assertTrue(Files.exists(tmp.resolve("new").resolve("metadata.yaml")));
    }

    @Test
    public void renameSyncFolder_caseOnly() throws Exception {
        // On a case-insensitive file system (the macOS default) the target "exists" already,
        // because it is the source.  The listing shows the name actually stored on disk.
        Files.createDirectories(tmp.resolve("Trip"));
        Files.writeString(tmp.resolve("Trip").resolve("metadata.yaml"), "x");

        PhotosUtils.renameSyncFolder(null, tmp.resolve("Trip"), tmp.resolve("trip"));

        assertEquals(List.of("trip"), names());
        assertTrue(Files.exists(tmp.resolve("trip").resolve("metadata.yaml")));
    }

    @Test
    public void slugify() {
        assertEquals("march-s-pictures-2002", PhotosUtils.slugify("March's Pictures 2002"));
        assertEquals("hi", PhotosUtils.slugify("  --Hi!! "));
        assertEquals("", PhotosUtils.slugify("!!!"));
        assertEquals("", PhotosUtils.slugify(null));
    }

    @Test
    public void slugify_ignoresTheDefaultLocale() {
        // Lowercased with the Turkish locale, "I" becomes a dotless i, which is not a-z.
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals("title", PhotosUtils.slugify("TITLE"));
        } finally {
            Locale.setDefault(saved);
        }
    }
}
