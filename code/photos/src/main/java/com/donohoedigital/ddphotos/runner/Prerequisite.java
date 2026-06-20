package com.donohoedigital.ddphotos.runner;

import java.util.List;

/**
 * A pre-check that must pass before the main command runs.
 * The check command is streamed to the output pane; its full output is also
 * captured and passed to {@link #passed} to determine whether to proceed.
 */
public abstract class Prerequisite {

    private final List<String> checkCommand_;

    protected Prerequisite(List<String> checkCommand) {
        checkCommand_ = checkCommand;
    }

    public List<String> checkCommand() { return checkCommand_; }

    /** True if the combined stdout+stderr output and exit code indicate success. */
    public abstract boolean passed(String output, int exitCode);

    /** What to do when the check fails. */
    public abstract Remediation remediation();

    /** Shown in the output pane before the check runs. */
    public abstract String checkingMessage();

    /** Shown in the output pane when the check fails (before RunCommand remediation). */
    public String failedMessage() { return "Prerequisite check failed."; }

    /** Returns the next prerequisite to run after this one passes, or null if this is the last. */
    public Prerequisite next() { return null; }

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
