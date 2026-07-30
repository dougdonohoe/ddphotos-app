package com.donohoedigital.ddphotos.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * {@link TextFile}'s own behaviour is covered by {@link CssFileTest}; this pins down the bits
 * specific to {@code site.env} - its name, and the editor flow of creating one for a site that
 * has none.
 */
public class SiteEnvFileTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Matches ddphotos/sample/config/site.env in shape. */
    private static final String SAMPLE =
            """
            RSYNC_HOST=user@your-server.example.com
            RSYNC_DEST=/path/to/your/web/root/

            # optional
            #CLOUDFRONT_ID=ABC123
            """;

    @Test
    public void fileNameMatchesWhatDeployLooksFor() {
        assertEquals("site.env", SiteEnvFile.FILE_NAME);
    }

    @Test
    public void roundTrip_isByteExact() throws Exception {
        Path f = write(SAMPLE);
        new SiteEnvFile(f).load().save();
        assertEquals(SAMPLE, read(f));
    }

    @Test
    public void load_readsExistingFile() throws Exception {
        SiteEnvFile env = new SiteEnvFile(write(SAMPLE)).load();
        assertTrue(env.existsOnDisk());
        assertEquals(SAMPLE, env.getContent());
    }

    @Test
    public void load_absentFileGivesEmptyModel() throws Exception {
        SiteEnvFile env = absent();
        assertFalse(env.existsOnDisk());
        assertEquals("", env.getContent());
        assertTrue(env.isEmpty());
    }

    /** Opening the editor on a site with no site.env and closing it again must leave no file. */
    @Test
    public void saveOrCreate_doesNothingWhenNothingWasTyped() throws Exception {
        SiteEnvFile env = absent();
        env.saveOrCreate();
        assertFalse(Files.exists(env.getPath()));
    }

    @Test
    public void saveOrCreate_createsFileForSiteThatHadNone() throws Exception {
        SiteEnvFile env = absent();
        env.setContent("S3_BUCKET=my-bucket");
        env.saveOrCreate();

        assertTrue(Files.exists(env.getPath()));
        assertEquals("S3_BUCKET=my-bucket\n", read(env.getPath()));
    }

    @Test
    public void save_writesEditedContent() throws Exception {
        Path f = write(SAMPLE);
        SiteEnvFile env = new SiteEnvFile(f).load();
        env.setContent(env.getContent().replace("/path/to/your/web/root/", "/srv/www/"));
        env.save();
        assertTrue(read(f).contains("RSYNC_DEST=/srv/www/"));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Path write(String content) throws Exception {
        Path f = tmp.newFolder().toPath().resolve(SiteEnvFile.FILE_NAME);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** A SiteEnvFile pointing at a path that does not exist. */
    private SiteEnvFile absent() throws Exception {
        return new SiteEnvFile(tmp.newFolder().toPath().resolve(SiteEnvFile.FILE_NAME)).load();
    }
}
