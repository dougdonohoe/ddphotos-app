package com.donohoedigital.ddphotos.config;

import java.util.Objects;

public class AlbumsSettings {
    private String id;
    private String siteName;
    private String siteUrl;
    private String siteDescription;
    private String copyrightOwner;
    private int copyrightYear;
    private boolean allowCrawling;
    private String descriptions;
    private String passwords;
    private String css;
    private String defaultTheme;
    private String siteTitleHtml;
    private String siteSubtitleHtml;
    private String siteOverviewHtml;
    private HeroEntry hero;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }

    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }

    public String getSiteDescription() { return siteDescription; }
    public void setSiteDescription(String siteDescription) { this.siteDescription = siteDescription; }

    public String getCopyrightOwner() { return copyrightOwner; }
    public void setCopyrightOwner(String copyrightOwner) { this.copyrightOwner = copyrightOwner; }

    public int getCopyrightYear() { return copyrightYear; }
    public void setCopyrightYear(int copyrightYear) { this.copyrightYear = copyrightYear; }

    public boolean isAllowCrawling() { return allowCrawling; }
    public void setAllowCrawling(boolean allowCrawling) { this.allowCrawling = allowCrawling; }

    public String getDescriptions() { return descriptions; }
    public void setDescriptions(String descriptions) { this.descriptions = descriptions; }

    public String getPasswords() { return passwords; }
    public void setPasswords(String passwords) { this.passwords = passwords; }

    public String getCss() { return css; }
    public void setCss(String css) { this.css = css; }

    public String getDefaultTheme() { return defaultTheme; }
    public void setDefaultTheme(String defaultTheme) { this.defaultTheme = defaultTheme; }

    public String getSiteTitleHtml() { return siteTitleHtml; }
    public void setSiteTitleHtml(String siteTitleHtml) { this.siteTitleHtml = siteTitleHtml; }

    public String getSiteSubtitleHtml() { return siteSubtitleHtml; }
    public void setSiteSubtitleHtml(String siteSubtitleHtml) { this.siteSubtitleHtml = siteSubtitleHtml; }

    public String getSiteOverviewHtml() { return siteOverviewHtml; }
    public void setSiteOverviewHtml(String siteOverviewHtml) { this.siteOverviewHtml = siteOverviewHtml; }

    public HeroEntry getHero() { return hero; }
    public void setHero(HeroEntry hero) { this.hero = hero; }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof AlbumsSettings s)) return false;
        return copyrightYear == s.copyrightYear
            && allowCrawling == s.allowCrawling
            && Objects.equals(id,              s.id)
            && Objects.equals(siteName,        s.siteName)
            && Objects.equals(siteUrl,         s.siteUrl)
            && Objects.equals(siteDescription, s.siteDescription)
            && Objects.equals(copyrightOwner,  s.copyrightOwner)
            && Objects.equals(descriptions,    s.descriptions)
            && Objects.equals(passwords,       s.passwords)
            && Objects.equals(css,             s.css)
            && Objects.equals(defaultTheme,    s.defaultTheme)
            && Objects.equals(siteTitleHtml,   s.siteTitleHtml)
            && Objects.equals(siteSubtitleHtml, s.siteSubtitleHtml)
            && Objects.equals(siteOverviewHtml, s.siteOverviewHtml)
            && Objects.equals(hero,            s.hero);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, siteName, siteUrl, siteDescription, copyrightOwner,
                            copyrightYear, allowCrawling, descriptions, passwords,
                            css, defaultTheme, siteTitleHtml, siteSubtitleHtml,
                            siteOverviewHtml, hero);
    }
}
