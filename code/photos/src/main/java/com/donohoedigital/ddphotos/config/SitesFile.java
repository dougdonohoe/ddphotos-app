package com.donohoedigital.ddphotos.config;

import com.donohoedigital.base.Utils;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SitesFile {

    private static final Logger logger = LogManager.getLogger(SitesFile.class);

    private List<Site> sites = new ArrayList<>();
    private final Path path_;

    // ── public API ──────────────────────────────────────────────────────────

    public SitesFile(Path path_) {
        this.path_ = path_;
    }

    public SitesFile load()
    {
        try {
            if (Files.exists(path_)) {
                loadInternal();
            }
        } catch (SitesFileException e) {
            logger.warn("Failed to load sites file: {}{}", path_, Utils.formatExceptionText(e));
        }
        return this;
    }


    SitesFile loadInternal() throws SitesFileException {
        String content;
        try {
            content = Files.readString(path_, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SitesFileException("read " + path_ + ": " + e.getMessage(), e);
        }

        Object obj;
        try {
            obj = new Load(LoadSettings.builder().build()).loadFromString(content);
        } catch (YamlEngineException e) {
            throw new SitesFileException("parse " + path_ + ": " + e.getMessage(), e);
        }

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
                        sites.add(site);
                    }
                }
                Collections.sort(sites);
            }
        }
        return this;
    }

    public void save() throws SitesFileException {
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
            Files.writeString(path_, yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SitesFileException("write " + path_ + ": " + e.getMessage(), e);
        }
    }

    // ── getters / setters ───────────────────────────────────────────────────

    public Path getPath() { return path_; }

    public List<Site> getSites() { return sites; }
    public void setSites(List<Site> sites) { this.sites = sites; }

    public void addSite(Site site) {
        int idx = Collections.binarySearch(sites, site);
        sites.add(idx >= 0 ? idx : -idx - 1, site);
    }

    public void sortSites() {
        Collections.sort(sites);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

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
