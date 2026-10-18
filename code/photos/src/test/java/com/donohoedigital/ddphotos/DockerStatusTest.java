package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Utils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class DockerStatusTest {

    @Test
    public void runCheck_killsCommandThatTimesOut() throws Exception {
        assumeFalse(Utils.ISWINDOWS, "uses sleep");
        assertFalse(DockerStatus.runCheck(new ProcessBuilder("sleep", "30"), 200));

        // destroyForcibly() returns before the process is gone, so give it a moment.
        long deadline = System.currentTimeMillis() + 2_000;
        List<ProcessHandle> left;
        do {
            left = ProcessHandle.current().children().filter(ProcessHandle::isAlive).toList();
            if (left.isEmpty()) break;
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        try {
            assertTrue(left.isEmpty(), "timed-out command still running: " + left);
        } finally {
            left.forEach(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    public void runCheck_reportsExitCode() {
        assumeFalse(Utils.ISWINDOWS, "uses true and false");
        assertTrue(DockerStatus.runCheck(new ProcessBuilder("true"), 5_000));
        assertFalse(DockerStatus.runCheck(new ProcessBuilder("false"), 5_000));
    }
}
