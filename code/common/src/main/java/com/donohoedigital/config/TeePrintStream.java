package com.donohoedigital.config;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class TeePrintStream {
    private final PrintStream originalOut;
    private final ByteArrayOutputStream baos;

    public TeePrintStream() {
        originalOut = System.out;
        baos = new ByteArrayOutputStream();
        TeeOutputStream tee = new TeeOutputStream(System.out, baos);
        PrintStream ps = new PrintStream(tee, true, StandardCharsets.UTF_8);
        System.setOut(ps);
    }

    public String[] getCapturedLines() {
        String capturedOutput = baos.toString(StandardCharsets.UTF_8);
        if (capturedOutput == null || capturedOutput.isEmpty()) {
            return new String[0];
        }
        return capturedOutput.split(System.lineSeparator());
    }

    public void restoreOriginal() {
        System.setOut(originalOut);
    }

    /** Writes everything to both streams. */
    private static class TeeOutputStream extends OutputStream {
        private final OutputStream a;
        private final OutputStream b;

        TeeOutputStream(OutputStream a, OutputStream b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int c) throws IOException {
            a.write(c);
            b.write(c);
        }

        @Override
        public void write(byte @NotNull [] buf, int off, int len) throws IOException {
            a.write(buf, off, len);
            b.write(buf, off, len);
        }

        @Override
        public void flush() throws IOException {
            a.flush();
            b.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                a.close();
            } finally {
                b.close();
            }
        }
    }

    static void main() {
        TeePrintStream teePrintStream = new TeePrintStream();

        // Example usage
        System.out.println("Hello, World!");
        System.out.println("This is a test.");

        // Fetch captured lines
        String[] output = teePrintStream.getCapturedLines();
        System.out.println("Captured Line 2: " + output[1]);

        // Restore original System.out
        teePrintStream.restoreOriginal();
    }
}
