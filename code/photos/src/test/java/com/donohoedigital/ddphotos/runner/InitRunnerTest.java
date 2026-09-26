package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.PhotosConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InitRunnerTest {

    @Test
    public void defaultSiteId_isFolderName() {
        assertEquals("my-photos", InitRunner.defaultSiteId("/Users/me/my-photos"));
    }

    @Test
    public void defaultSiteId_fitsSiteIdPattern() {
        for (String dir : new String[]{"/tmp/My_Photos.2026", "/tmp/.hidden", "/tmp/--x"}) {
            String id = InitRunner.defaultSiteId(dir);
            assertTrue(id.matches(PhotosConstants.REGEXP_SITE_ID), dir + " -> " + id);
        }
        assertEquals("my-photos-2026", InitRunner.defaultSiteId("/tmp/My_Photos.2026"));
    }

    @Test
    public void defaultSiteId_emptyWithoutFolder() {
        assertEquals("", InitRunner.defaultSiteId(null));
        assertEquals("", InitRunner.defaultSiteId(""));
        assertEquals("", InitRunner.defaultSiteId("/"));
    }
}
