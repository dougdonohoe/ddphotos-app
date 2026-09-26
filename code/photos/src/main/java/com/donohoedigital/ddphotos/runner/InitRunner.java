package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.PhotosConstants;
import com.donohoedigital.ddphotos.config.Site;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.donohoedigital.ddphotos.runner.FlagVisibility.EDITABLE;

public class InitRunner extends DdphotosRunner {

    private final Site site_;
    private final Path targetDir_;

    public InitRunner(Path targetDir) {
        targetDir_ = targetDir;
        site_ = new Site(null, targetDir.toString(), null);
    }

    @Override
    public String getSubCommand() { return "init"; }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        // Required (the pattern rejects empty); starts as the chosen folder's name
        return List.of(
            new FlagDef.ValidatedTextField("--site-id", PhotosConstants.REGEXP_SITE_ID, EDITABLE, 240,
                    defaultSiteId(site.getDirPath()))
        );
    }

    /**
     * The folder name, made to fit {@link PhotosConstants#REGEXP_SITE_ID}: lowercased, with each
     * run of other characters replaced by a hyphen and any leading hyphens removed.
     */
    static String defaultSiteId(String dirPath) {
        if (dirPath == null || dirPath.isBlank()) return "";
        Path name = Path.of(dirPath).getFileName();
        if (name == null) return "";
        return name.toString().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("^-+", "");
    }

    @Override
    public List<String> buildCommand(Site ignored, Map<String, String> userValues) {
        return super.buildCommand(site_, userValues);
    }

    @Override
    public Process launch(Site ignored, Map<String, String> userValues) throws IOException {
        // ddphotos init requires --dir to already exist
        Files.createDirectories(targetDir_);
        return super.launch(site_, userValues);
    }
}
