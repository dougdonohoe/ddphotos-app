package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.config.Site;

import java.util.ArrayList;
import java.util.List;

import static com.donohoedigital.ddphotos.runner.FlagVisibility.EDITABLE;

public class DeployRunner extends DdphotosRunner {

    // What 'aws' considers a valid profile name: letters, digits and the
    // punctuation AWS allows in named/SSO-generated profiles. Empty is valid (flag omitted).
    private static final String AWS_PROFILE_PATTERN = "[A-Za-z0-9._@+=,-]*";

    @Override
    public String getSubCommand() {
        return "deploy";
    }

    @Override
    public boolean showsCompletionFeedback() { return true; }

    @Override
    protected List<FlagDef> wrapperFlagDefs(Site site) {
        List<FlagDef> defs = new ArrayList<>(super.wrapperFlagDefs(site));
        defs.add(new FlagDef.FilePickerField("--site-env", "site.env", EDITABLE));
        return defs;
    }

    @Override
    protected List<FlagDef> subCommandFlagDefs(Site site) {
        return List.of(
            new FlagDef.ValidatedTextField("--aws-profile", AWS_PROFILE_PATTERN, EDITABLE, 20)
        );
    }
}
