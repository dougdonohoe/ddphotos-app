package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.config.Site;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
    protected List<FlagDef> subCommandFlagDefs(Site site) { return List.of(); }

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
