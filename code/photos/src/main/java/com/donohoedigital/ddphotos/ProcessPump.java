package com.donohoedigital.ddphotos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Reads a process's stdout and stderr on two daemon threads, one line at a time, and reports the
 * exit code on a third once the process has exited and both streams have been read to the end.
 * Waiting for the readers is what guarantees {@code onExit} comes after the last line.
 *
 * <p>No Swing here: the handlers run on the reader and monitor threads, so callers marshal to the
 * EDT themselves.
 */
final class ProcessPump {

    /**
     * How long the monitor waits for each reader after the process exits.  A reader normally ends
     * at once, but a grandchild the process left behind can hold the pipe open indefinitely; the
     * exit is then reported anyway, and any later output still reaches its handler.
     */
    static final long READER_JOIN_MS = 2000;

    private ProcessPump() {}

    /**
     * @param name   prefix for the thread names (e.g. "check" gives check-stdout, check-stderr,
     *               check-monitor)
     * @param stdout called with each stdout line, without its line terminator
     * @param stderr called with each stderr line, without its line terminator
     * @param onExit called with the exit code after both streams are drained
     */
    static void start(Process p, String name, Consumer<String> stdout, Consumer<String> stderr,
                      IntConsumer onExit) {
        Thread out = reader(p.getInputStream(), stdout, name + "-stdout");
        Thread err = reader(p.getErrorStream(), stderr, name + "-stderr");
        Thread mon = new Thread(() -> {
            try {
                int code = p.waitFor();
                out.join(READER_JOIN_MS);
                err.join(READER_JOIN_MS);
                onExit.accept(code);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, name + "-monitor");
        mon.setDaemon(true);
        out.start();
        err.start();
        mon.start();
    }

    private static Thread reader(InputStream is, Consumer<String> handler, String name) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    handler.accept(line);
                }
            } catch (IOException e) {
                // normal on process exit
            }
        }, name);
        t.setDaemon(true);
        return t;
    }
}
