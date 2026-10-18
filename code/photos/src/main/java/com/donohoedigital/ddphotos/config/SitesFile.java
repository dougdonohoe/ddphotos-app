package com.donohoedigital.ddphotos.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SitesFile extends ConfigFile {

    private static final Logger logger = LogManager.getLogger(SitesFile.class);

    private List<Site> sites = new ArrayList<>();
    private final Path path_;

    // ── public API ──────────────────────────────────────────────────────────

    public SitesFile(Path path_) {
        this.path_ = path_;
    }

    public SitesFile load()
    {
        setLoadError(null);
        try {
            if (Files.exists(path_)) {
                loadInternal();
            }
        } catch (SitesFileException e) {
            logger.warn("Failed to load sites file: {}", path_, e);
            setLoadError(e.getMessage());
        }
        return this;
    }


    SitesFile loadInternal() throws SitesFileException {
        String content;
        try {
            content = Files.readString(path_, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SitesFileException("read " + path_ + ": " + FileErrors.reason(e), e);
        }

        Object obj;
        try {
            obj = new Load(LoadSettings.builder().build()).loadFromString(content);
        } catch (YamlEngineException e) {
            throw new SitesFileException("parse " + path_ + ": " + e.getMessage(), e);
        }

        // Built aside and swapped in at the end, so a bad entry leaves none of the file's sites behind.
        List<Site> loaded = new ArrayList<>();
        if (obj instanceof Map<?, ?> root) {
            Object sitesObj = root.get("sites");
            if (sitesObj instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map<?, ?> map) {
                        Site site = siteFromMap(map);
                        if (site.getDisplayName() == null || site.getDisplayName().isBlank()) {
                            throw new SitesFileException(path_ + ": site[" + i + "]: display_name is required");
                        }
                        loaded.add(site);
                    }
                }
                Collections.sort(loaded);
            }
        }
        // In place rather than reassigned: the site combo holds this list.
        sites.clear();
        sites.addAll(loaded);
        restamp();
        return this;
    }

    public void save() throws SitesFileException {
        String refusal = saveRefusal();
        if (refusal != null) throw new SitesFileException(refusal);
        List<Map<String, String>> list = new ArrayList<>();
        for (Site site : sites) {
            Map<String, String> map = new LinkedHashMap<>();
            if (site.getDisplayName() != null) map.put("display_name", site.getDisplayName());
            if (site.getDirPath() != null) map.put("dir_path", site.getDirPath());
            if (site.getConfigPath() != null) map.put("config_path", site.getConfigPath());
            list.add(map);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("sites", list);

        DumpSettings ds = DumpSettings.builder()
                .setIndent(2)
                .setIndicatorIndent(2)
                .setIndentWithIndicator(true)
                .build();
        String yaml = new Dump(ds).dumpToString(root);
        try {
            AtomicWrite.writeString(path_, yaml);
        } catch (IOException e) {
            throw new SitesFileException("write " + path_ + ": " + FileErrors.reason(e), e);
        }
        restamp();
    }

    // ── getters / setters ───────────────────────────────────────────────────

    @Override
    public Path getPath() { return path_; }

    public List<Site> getSites() { return sites; }
    public void setSites(List<Site> sites) { this.sites = sites; }

    public void addSite(Site site) {
        int idx = Collections.binarySearch(sites, site);
        sites.add(idx >= 0 ? idx : -idx - 1, site);
    }

    /**
     * Takes out of the list every site whose {@code albums.yaml} exists but cannot be read, so the
     * app never reaches it.  Does not save.  Returns one {@code <site>: <reason>} line per site
     * removed; empty when none was.
     */
    public List<String> removeUnreadableSites() {
        List<String> removed = new ArrayList<>();
        for (Iterator<Site> it = sites.iterator(); it.hasNext(); ) {
            Site site = it.next();
            String error = site.getAlbumsFileLoadError();
            if (error != null) {
                logger.warn("Removing site '{}': {}", site.getDisplayName(), error);
                removed.add(site.getDisplayName() + ": " + error);
                it.remove();
            }
        }
        return removed;
    }

    public void sortSites() {
        Collections.sort(sites);
    }

    /**
     * Returns the existing site pointing at the same content as {@code candidate} - same site
     * folder and same effective config folder - or null if there is none.  {@code exclude} is the
     * site being edited, skipped by identity so it never matches itself.
     */
    public Site findDuplicate(Site candidate, Site exclude) {
        for (Site site : sites) {
            if (site == exclude) continue;
            if (samePath(site.getDirPath(), candidate.getDirPath())
                    && samePath(site.getActualConfigPath(), candidate.getActualConfigPath())) {
                return site;
            }
        }
        return null;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Path equality that ignores trailing separators, redundant "." segments and (on Windows) the
     * mix of separators {@link Site#getActualConfigPath} can produce.  Falls back to string
     * equality for paths this platform cannot parse - sites.yaml is hand-editable, and a bad entry
     * there must not blow up the caller.
     */
    private static boolean samePath(String a, String b) {
        boolean aBlank = (a == null || a.isEmpty());
        boolean bBlank = (b == null || b.isEmpty());
        if (aBlank || bBlank) return aBlank && bBlank;
        try {
            return Path.of(a).normalize().equals(Path.of(b).normalize());
        } catch (InvalidPathException e) {
            return a.equals(b);
        }
    }

    private static Site siteFromMap(Map<?, ?> map) {
        Site site = new Site();
        Object displayName = map.get("display_name");
        Object dirPath = map.get("dir_path");
        Object configPath = map.get("config_path");
        if (displayName instanceof String s) site.setDisplayName(s);
        if (dirPath instanceof String s) site.setDirPath(s);
        if (configPath instanceof String s) site.setConfigPath(s);
        return site;
    }
}
