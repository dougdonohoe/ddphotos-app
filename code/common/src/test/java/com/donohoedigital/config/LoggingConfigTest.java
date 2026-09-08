package com.donohoedigital.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Oct 14, 2008
 * Time: 8:16:40 AM
 * To change this template use File | Settings | File Templates.
 */
public class LoggingConfigTest {
    public static final String UNITTEST_APPNAME = "unittests";
    private static final TestRuntimeDirectory runtime = new TestRuntimeDirectory();

    /**
     * On setup, verify no temp files exist
     */
    @BeforeEach
    protected void setUp() {
        LoggingConfig.reset();
        checkDirectoryDoesntExist(runtime.getClientHome(UNITTEST_APPNAME));
    }

    private static void checkDirectoryDoesntExist(File dir) {
        if (dir.exists()) {
            ConfigUtils.deleteDir(dir);
            fail(" a previous test failed to clean up after itself " + dir);
        }
    }

    /**
     * Test client
     */
    @Test
    public void testClient() {
        process(new LoggingConfig(UNITTEST_APPNAME, ApplicationType.CLIENT, runtime, false),
                runtime.getClientHome(UNITTEST_APPNAME), "GUI", true);
    }

    /**
     * Test headless client
     */
    @Test
    public void testHeadlessClient() {
        process(new LoggingConfig(UNITTEST_APPNAME, ApplicationType.HEADLESS_CLIENT, runtime, false),
                runtime.getClientHome(UNITTEST_APPNAME), "GUI", true);
    }

    /**
     * Test command line
     */
    @Test
    public void testCommandLine() {
        process(new LoggingConfig(UNITTEST_APPNAME, ApplicationType.COMMAND_LINE, runtime, false),
                runtime.getClientHome(UNITTEST_APPNAME), "CLI", false);
    }

    @Test
    public void testOverride() {
        process(new LoggingConfig("unit-test-override", ApplicationType.HEADLESS_CLIENT, runtime, false),
                runtime.getClientHome(UNITTEST_APPNAME), "OVER", true);
    }

    /**
     * actual test logic
     */
    private void process(LoggingConfig logging, File runtimeDir, String slug, boolean verifyLogfile) {
        TeePrintStream tee = new TeePrintStream();
        try {
            logging.init();

            ConfigUtils.verifyDirectory(runtimeDir);
            ConfigUtils.verifyDirectory(logging.getLogDir());
            Logger logger = LogManager.getLogger("com.donohoedigital.test");
            String message = "Test message - A quick brown fox jumped over a lazy cow.";
            logger.info(message);

            // capture lines include 0-2 informational messages from LoggingConfig
            //  + if CMD, 1st line "Log4j configured using ..." is skipped
            //  + if OVER (testing overrides), extra line for that override file
            int expected = slug.equals("CLI") ? 1 : slug.equals("OVER") ? 3 : 2;

            // inspect stdout
            String[] lines = tee.getCapturedLines();
            assertEquals(expected, lines.length);
            String line = lines[expected - 1];
            assertTrue(line.contains(" " + slug + " [main"), "should contain " + slug + " [main");
            assertTrue(line.contains(message), "Stdout file should contain message: " + message);

            if (verifyLogfile) {
                ConfigUtils.verifyFile(logging.getLogFile());
                String contents = ConfigUtils.readFile(logging.getLogFile());
                assertTrue(contents.contains(" " + slug + " [main"), "should contain " + slug + " [main");
                assertTrue(contents.contains(message), "Log file should contain message: " + message);
            }
        } finally {
            tee.restoreOriginal();
            logging.shutdown();
        }
    }

    /**
     * remove temp directories
     */
    @AfterEach
    protected void tearDown() {
        // remove directories
        cleanup(runtime.getClientHome(UNITTEST_APPNAME));
    }

    /**
     * Remove directory and all contents
     */
    private void cleanup(File dir) {
        if (dir.exists() && !ConfigUtils.deleteDir(dir)) {
            fail("Could not cleanup " + dir.getAbsolutePath());
        }
    }
}