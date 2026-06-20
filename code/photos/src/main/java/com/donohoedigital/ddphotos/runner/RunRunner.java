package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.config.Site;

import java.util.List;

public class RunRunner extends DdphotosRunner {

    @Override
    public String getSubCommand() {
        return "run";
    }

    @Override
    public boolean showsFailureFeedback() { return true; }

    @Override
    protected boolean usesNamedContainer() { return true; }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of();
    }
}
