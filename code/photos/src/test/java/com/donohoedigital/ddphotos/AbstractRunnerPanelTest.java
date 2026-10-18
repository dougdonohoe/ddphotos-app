package com.donohoedigital.ddphotos;

import com.donohoedigital.config.ApplicationType;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.config.StylesConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.runner.CommandRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** How a finished run is classified as a user stop.  No UI is built and no process is started. */
public class AbstractRunnerPanelTest {

    @BeforeAll
    public static void initConfig() {
        // Enough for the panel to be constructed: it reads colors and tooltip settings.
        new PropertyConfig("test", new String[0], ApplicationType.COMMAND_LINE, null, false);
        new StylesConfig(new String[]{"common", "ddphotos"});
    }

    private static final class TestPanel extends AbstractRunnerPanel {
        TestPanel() { super(null); }
        @Override protected Site getCurrentSite() { return null; }
        @Override protected CommandRunner resolveRunner() { return null; }
        @Override protected void onRun() {}
        @Override protected void launchMainCommand(Map<String, String> userValues) {}
    }

    @Test
    void notStoppedByTheUser_isNotAUserStop() {
        // Whatever the exit code: 137 (SIGKILL, for example from the out-of-memory killer) and
        // 143 (SIGTERM) are failures when the user did not press Stop or Kill.
        assertFalse(new TestPanel().wasUserStop());
    }

    @Test
    void stopAndKill_countAsAUserStop() {
        TestPanel panel = new TestPanel();
        panel.onStop();
        assertTrue(panel.wasUserStop());
        panel.clearUserStop();
        panel.onKill();
        assertTrue(panel.wasUserStop());
    }

    @Test
    void clearUserStop_endsTheStop() {
        TestPanel panel = new TestPanel();
        panel.onStop();
        panel.clearUserStop();
        assertFalse(panel.wasUserStop());
    }
}
