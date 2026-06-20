package com.donohoedigital.ddphotos;

public record AlbumSettings(
        String id,
        String siteName,
        String siteUrl,
        String siteDescription,
        String copyrightOwner,
        int    copyrightYear,
        boolean allowCrawling,
        String descriptions,
        String siteTitleHtml,
        String siteSubtitleHtml,
        String siteOverviewHtml
) {}
