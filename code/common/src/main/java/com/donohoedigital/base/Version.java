/*
 * Version.java
 *
 * Created on June 24, 2003, 5:12 PM
 */

package com.donohoedigital.base;

/**
 * @author donohoe
 */
public class Version
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
}
