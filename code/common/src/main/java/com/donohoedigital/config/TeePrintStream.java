package com.donohoedigital.config;

import org.apache.commons.io.output.TeeOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public class TeePrintStream {
    private final PrintStream originalOut;
    private final ByteArrayOutputStream baos;

    public TeePrintStream() {
        originalOut = System.out;
        baos = new ByteArrayOutputStream();
        TeeOutputStream tee = new TeeOutputStream(System.out, baos);
        PrintStream ps = new PrintStream(tee);
        System.setOut(ps);
    }

    public String[] getCapturedLines() {
        String capturedOutput;
        try {
            capturedOutput = baos.toString(StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        if (capturedOutput == null || capturedOutput.isEmpty()) {
            return new String[0];
        }
        return capturedOutput.split(System.lineSeparator());
    }

    public void restoreOriginal() {
        System.setOut(originalOut);
    }

    public static void main(String[] args) {
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
