package com.donohoedigital.ddphotos;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Pure (Swing-free, config-free) path validation shared by the album and site detail panels.
 *
 * <p>A single evaluation per field yields validity + an optional message + the resolved
 * {@link Path} (when valid), so the field's red state, the warning text, and the preview can
 * never drift apart.  WARN reddens the field and blocks Save; INFO (a Docker note) is a blue
 * note that stays valid; OK is silent.
 *
 * <p>{@link PathStatus} carries a message <em>key</em> and args rather than localized text, so
 * this class has no dependency on the config/UI layers and can be unit-tested directly.  The UI
 * layer resolves the key to text when rendering.
 */
public final class PathValidation {

    private PathValidation() {}

    public enum Severity { OK, INFO, WARN }

    public record PathStatus(Severity severity, String messageKey, Object[] args, Path resolved) {
        public boolean isValid()    { return severity != Severity.WARN; }
        public boolean hasMessage() { return messageKey != null; }

        static PathStatus ok()             { return new PathStatus(Severity.OK,   null, NO_ARGS, null); }
        static PathStatus resolved(Path p) { return new PathStatus(Severity.OK,   null, NO_ARGS, p);    }
        static PathStatus info(String key, Object... a) { return new PathStatus(Severity.INFO, key, a, null); }
        static PathStatus warn(String key, Object... a) { return new PathStatus(Severity.WARN, key, a, null); }
    }

    private static final Object[] NO_ARGS = new Object[0];

    // Mirrors allowedPhotoExtensions / allowedVideoExtensions in ddphotos (pkg/photogen/album.go,
    // pkg/photogen/video.go).  The two sets are kept apart because they are not interchangeable:
    // a hero image, for instance, accepts photos only.
    private static final Pattern IMAGE_EXTENSION =
            Pattern.compile(".*\\.(?i)(png|jpe?g|webp|tiff?|hei[cf])");

    private static final Pattern VIDEO_EXTENSION =
            Pattern.compile(".*\\.(?i)(mov|mp4|m4v)");

    /**
     * True if blank or the path ends in a recognized image extension (png, jpg, jpeg, webp, tif, tiff,
     * heic, heif).  Note this and {@link #isMediaFile} answer "not disqualified" - blank passes - for
     * the benefit of the {@code evaluate*} methods below; {@link #isVideoFile} answers the stricter
     * "this <em>is</em> a clip", since callers use it to pick an icon.
     */
    public static boolean isImageFile(String text) {
        return text == null || text.isBlank() || IMAGE_EXTENSION.matcher(text).matches();
    }

    /** True if the path ends in a recognized video extension (mov, mp4, m4v).  Blank is false. */
    public static boolean isVideoFile(String text) {
        return text != null && !text.isBlank() && VIDEO_EXTENSION.matcher(text).matches();
    }

    /** True if blank or the path is either a recognized image or a recognized video. */
    public static boolean isMediaFile(String text) {
        return isImageFile(text) || isVideoFile(text);
    }

    /**
     * Evaluates a path value against an optional base directory.  {@code kind} selects the
     * message-key family ("source" | "hero"); {@code requireImage} enforces an image extension.
     * Rules:
     * <ul>
     *   <li>blank -&gt; OK</li>
     *   <li>requireImage and not an image extension -&gt; WARN {kind}.not.image</li>
     *   <li>docker path (/ddphotos, /docker): base selected -&gt; WARN {kind}.docker.with.base;
     *       else INFO note</li>
     *   <li>base selected: absolute -&gt; WARN {kind}.must.be.relative; base dir missing -&gt;
     *       WARN base.not.found; outside base -&gt; WARN {kind}.outside.base; missing on disk -&gt;
     *       WARN {kind}.not.found; else resolved</li>
     *   <li>no base: relative -&gt; WARN {kind}.no.base; missing -&gt; WARN {kind}.not.found;
     *       else resolved</li>
     * </ul>
     */
    public static PathStatus evaluateUnderBase(String value, Path baseAbs, boolean baseSelected,
                                               boolean requireImage, String kind) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) return PathStatus.ok();

        if (requireImage && !isImageFile(v)) {
            return PathStatus.warn("msg.warn." + kind + ".not.image");
        }

        if (isDockerPath(v)) {
            return baseSelected
                    ? PathStatus.warn("msg.warn." + kind + ".docker.with.base")
                    : PathStatus.info("msg.note.source.docker", v);
        }

        Path p = Path.of(v);
        if (baseSelected) {
            if (p.isAbsolute()) {
                return PathStatus.warn("msg.warn." + kind + ".must.be.relative");
            }
            if (baseAbs == null || !baseAbs.toFile().exists()) {
                return PathStatus.warn("msg.warn.base.not.found", baseAbs);
            }
            Path resolved = baseAbs.resolve(v).normalize();
            if (!resolved.startsWith(baseAbs)) {
                return PathStatus.warn("msg.warn." + kind + ".outside.base");
            }
            if (!resolved.toFile().exists()) {
                return PathStatus.warn("msg.warn." + kind + ".not.found", v);
            }
            return PathStatus.resolved(resolved);
        } else {
            if (!p.isAbsolute()) {
                return PathStatus.warn("msg.warn." + kind + ".no.base");
            }
            if (!p.toFile().exists()) {
                return PathStatus.warn("msg.warn." + kind + ".not.found", v);
            }
            return PathStatus.resolved(p.normalize());
        }
    }

    /**
     * Evaluates a cover photo against the resolved source directory.  Cover is always relative to
     * source; when source is not resolved we stay quiet (OK) since source's own warning already
     * blocks Save - avoids a redundant second message.
     *
     * <p>A video is a legal cover: photogen uses the clip's poster frame.
     */
    public static PathStatus evaluateCover(String value, Path resolvedSource) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) return PathStatus.ok();
        if (isDockerPath(v)) {
            return PathStatus.info("msg.note.source.docker", v);
        }
        if (resolvedSource == null) return PathStatus.ok();
        if (!isMediaFile(v)) {
            return PathStatus.warn("msg.warn.cover.not.image");
        }
        Path candidate = resolvedSource.resolve(v).normalize();
        if (!candidate.startsWith(resolvedSource)) {
            return PathStatus.warn("msg.warn.cover.outside.source");
        }
        if (!candidate.toFile().exists()) {
            return PathStatus.warn("msg.warn.cover.not.found", v);
        }
        return PathStatus.resolved(candidate);
    }

    private static boolean isDockerPath(String v) {
        return v.startsWith("/ddphotos") || v.startsWith("/docker");
    }
}
