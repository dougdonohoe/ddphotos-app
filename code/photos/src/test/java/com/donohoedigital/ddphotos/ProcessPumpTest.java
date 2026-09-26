package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Drives {@link ProcessPump} with real {@code sh} processes: each stream's lines reach their own
 * handler in order, and {@code onExit} reports the exit code only after the last line.
 */
public class ProcessPumpTest {

    private final List<String> events_ = Collections.synchronizedList(new ArrayList<>());
    private final CompletableFuture<Integer> exit_ = new CompletableFuture<>();

    @BeforeEach
    void requireSh() {
        assumeFalse(Utils.ISWINDOWS, "uses sh");
    }

    private int pump(String script) throws Exception {
        Process p = new ProcessBuilder("sh", "-c", script).start();
        ProcessPump.start(p, "test",
                line -> events_.add("out:" + line),
                line -> events_.add("err:" + line),
                code -> {
                    events_.add("exit:" + code);
                    exit_.complete(code);
                });
        return exit_.get(10, TimeUnit.SECONDS);
    }

    private List<String> only(String prefix) {
        synchronized (events_) {
            return events_.stream().filter(e -> e.startsWith(prefix)).toList();
        }
    }

    @Test
    void routesEachStreamToItsHandlerInOrder() throws Exception {
        int code = pump("echo a; echo x >&2; echo b; echo y >&2; exit 3");

        assertEquals(3, code);
        assertEquals(List.of("out:a", "out:b"), only("out:"));
        assertEquals(List.of("err:x", "err:y"), only("err:"));
    }

    @Test
    void reportsExitAfterTheLastLine() throws Exception {
        // Enough output that the readers are still busy when the process exits.
        pump("i=0; while [ $i -lt 2000 ]; do echo line$i; echo e$i >&2; i=$((i+1)); done");

        assertEquals(2000, only("out:").size());
        assertEquals(2000, only("err:").size());
        assertEquals("exit:0", events_.getLast());
    }

    @Test
    void handlesNoOutput() throws Exception {
        assertEquals(0, pump("true"));
        assertEquals(List.of("exit:0"), events_);
    }

    @Test
    void doesNotWaitForeverOnAPipeHeldOpenByAGrandchild() throws Exception {
        // The background sleep inherits both pipes and keeps them open after sh exits. Each
        // reader gets READER_JOIN_MS, so the exit should arrive well before the sleep ends.
        long start = System.nanoTime();
        int code = pump("echo before; sleep 12 & exit 5");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(5, code);
        assertTrue(elapsedMs < 8_000, "exit reported after " + elapsedMs + "ms");
        assertEquals(List.of("out:before"), only("out:"));
    }
}
