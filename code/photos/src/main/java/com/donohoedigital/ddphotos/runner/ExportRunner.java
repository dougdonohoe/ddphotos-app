package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.config.Site;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.donohoedigital.ddphotos.runner.FlagVisibility.EDITABLE;

public class ExportRunner extends DdphotosRunner {

    // alphanumeric, dash, underscore, period; empty = flag omitted
    private static final String SITE_ID_PATTERN = "[a-zA-Z0-9_.-]*";

    public static List<String> exportDirChoices(Site site) {
        if (site == null || site.getDirPath() == null) return List.of();
        File exportRoot = new File(site.getDirPath(), "export");
        File[] subdirs = exportRoot.listFiles(File::isDirectory);
        if (subdirs == null || subdirs.length == 0) return List.of();
        Arrays.sort(subdirs);
        return Arrays.stream(subdirs)
                     .map(d -> "export/" + d.getName())
                     .collect(Collectors.toList());
    }

    @Override
    public String getSubCommand() {
        return "export";
    }

    @Override
    public boolean showsCompletionFeedback() { return true; }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of(
            new FlagDef.BooleanFlag("--copy", EDITABLE),
            new FlagDef.BooleanFlag("--cloudflare", EDITABLE),
            new FlagDef.ValidatedTextField("--export-site-id", SITE_ID_PATTERN, EDITABLE)
        );
    }
}
