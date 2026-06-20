package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.config.Site;

import java.util.List;

public class ServeRunner extends DdphotosRunner {

    @Override
    public String getSubCommand() {
        return "serve";
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
