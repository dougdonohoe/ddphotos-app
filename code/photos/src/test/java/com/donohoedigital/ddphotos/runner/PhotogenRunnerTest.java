package com.donohoedigital.ddphotos.runner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PhotogenRunnerTest {

    @Test
    public void syncFlags_offByDefault_passedWhenChecked() {
        List<FlagDef> defs = new PhotogenRunner().subCommandFlagDefs(null);
        for (String name : List.of("--sync-only", "--no-sync")) {
            FlagDef def = defs.stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
            assertEquals(List.of(), def.toArgs("false"), name);
            assertEquals(List.of(name), def.toArgs("true"), name);
        }
    }
}
