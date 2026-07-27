package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.config.PropertyConfig;

import java.util.List;

/**
 * A pre-check that must pass before the main command runs.
 * The check command is streamed to the output pane; its full output is also
 * captured and passed to {@link #check} to determine whether to proceed.
 */
public abstract class Prerequisite {

    private final List<String> checkCommand_;

    protected Prerequisite(List<String> checkCommand) {
        checkCommand_ = checkCommand;
    }

    public List<String> checkCommand() { return checkCommand_; }

    /**
     * How a check turned out. The distinction between {@link #FAILED} and {@link #ERROR} is the
     * point: "did not pass" must not be read as "needs the remediation". The tools we check run
     * through docker and {@code npx --yes} (unpinned, always latest), so a check can fail to
     * produce an answer for reasons that have nothing to do with logging in - a network failure,
     * an expired token, an API error, or simply new wording we don't recognize. Guessing in those
     * cases means offering the wrong fix, or worse, running the main command anyway.
     */
    public enum Result {
        /** The check positively confirmed what it was looking for. */
        PASSED,
        /** The tool ran and positively reported the condition {@link #remediation} addresses. */
        FAILED,
        /** The check could not be evaluated - stop and show the output rather than guess. */
        ERROR
    }

    /** Classify the combined stdout+stderr output and exit code. */
    public abstract Result check(String output, int exitCode);

    /** What to do when the check fails. */
    public abstract Remediation remediation();

    /** Shown in the output pane before the check runs. */
    public abstract String checkingMessage();

    /** Shown in the output pane when the check fails (before RunCommand remediation). */
    public String failedMessage() { return PropertyConfig.getMessage("msg.cmd.prereqFailed"); }

    /** Shown in the output pane when the check could not be evaluated. */
    public String errorMessage(int exitCode) {
        return PropertyConfig.getMessage("msg.cmd.prereqError", exitCode);
    }

    /** HTML for the error dialog on {@link Result#ERROR}, or null for a console message only. */
    public String errorDialogMessage(int exitCode) { return null; }

    /** Title key for the {@link #errorDialogMessage} dialog. */
    public String errorTitleKey() { return "msg.windowtitle.prereqError"; }

    /** Returns the next prerequisite to run after this one passes, or null if this is the last. */
    public Prerequisite next() { return null; }

    // -------------------------------------------------------------------------
    // Output matching helpers
    // -------------------------------------------------------------------------

    /** True if {@code output} contains any of {@code needles}, ignoring case. */
    public static boolean containsAny(String output, String... needles) {
        String lower = output.toLowerCase();
        for (String needle : needles) {
            if (lower.contains(needle.toLowerCase())) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Remediation types — siblings of Remediation, nested inside Prerequisite
    // -------------------------------------------------------------------------

    public sealed interface Remediation permits RunCommand, ShowDialog, ConfirmThenRun, ShowMessage {}

    /** Run a command (e.g. login); on exit 0, proceed with the main command. */
    public record RunCommand(List<String> command) implements Remediation {}

    /** Show an info dialog; user must retry manually after fixing the issue. */
    public record ShowDialog(String htmlMessage, String titleKey) implements Remediation {}

    /** Append a message to the console and stop; no dialog, no further command. */
    public record ShowMessage(String message) implements Remediation {}

    /** Show a confirmation dialog; on yes, run the command; on no, abort. */
    public record ConfirmThenRun(String messageKey, String titleKey, List<String> command, Object... messageArgs) implements Remediation {}
}
