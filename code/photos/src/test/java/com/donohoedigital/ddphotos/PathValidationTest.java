package com.donohoedigital.ddphotos;

import com.donohoedigital.ddphotos.PathValidation.PathStatus;
import com.donohoedigital.ddphotos.PathValidation.Severity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;

import static com.donohoedigital.ddphotos.PathValidation.evaluateCover;
import static com.donohoedigital.ddphotos.PathValidation.evaluateUnderBase;
import static org.junit.Assert.*;

/**
 * Unit tests for the path-validation rule engine.  No Swing or config dependency - exercises
 * the rules directly with on-disk fixtures from a {@link TemporaryFolder}.
 */
public class PathValidationTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path base;        // an existing base directory
    private Path sourceDir;   // base/source, an existing source directory

    @org.junit.Before
    public void setUp() throws IOException {
        base = tmp.newFolder("base").toPath();
        sourceDir = tmp.newFolder("base", "source").toPath();
    }

    // ── isImageFile ─────────────────────────────────────────────────────────

    @Test
    public void isImageFile_recognizesExtensions() {
        assertTrue(PathValidation.isImageFile("a.jpg"));
        assertTrue(PathValidation.isImageFile("a.JPEG"));
        assertTrue(PathValidation.isImageFile("a.png"));
        assertTrue(PathValidation.isImageFile("a.webp"));
        assertTrue(PathValidation.isImageFile("a.heic"));
        assertTrue(PathValidation.isImageFile(""));     // blank is permissive
        assertTrue(PathValidation.isImageFile(null));
        assertFalse(PathValidation.isImageFile("README.md"));
        assertFalse(PathValidation.isImageFile("notes.txt"));
    }

    // ── blank ───────────────────────────────────────────────────────────────

    @Test
    public void blank_isOkAndSilent() {
        PathStatus s = evaluateUnderBase("  ", base, true, false, "source");
        assertEquals(Severity.OK, s.severity());
        assertTrue(s.isValid());
        assertFalse(s.hasMessage());
        assertNull(s.resolved());
    }

    // ── base selected ─────────────────────────────────────────────────────────

    @Test
    public void baseSelected_validRelative_resolves() {
        PathStatus s = evaluateUnderBase("source", base, true, false, "source");
        assertTrue(s.isValid());
        assertFalse(s.hasMessage());
        assertEquals(sourceDir, s.resolved());
    }

    @Test
    public void baseSelected_absolute_mustBeRelative() {
        PathStatus s = evaluateUnderBase("/abs/path", base, true, false, "source");
        assertWarn(s, "msg.warn.source.must.be.relative");
        assertNull(s.resolved());
    }

    @Test
    public void baseSelected_baseMissing_baseNotFound() {
        Path missing = base.resolve("does-not-exist");
        PathStatus s = evaluateUnderBase("source", missing, true, false, "source");
        assertWarn(s, "msg.warn.base.not.found");
        // arg carries the missing base path for the message
        assertArrayEquals(new Object[]{missing}, s.args());
    }

    @Test
    public void baseSelected_outsideBase_warns() {
        PathStatus s = evaluateUnderBase("../escape", base, true, false, "source");
        assertWarn(s, "msg.warn.source.outside.base");
    }

    @Test
    public void baseSelected_relativeNotFound_warns() {
        PathStatus s = evaluateUnderBase("missing-sub", base, true, false, "source");
        assertWarn(s, "msg.warn.source.not.found");
    }

    // ── no base ───────────────────────────────────────────────────────────────

    @Test
    public void noBase_relative_warnsNoBase() {
        PathStatus s = evaluateUnderBase("relative/path", null, false, false, "source");
        assertWarn(s, "msg.warn.source.no.base");
    }

    @Test
    public void noBase_absoluteExisting_resolves() {
        PathStatus s = evaluateUnderBase(sourceDir.toString(), null, false, false, "source");
        assertTrue(s.isValid());
        assertEquals(sourceDir, s.resolved());
    }

    @Test
    public void noBase_absoluteMissing_warns() {
        PathStatus s = evaluateUnderBase("/no/such/path", null, false, false, "source");
        assertWarn(s, "msg.warn.source.not.found");
    }

    // ── docker (rule 4) ─────────────────────────────────────────────────────────

    @Test
    public void docker_noBase_isInfoNote() {
        for (String p : new String[]{"/ddphotos/x.jpg", "/docker/x.jpg"}) {
            PathStatus s = evaluateUnderBase(p, null, false, true, "hero");
            assertEquals(p, Severity.INFO, s.severity());
            assertTrue("docker note stays valid", s.isValid());
            assertEquals("msg.note.source.docker", s.messageKey());
            assertNull("cannot preview a container path", s.resolved());
        }
    }

    @Test
    public void docker_withBase_warns() {
        PathStatus s = evaluateUnderBase("/ddphotos/x.jpg", base, true, true, "hero");
        assertWarn(s, "msg.warn.hero.docker.with.base");
    }

    // ── image requirement ───────────────────────────────────────────────────────

    @Test
    public void requireImage_nonImage_warns() {
        PathStatus s = evaluateUnderBase("README.md", null, false, true, "hero");
        assertWarn(s, "msg.warn.hero.not.image");
    }

    @Test
    public void requireImage_off_allowsNonImage() {
        // source is a folder - no image requirement
        PathStatus s = evaluateUnderBase("source", base, true, false, "source");
        assertTrue(s.isValid());
    }

    // ── cover ───────────────────────────────────────────────────────────────────

    @Test
    public void cover_blank_isOk() {
        assertTrue(evaluateCover("  ", sourceDir).isValid());
        assertFalse(evaluateCover("  ", sourceDir).hasMessage());
    }

    @Test
    public void cover_sourceUnresolved_staysQuiet() {
        // source not resolved -> cover stays OK (source's own warning already blocks Save)
        PathStatus s = evaluateCover("cover.jpg", null);
        assertEquals(Severity.OK, s.severity());
        assertFalse(s.hasMessage());
    }

    @Test
    public void cover_docker_isInfoNote() {
        PathStatus s = evaluateCover("/ddphotos/c.jpg", sourceDir);
        assertEquals(Severity.INFO, s.severity());
        assertTrue(s.isValid());
        assertEquals("msg.note.source.docker", s.messageKey());
    }

    @Test
    public void cover_nonImage_warns() throws IOException {
        tmp.newFile("base/source/README.md");
        PathStatus s = evaluateCover("README.md", sourceDir);
        assertWarn(s, "msg.warn.cover.not.image");
    }

    @Test
    public void cover_outsideSource_warns() {
        PathStatus s = evaluateCover("../escape.jpg", sourceDir);
        assertWarn(s, "msg.warn.cover.outside.source");
    }

    @Test
    public void cover_notFound_warns() {
        PathStatus s = evaluateCover("missing.jpg", sourceDir);
        assertWarn(s, "msg.warn.cover.not.found");
    }

    @Test
    public void cover_valid_resolves() throws IOException {
        Path file = tmp.newFile("base/source/cover.jpg").toPath();
        PathStatus s = evaluateCover("cover.jpg", sourceDir);
        assertTrue(s.isValid());
        assertFalse(s.hasMessage());
        assertEquals(file, s.resolved());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void assertWarn(PathStatus s, String expectedKey) {
        assertEquals("severity", Severity.WARN, s.severity());
        assertFalse("WARN must be invalid", s.isValid());
        assertTrue("WARN must carry a message", s.hasMessage());
        assertEquals("message key", expectedKey, s.messageKey());
    }
}
