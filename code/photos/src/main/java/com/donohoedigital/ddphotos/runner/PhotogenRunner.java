package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.config.Site;

import java.util.List;

import static com.donohoedigital.ddphotos.runner.FlagVisibility.EDITABLE;
import static com.donohoedigital.ddphotos.runner.FlagVisibility.HIDDEN;

public class PhotogenRunner extends DdphotosRunner {

    @Override
    public String getSubCommand() {
        return "photogen";
    }

    @Override
    public boolean showsCompletionFeedback() {
        return true;
    }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of(
                // pass -- so that photogen only uses our args
                new FlagDef.Constant("--", HIDDEN),
                new FlagDef.BooleanFlag("--doit", false, EDITABLE),
                new FlagDef.BooleanFlag("--index", false, EDITABLE),
                new FlagDef.BooleanFlag("--resize", false, EDITABLE),
                new FlagDef.BooleanFlag("--clean", false, EDITABLE),
                new FlagDef.BooleanFlag("--hero-only", false, EDITABLE),
                new FlagDef.BooleanFlag("--force", false, EDITABLE)
        );
    }
}
