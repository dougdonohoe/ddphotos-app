package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.ddphotos.PhotosUtils;
import com.donohoedigital.ddphotos.runner.Prerequisite.Result;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the prerequisite classification rules.  No Swing, config or process dependency -
 * exercises the static check methods directly, the way {@code PathValidationTest} does.
 *
 * <p>The fixtures below are real captured output from
 * {@code ddphotos --dir ... --non-interactive --show-mounts <tool> whoami}, stdout and stderr
 * merged the way {@code CommandRunnerPanel.startCheckReaders} merges them - including the wrapper's
 * mount banner, the npm notices and wrangler's ANSI colouring.  Only the two states that could not
 * be produced on the capture machine (a valid Cloudflare login, and a Pages project list) are
 * reconstructed, from the strings in wrangler's own bundle.
 */
public class PrerequisiteCheckTest {

    private static final String ESC = Character.toString(27);

    /** The wrapper's --show-mounts banner, which precedes every check's real output. */
    private static final String MOUNTS = """
            DD Photos (using /usr/local/bin/docker) is mounting:
              /Users/donohoe/.config/ddphotos/bin -> /ddphotos-script-dir
              /Users/donohoe/work/ddphotos -> /ddphotos

            """;

    /** The wrapper writes this to stderr on every command when a new image is published. */
    private static final String UPGRADE_NOTICE =
            "Update available from v1.27.2: v1.28.1 - run 'ddphotos upgrade' to update\n";

    private static final String NPM_NOTICES = "npm notice run npx\nnpm notice run 'wrangler' whoami\n";

    // ── wrangler whoami ──────────────────────────────────────────────────────

    /** Real: token expired, non-interactive.  Note this exits 1 and colours its output. */
    private static final String WRANGLER_EXPIRED = MOUNTS + """

             ⛅️ wrangler 4.114.0
            ────────────────────
            Getting User settings...

            """ + UPGRADE_NOTICE + NPM_NOTICES
            + ESC + "[31m✘ " + ESC + "[41;31m[" + ESC + "[41;97mERROR" + ESC + "[41;31m]" + ESC + "[0m "
            + ESC + "[1mNot logged in. Your auth token has expired and could not be refreshed, and the "
            + "environment is non-interactive. Run `wrangler login` in an interactive terminal or set a "
            + "CLOUDFLARE_API_TOKEN." + ESC + "[0m\n";

    /** Real: no credentials at all.  Note this exits 0. */
    private static final String WRANGLER_NEVER_LOGGED_IN = MOUNTS + """

             ⛅️ wrangler 4.114.0
            ────────────────────
            Getting User settings...
            You are not authenticated. Please run `wrangler login`.
            To deploy without logging in, run a command like `wrangler deploy --temporary` to use a temporary preview account.
            """ + NPM_NOTICES;

    /** Reconstructed from wrangler's bundle: "You are logged in with an ${user.authType}, ...". */
    private static final String WRANGLER_LOGGED_IN = MOUNTS + """

             ⛅️ wrangler 4.114.0
            ────────────────────
            Getting User settings...
            👋 You are logged in with an OAuth Token, associated with the email doug@donohoe.info.
            """ + NPM_NOTICES;

    @Test
    public void wranglerAuth_loggedIn_passes() {
        assertEquals(Result.PASSED, WranglerRunner.checkAuth(WRANGLER_LOGGED_IN, 0));
    }

    /** Never logged in: wrangler exits 0, so only the phrase distinguishes this from success. */
    @Test
    public void wranglerAuth_neverLoggedIn_fails() {
        assertEquals(Result.FAILED, WranglerRunner.checkAuth(WRANGLER_NEVER_LOGGED_IN, 0));
    }

    /** Expired token: exits 1, but it is still the condition 'wrangler login' fixes. */
    @Test
    public void wranglerAuth_expiredToken_failsRatherThanErrors() {
        assertEquals(Result.FAILED, WranglerRunner.checkAuth(WRANGLER_EXPIRED, 1));
    }

    /** The regression this all exists for: an errored check must not read as logged in. */
    @Test
    public void wranglerAuth_apiError_isError() {
        String out = MOUNTS + "✘ [ERROR] A request to the Cloudflare API failed.\n"
                     + "  Authentication error [code: 10000]\n";
        assertEquals(Result.ERROR, WranglerRunner.checkAuth(out, 1));
    }

    @Test
    public void wranglerAuth_emptyOutput_isError() {
        assertEquals(Result.ERROR, WranglerRunner.checkAuth("", 1));
    }

    /** A killed check can leave the success phrase already printed; the exit code still rules it out. */
    @Test
    public void wranglerAuth_loggedInTextButNonZeroExit_isError() {
        assertEquals(Result.ERROR, WranglerRunner.checkAuth(WRANGLER_LOGGED_IN, 137));
    }

    /** Unrecognized wording must not pass - both tools run via 'npx --yes', i.e. always latest. */
    @Test
    public void wranglerAuth_unknownWording_isError() {
        assertEquals(Result.ERROR, WranglerRunner.checkAuth(MOUNTS + "Something new and unexpected.\n", 0));
    }

    // ── wrangler pages project list --json ───────────────────────────────────

    private static final String PROJECT_LIST = MOUNTS + """
            [
              {
                "Project Name": "donohoe-photos",
                "Project Domains": "donohoe-photos.pages.dev",
                "Git Provider": "No",
                "Last Modified": "3 days ago"
              },
              {
                "Project Name": "manly-man",
                "Project Domains": "manly-man.pages.dev",
                "Git Provider": "No",
                "Last Modified": "1 month ago"
              }
            ]
            """;

    @Test
    public void wranglerProject_present_passes() {
        assertEquals(Result.PASSED, WranglerRunner.checkProject(PROJECT_LIST, 0, "manly-man"));
    }

    @Test
    public void wranglerProject_absentFromRealList_fails() {
        assertEquals(Result.FAILED, WranglerRunner.checkProject(PROJECT_LIST, 0, "not-there"));
    }

    @Test
    public void wranglerProject_noProjectsAtAll_fails() {
        assertEquals(Result.FAILED, WranglerRunner.checkProject(MOUNTS + "[]\n", 0, "my-site"));
    }

    /** A name must match a whole cell - 'manly' must not be satisfied by 'manly-man'. */
    @Test
    public void wranglerProject_partialNameDoesNotMatch() {
        assertEquals(Result.FAILED, WranglerRunner.checkProject(PROJECT_LIST, 0, "manly"));
    }

    /**
     * The sharpest bug this fixes: a list that failed on permissions used to be reported as
     * "project not found", which offered to create a project that may well already exist.
     */
    @Test
    public void wranglerProject_apiError_isErrorNotNotFound() {
        String out = MOUNTS + "✘ [ERROR] A request to the Cloudflare API failed.\n"
                     + "  Authentication error [code: 10000]\n";
        assertEquals(Result.ERROR, WranglerRunner.checkProject(out, 1, "my-site"));
    }

    @Test
    public void wranglerProject_unparseableOutput_isError() {
        assertEquals(Result.ERROR, WranglerRunner.checkProject(MOUNTS + "who knows\n", 0, "my-site"));
    }

    @Test
    public void wranglerProject_blankName_isError() {
        assertEquals(Result.ERROR, WranglerRunner.checkProject(PROJECT_LIST, 0, "  "));
    }

    // ── surge whoami ─────────────────────────────────────────────────────────

    /** Real: authenticated.  Surge prints "<email> - <plan>" and exits 0. */
    private static final String SURGE_LOGGED_IN = """
            DD Photos (using /usr/local/bin/docker) is mounting:
              /Users/donohoe/.config/ddphotos/bin -> /ddphotos-script-dir
              /Users/donohoe/work/ddphotos -> /ddphotos
              /Users/donohoe/.netrc -> /root/.netrc


               doug@donohoe.info - Student
            """ + UPGRADE_NOTICE;

    /** Real: no credentials.  Surge exits 0 here too, so the exit code proves nothing. */
    private static final String SURGE_LOGGED_OUT = MOUNTS + "\n   Not Authenticated\n";

    @Test
    public void surgeAuth_loggedIn_passes() {
        assertEquals(Result.PASSED, SurgeRunner.checkAuth(SURGE_LOGGED_IN, 0));
    }

    @Test
    public void surgeAuth_loggedOut_fails() {
        assertEquals(Result.FAILED, SurgeRunner.checkAuth(SURGE_LOGGED_OUT, 0));
    }

    /** Previously this passed: exit 0 plus no "not authenticated" was treated as success. */
    @Test
    public void surgeAuth_unknownWording_isError() {
        assertEquals(Result.ERROR, SurgeRunner.checkAuth(MOUNTS + "\n   Sorry, you must log in.\n", 0));
    }

    @Test
    public void surgeAuth_networkError_isError() {
        String out = MOUNTS + "npm error network request to https://registry.npmjs.org/surge failed\n";
        assertEquals(Result.ERROR, SurgeRunner.checkAuth(out, 1));
    }

    @Test
    public void surgeAuth_emptyOutput_isError() {
        assertEquals(Result.ERROR, SurgeRunner.checkAuth("", 0));
    }

    /** The account line is anchored so a mount path containing an '@' cannot satisfy it. */
    @Test
    public void surgeAuth_emailInMountPath_doesNotPass() {
        String out = """
                DD Photos (using /usr/local/bin/docker) is mounting:
                  /Users/doug@donohoe.info/photos -> /ddphotos

                """;
        assertEquals(Result.ERROR, SurgeRunner.checkAuth(out, 0));
    }

    // ── ddphotos version --image ─────────────────────────────────────────────

    private static final String VERSION_OUTPUT = MOUNTS + """
            Script:         /Users/donohoe/.config/ddphotos/bin/ddphotos
            Image:          dougdonohoe/ddphotos:v1.27.2
                              Version:  v1.27.2
                              Git:      v1.27.2
            DD Photos dir:  /Users/donohoe/work/ddphotos
            """;

    @Test
    public void upgrade_updateAvailable_passes() {
        assertEquals(Result.PASSED, UpgradeRunner.checkVersion(VERSION_OUTPUT + UPGRADE_NOTICE, 0));
    }

    @Test
    public void upgrade_upToDate_fails() {
        assertEquals(Result.FAILED, UpgradeRunner.checkVersion(VERSION_OUTPUT, 0));
    }

    /** Without this, a version check that never ran was reported as "No upgrade available." */
    @Test
    public void upgrade_checkFailed_isErrorNotUpToDate() {
        assertEquals(Result.ERROR,
                UpgradeRunner.checkVersion("Error: Docker is not running. Make sure Docker Desktop is open.\n", 1));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @Test
    public void stripAnsi_removesColourCodes() {
        assertEquals("Not logged in.",
                PhotosUtils.stripAnsi(ESC + "[31m" + ESC + "[1mNot logged in." + ESC + "[0m"));
    }

    /**
     * The console pumps output through stripAnsi because Swing renders none of it - without that,
     * wrangler's coloured error line shows the parameters as literal text ("[31m", "[0m").
     */
    @Test
    public void stripAnsi_leavesNoBracketCodesInRealWranglerError() {
        String clean = PhotosUtils.stripAnsi(WRANGLER_EXPIRED);
        org.junit.Assert.assertFalse(clean.contains(ESC));
        org.junit.Assert.assertFalse(clean.contains("[31m"));
        org.junit.Assert.assertFalse(clean.contains("[41;97m"));
        org.junit.Assert.assertFalse(clean.contains("[0m"));
        org.junit.Assert.assertTrue(clean.contains("✘ [ERROR] Not logged in. Your auth token has expired"));
    }

    @Test
    public void containsAny_isCaseInsensitive() {
        org.junit.Assert.assertTrue(Prerequisite.containsAny("   NOT AUTHENTICATED", "Not Authenticated"));
        org.junit.Assert.assertFalse(Prerequisite.containsAny("all good", "Not Authenticated"));
    }
}
