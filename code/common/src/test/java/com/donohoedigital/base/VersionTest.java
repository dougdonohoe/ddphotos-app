package com.donohoedigital.base;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for parsing and ordering versions - what {@code UpdateCheck} in the photos module
 * relies on when it compares a GitHub release tag against the running build.
 */
public class VersionTest
{
    // -------------------------------------------------------------------------
    // parse / toString round-trip
    // -------------------------------------------------------------------------

    @Test
    public void parse_roundTripsToString()
    {
        assertRoundTrip("1.0.6");
        assertRoundTrip("1.0.0b8");
        assertRoundTrip("2.1.0a3");
        assertRoundTrip("1.0.6_en");
        assertRoundTrip("10.20.30");
        assertRoundTrip("1.0.0b12_fr");
    }

    private static void assertRoundTrip(String s)
    {
        Version v = Version.parse(s);
        assertTrue("did not parse: " + s, v != null);
        assertEquals(s, v.toString());
    }

    @Test
    public void parse_readsTheParts()
    {
        Version beta = Version.parse("1.2.3b4");
        assertEquals(1, beta.getMajor());
        assertEquals(2, beta.getMinor());
        assertEquals(3, beta.getPatch());
        assertEquals(4, beta.getAlphaBetaVersion());
        assertTrue(beta.isBeta());
        assertFalse(beta.isAlpha());
        assertFalse(beta.isProduction());

        Version alpha = Version.parse("1.2.3a4");
        assertTrue(alpha.isAlpha());
        assertFalse(alpha.isBeta());

        Version prod = Version.parse("1.2.3");
        assertTrue(prod.isProduction());
        assertEquals(0, prod.getAlphaBetaVersion());
        assertNull(prod.getLocale());

        assertEquals("en", Version.parse("1.2.3_en").getLocale());
    }

    /** A leading "v" is tolerated, since tags elsewhere are often written that way. */
    @Test
    public void parse_toleratesVPrefix()
    {
        assertEquals(Version.parse("1.0.6"), Version.parse("v1.0.6"));
    }

    @Test
    public void parse_trimsWhitespace()
    {
        assertEquals(Version.parse("1.0.6"), Version.parse("  1.0.6\n"));
    }

    /** Anything that is not a version returns null rather than throwing - see Version.parse. */
    @Test
    public void parse_rejectsNonVersions()
    {
        assertNull(Version.parse(null));
        assertNull(Version.parse(""));
        assertNull(Version.parse("   "));
        assertNull(Version.parse("1.0"));
        assertNull(Version.parse("1"));
        assertNull(Version.parse("latest"));
        assertNull(Version.parse("1.0.x"));
        assertNull(Version.parse("v1.2"));
        assertNull(Version.parse("1.0.0c1"));
        assertNull(Version.parse("1.0.0b"));
        assertNull(Version.parse("1.0.6.1"));
        assertNull(Version.parse("<!DOCTYPE html><html lang=\"en\">"));
        assertNull(Version.parse("releases/tag/1.0.6"));
        // too big for an int
        assertNull(Version.parse("99999999999.0.0"));
    }

    // -------------------------------------------------------------------------
    // Ordering
    // -------------------------------------------------------------------------

    @Test
    public void isNewerThan_comparesNumbers()
    {
        assertNewer("1.0.7", "1.0.6");
        assertNewer("1.1.0", "1.0.9");
        assertNewer("2.0.0", "1.9.9");
        assertNewer("1.0.10", "1.0.9");
    }

    /** An alpha or beta of a version comes before the version itself. */
    @Test
    public void isNewerThan_ranksPrereleases()
    {
        assertNewer("1.0.0", "1.0.0b8");
        assertNewer("1.0.0", "1.0.0a1");
        assertNewer("1.0.0b8", "1.0.0b7");
        assertNewer("1.0.0b1", "1.0.0a9");
        // the release type only breaks a tie on the numbers
        assertNewer("1.0.1b1", "1.0.0");
    }

    @Test
    public void isNewerThan_isFalseForSameOrOlder()
    {
        assertFalse(Version.parse("1.0.6").isNewerThan(Version.parse("1.0.6")));
        assertFalse(Version.parse("1.0.0b8").isNewerThan(Version.parse("1.0.0b8")));
        assertFalse(Version.parse("1.0.5").isNewerThan(Version.parse("1.0.6")));
    }

    /** A failed lookup hands back null; that must not read as "newer". */
    @Test
    public void isNewerThan_isFalseForNull()
    {
        assertFalse(Version.parse("9.9.9").isNewerThan(null));
    }

    /** The locale says who a build is for, not when it is from. */
    @Test
    public void ordering_ignoresLocale()
    {
        assertEquals(Version.parse("1.0.6"), Version.parse("1.0.6_fr"));
        assertFalse(Version.parse("1.0.6_fr").isNewerThan(Version.parse("1.0.6_en")));
    }

    /**
     * The real ladder DD Photos has shipped (the history in {@code PhotosConstants}, oldest
     * first).  Shuffled and re-sorted, it has to come back in the same order.
     */
    @Test
    public void ordering_reproducesShippedHistory()
    {
        List<String> oldestFirst = List.of(
                "1.0.0b1", "1.0.0b2", "1.0.0b3", "1.0.0b4", "1.0.0b5", "1.0.0b6", "1.0.0b7",
                "1.0.0b8", "1.0.0", "1.0.1", "1.0.2", "1.0.3", "1.0.4", "1.0.5", "1.0.6");

        List<Version> shuffled = new ArrayList<>(oldestFirst.stream().map(Version::parse).toList());
        Collections.shuffle(shuffled);
        Collections.sort(shuffled);

        assertEquals(oldestFirst, shuffled.stream().map(Version::toString).toList());
    }

    @Test
    public void equalsAndHashCode_agreeWithCompareTo()
    {
        Version a = Version.parse("1.0.6");
        Version b = new Version(1, 0, 6);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        assertFalse(a.equals(Version.parse("1.0.7")));
        assertFalse(a.equals("1.0.6"));
        assertFalse(a.equals(null));
    }

    private static void assertNewer(String newer, String older)
    {
        Version n = Version.parse(newer);
        Version o = Version.parse(older);
        assertTrue(newer + " should be newer than " + older, n.isNewerThan(o));
        assertFalse(older + " should not be newer than " + newer, o.isNewerThan(n));
    }
}
