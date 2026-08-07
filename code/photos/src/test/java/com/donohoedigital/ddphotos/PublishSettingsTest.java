package com.donohoedigital.ddphotos;

import com.donohoedigital.ddphotos.PublishSettings.Step;
import com.donohoedigital.ddphotos.PublishSettings.Target;
import com.donohoedigital.ddphotos.PublishSettings.Uploader;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the publish step plan.  Exercises the record directly - the preference and
 * client.properties side of {@link PublishSettings} needs a configured app, and what matters
 * here is the order the commands come out in.
 */
public class PublishSettingsTest {

    private static List<Step> steps(boolean photogen, boolean build, Target target, Uploader uploader) {
        return new PublishSettings(photogen, build, target, uploader).steps();
    }

    @Test
    public void deployRunsThePreparationStepsFirst() {
        assertEquals(List.of(Step.PHOTOGEN, Step.BUILD, Step.DEPLOY),
                     steps(true, true, Target.DEPLOY, Uploader.WRANGLER));
    }

    @Test
    public void unwantedPreparationStepsAreSkipped() {
        assertEquals(List.of(Step.BUILD, Step.DEPLOY),
                     steps(false, true, Target.DEPLOY, Uploader.WRANGLER));
        assertEquals(List.of(Step.PHOTOGEN, Step.DEPLOY),
                     steps(true, false, Target.DEPLOY, Uploader.WRANGLER));
    }

    /** A target is always chosen, so there is always something to run. */
    @Test
    public void theTargetAloneIsAValidPlan() {
        assertEquals(List.of(Step.DEPLOY), steps(false, false, Target.DEPLOY, Uploader.WRANGLER));
    }

    @Test
    public void exportIsFollowedByItsUploader() {
        assertEquals(List.of(Step.PHOTOGEN, Step.BUILD, Step.EXPORT, Step.WRANGLER),
                     steps(true, true, Target.EXPORT, Uploader.WRANGLER));
        assertEquals(List.of(Step.PHOTOGEN, Step.BUILD, Step.EXPORT, Step.SURGE),
                     steps(true, true, Target.EXPORT, Uploader.SURGE));
    }

    /** The uploader is irrelevant unless the site is exported. */
    @Test
    public void deployIgnoresTheUploaderChoice() {
        assertEquals(steps(true, true, Target.DEPLOY, Uploader.WRANGLER),
                     steps(true, true, Target.DEPLOY, Uploader.SURGE));
    }

    /**
     * OptionRadio persists the selected radio's int value, and {@code PublishSettingsDialog}
     * uses these ordinals as those values - so reordering either enum would silently reinterpret
     * every site's stored choice.
     */
    @Test
    public void storedRadioValuesAreTheEnumOrdinals() {
        assertEquals(0, Target.DEPLOY.ordinal());
        assertEquals(1, Target.EXPORT.ordinal());
        assertEquals(0, Uploader.WRANGLER.ordinal());
        assertEquals(1, Uploader.SURGE.ordinal());
    }
}
