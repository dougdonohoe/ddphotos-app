package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.config.Site;

import java.util.List;

public class BuildRunner extends DdphotosRunner {

    @Override
    public String getSubCommand() {
        return "build";
    }

    @Override
    public boolean showsCompletionFeedback() { return true; }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of();
    }
}
