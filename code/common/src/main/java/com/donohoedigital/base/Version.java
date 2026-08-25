/*
 * Version.java
 *
 * Created on June 24, 2003, 5:12 PM
 */

package com.donohoedigital.base;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author donohoe
 */
public class Version implements Comparable<Version>
{
    private int nMajor_;
    private int nMinor_;
    private int nPatch_;
    private boolean bBeta_;
    private boolean bAlpha_;
    private int nAlphaBetaVersion_;
    private String sLocale_;

    public static final int TYPE_PRODUCTION = 0;
    public static final int TYPE_ALPHA = 1;
    public static final int TYPE_BETA = 2;

    /**
     * Empty needed for demarshal
     */
    public Version()
    {
    }

    /**
     * Creates a new instance of Version
     */
    public Version(int nMajor, int nMinor, int nPatch)
    {
        this(TYPE_PRODUCTION, nMajor, nMinor, 0, nPatch);
    }

    /**
     * Creates a new instance of Version
     */
    public Version(int nType, int nMajor, int nMinor, int nAlphaBetaVersion, int nPatchVersion)
    {
        nMajor_ = nMajor;
        nMinor_ = nMinor;
        nPatch_ = nPatchVersion;
        bBeta_ = nType == TYPE_BETA;
        bAlpha_ = nType == TYPE_ALPHA;
        nAlphaBetaVersion_ = nAlphaBetaVersion;
    }

    public int getMajor()
    {
        return nMajor_;
    }

    public String getMajorAsString()
    {
        return String.valueOf(nMajor_);
    }

    public int getMinor()
    {
        return nMinor_;
    }

    public int getPatch()
    {
        return nPatch_;
    }

    public boolean isAlpha()
    {
        return bAlpha_;
    }

    public boolean isBeta()
    {
        return bBeta_;
    }

    public boolean isProduction()
    {
        return !bAlpha_ && !bBeta_;
    }

    public int getAlphaBetaVersion()
    {
        return nAlphaBetaVersion_;
    }

    public String getLocale()
    {
        return sLocale_;
    }

    public void setLocale(String s)
    {
        sLocale_ = s;
    }

    @Override
    public String toString()
    {
        return nMajor_ + "." + nMinor_ + "." + nPatch_ + (bAlpha_ | bBeta_ ? (bAlpha_ ? "a" : "b") + nAlphaBetaVersion_ : "") +
               (sLocale_ != null ? "_" + sLocale_ : "");
    }

    /** Mirrors {@link #toString}: major.minor.patch, an optional a/b number, an optional locale. */
    private static final Pattern PARSE =
            Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:([ab])(\\d+))?(?:_(.+))?$");

    /**
     * The inverse of {@link #toString}: "1.0.6", "1.0.0b8", "2.1.0a3", "1.0.6_en".  A leading
     * "v" is tolerated, for version strings that come from elsewhere (a git tag, say).
     *
     * @return the parsed version, or null if the string is not one.  Never throws - callers
     * parse text they did not produce.
     */
    public static Version parse(String s)
    {
        if (s == null) return null;

        Matcher m = PARSE.matcher(s.trim());
        if (!m.matches()) return null;

        try
        {
            String sType = m.group(4);
            int nType = sType == null ? TYPE_PRODUCTION : ("a".equals(sType) ? TYPE_ALPHA : TYPE_BETA);
            int nAlphaBeta = sType == null ? 0 : Integer.parseInt(m.group(5));

            Version v = new Version(nType,
                                    Integer.parseInt(m.group(1)),
                                    Integer.parseInt(m.group(2)),
                                    nAlphaBeta,
                                    Integer.parseInt(m.group(3)));
            v.setLocale(m.group(6));
            return v;
        }
        catch (NumberFormatException e)
        {
            // a part too big for an int - not a version we know how to compare
            return null;
        }
    }

    /**
     * Newest last: major, then minor, then patch, then the release type, then the alpha/beta
     * number.  An alpha or beta of a version comes before the version itself - 1.0.0a3, then
     * 1.0.0b1, then 1.0.0 - which is the order this project has actually shipped in (see the
     * history in PhotosConstants).  The locale plays no part; it says who a build is for, not
     * when it is from.
     */
    @Override
    public int compareTo(Version o)
    {
        if (nMajor_ != o.nMajor_) return Integer.compare(nMajor_, o.nMajor_);
        if (nMinor_ != o.nMinor_) return Integer.compare(nMinor_, o.nMinor_);
        if (nPatch_ != o.nPatch_) return Integer.compare(nPatch_, o.nPatch_);
        if (typeRank() != o.typeRank()) return Integer.compare(typeRank(), o.typeRank());
        return Integer.compare(nAlphaBetaVersion_, o.nAlphaBetaVersion_);
    }

    /** Alpha before beta before production - see {@link #compareTo}. */
    private int typeRank()
    {
        if (bAlpha_) return 0;
        if (bBeta_) return 1;
        return 2;
    }

    /**
     * True if this version is a later release than the given one.  False when they are the same
     * version, so a check for a newer build does not report the one already running, and false
     * for a null other, which is what a failed lookup hands back.
     */
    public boolean isNewerThan(Version other)
    {
        return other != null && compareTo(other) > 0;
    }

    /** Consistent with {@link #compareTo}, so the locale is left out of this too. */
    @Override
    public boolean equals(Object o)
    {
        return o instanceof Version v && compareTo(v) == 0;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(nMajor_, nMinor_, nPatch_, typeRank(), nAlphaBetaVersion_);
    }
}
