package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.AppEngine;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.base.ApplicationError;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.config.DataElement;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.AlbumsFile;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.runner.CommandRunner;
import com.donohoedigital.ddphotos.runner.FlagDef;
import com.donohoedigital.gui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Shared base for the two runner panels: {@link CommandRunnerPanel} (tabbed command runner) and
 * {@link WizardRunnerPanel} (wizard-step runner). Owns the control row, the flag / sub-flag rows,
 * the {@link RunnerConsole}, the process-monitor threads and the stop/kill/close machinery.
 *
 * <p>Subclasses supply the variation points: which {@link Site} drives the flag rows
 * ({@link #getCurrentSite()}), which {@link CommandRunner} is rendered/launched
 * ({@link #resolveRunner()}) and what happens when Run is clicked ({@link #onRun()}).
 *
 * <p>Extends {@link DDTabPanel} because {@link CommandRunnerPanel} must remain one (it is added
 * via {@code DDTabbedPane.addTab}). When used outside a tab pane (the wizard) {@code getTabPane()}
 * is null - subclasses must guard any tab-pane access.
 */
public abstract class AbstractRunnerPanel extends DDTabPanel implements AppEngine.CloseListener {

    protected static final String STYLE = "Options";
    protected static final String CONSOLE_GREEN_STYLE = "ConsoleGreen";
    protected static final String CONSOLE_BLUE_STYLE = "ConsoleBlue";
    protected static final String CONSOLE_BIG_STYLE = "ConsoleBig";

    protected final AppContext context_;

    // Controls created in createUI()
    protected RunnerConsole console_;
    protected DDButton runBtn_;
    protected DDButton stopBtn_;
    protected DDButton killBtn_;
    private JPanel primaryFlagsRow_;
    private JPanel subFlagsRow_;

    private final Map<String, OptionBoolean> boolControls_ = new LinkedHashMap<>();
    private final Map<String, OptionText> textControls_ = new LinkedHashMap<>();
    private final Map<String, OptionFileChooser> fileControls_ = new LinkedHashMap<>();
    private final Map<String, OptionCombo<String>> choiceControls_ = new LinkedHashMap<>();
    private final TypedHashMap dummy_ = new TypedHashMap();

    // Process state (volatile so monitor thread reads current value)
    protected volatile Process process_;
    // The runner that launched the live process - used by stop/kill/okayToClose.
    protected CommandRunner activeRunner_;
    // Set when the user stops/kills the live process, so the monitor thread treats whatever exit
    // code results as a deliberate stop rather than a failure. The exit code alone is unreliable:
    // a forced terminate yields 1 on Windows, not the 137/143 of a Unix SIGKILL/SIGTERM.
    private volatile boolean userStopped_;

    // External gate (e.g. wizard validation) ANDed with the panel's own validity check.
    private boolean runAllowed_ = true;

    protected AbstractRunnerPanel(AppContext context) {
        super(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        context_ = context;
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Variation points
    // ──────────────────────────────────────────────────────────────────────────────

    /** The site that drives the flag rows and gates the Run button (must be non-null to run). */
    protected abstract Site getCurrentSite();

    /** The runner used to render the control row / flag rows and (when launching) to build the command. */
    protected abstract CommandRunner resolveRunner();

    /** Invoked when the Run button is clicked - each subclass owns its launch flow. */
    protected abstract void onRun();

    /** Hook run at the start of {@link #createUI()}, before any UI is built. */
    protected void onBeforeBuild() {}

    /** Hook run at the end of {@link #createUI()}, after the UI is built (e.g. register listeners). */
    protected void onAfterBuild() {}

    /** Left inset (px) applied to the control and flag rows so they align with their container. */
    protected int contentLeftInset() { return 10; }

    /**
     * Background for the flag rows. The default brighter shade gives the editable-flag strip a
     * subtle band in the command tabs; return {@code null} to make the rows transparent so they
     * blend into the parent panel.
     */
    protected Color flagRowBackground() {
        return UIManager.getColor("Label.background").brighter();
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // UI construction (lazy, via DDTabPanel.ancestorAdded -> createUI)
    // ──────────────────────────────────────────────────────────────────────────────

    @Override
    protected void createUI() {
        setLayout(new BorderLayout());
        onBeforeBuild();

        console_ = new RunnerConsole();
        add(buildOptionsPanel(), BorderLayout.NORTH);
        add(console_, BorderLayout.CENTER);

        onAfterBuild();
        updateButtonState();
        AppEngine.getAppEngine().addCloseListener(this);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        AppEngine.getAppEngine().removeCloseListener(this);
    }

    private JPanel buildOptionsPanel() {
        DDPanel panel = new DDPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(6, 0, 0, 0)));

        int left = contentLeftInset();
        JPanel controlRow = buildControlRow();
        controlRow.setBorder(BorderFactory.createEmptyBorder(0, left, 0, 0));
        JPanel flagsRow = buildFlagsRow();
        flagsRow.setBorder(BorderFactory.createEmptyBorder(0, left, 4, 0));
        panel.add(controlRow);
        panel.add(Box.createVerticalStrut(4));
        panel.add(flagsRow);

        return panel;
    }

    private JPanel buildControlRow() {
        runBtn_ = DDIconButtons.iconButton("runcmd", STYLE, DDIconButtons.PLAY);
        stopBtn_ = DDIconButtons.iconButton("stopcmd", STYLE, DDIconButtons.STOP_SQUARE);
        killBtn_ = DDIconButtons.iconButton("killcmd", STYLE, DDIconButtons.KILL);
        DDButton scrollToTopBtn = DDIconButtons.iconButton("scrolltop", STYLE, DDIconButtons.SCROLL_TOP);
        DDButton scrollToBottomBtn = DDIconButtons.iconButton("scrollbottom", STYLE, DDIconButtons.SCROLL_BOTTOM);
        DDButton clearBtn = DDIconButtons.iconButton("clearcmd", STYLE, DDIconButtons.ERASER);
        DDButton findBtn = DDIconButtons.iconButton("findcmd", STYLE, DDIconButtons.SEARCH);

        runBtn_.addActionListener(_ -> onRun());
        stopBtn_.addActionListener(_ -> onStop());
        killBtn_.addActionListener(_ -> onKill());
        scrollToTopBtn.addActionListener(_ -> console_.scrollToTop());
        scrollToBottomBtn.addActionListener(_ -> console_.scrollToBottom());
        clearBtn.addActionListener(_ -> console_.clear());
        findBtn.addActionListener(_ -> console_.showSearch());

        CommandRunner runner = resolveRunner();
        DDLabel cmdLabel = new DDLabel(runner.getPrefsKey(true), CONSOLE_BIG_STYLE);
        cmdLabel.setText(runner.getDisplayName());

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(runBtn_);
        row.add(Box.createHorizontalStrut(4));
        row.add(stopBtn_);
        row.add(Box.createHorizontalStrut(4));
        row.add(killBtn_);
        row.add(Box.createHorizontalStrut(16));
        row.add(scrollToTopBtn);
        row.add(Box.createHorizontalStrut(4));
        row.add(scrollToBottomBtn);
        row.add(Box.createHorizontalStrut(4));
        row.add(clearBtn);
        row.add(Box.createHorizontalStrut(4));
        row.add(findBtn);
        row.add(Box.createHorizontalStrut(12));
        row.add(cmdLabel);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private JPanel buildFlagsRow() {
        DDPanel panel = new DDPanel();
        panel.setAlignmentX(LEFT_ALIGNMENT);

        primaryFlagsRow_ = newFlagSubRow();
        subFlagsRow_ = newFlagSubRow();
        rebuildFlagsRow();

        panel.add(primaryFlagsRow_, BorderLayout.NORTH);
        panel.add(subFlagsRow_, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel newFlagSubRow() {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 14, 2));
        row.setAlignmentX(LEFT_ALIGNMENT);
        Color bg = flagRowBackground();
        if (bg != null) {
            row.setOpaque(true);
            row.setBackground(bg);
        } else {
            row.setOpaque(false);
        }
        // WrapLayout derives its wrapped height from the row's current width, which is 0 before
        // the first layout pass (so it reports a single-row height and the extra rows get clipped).
        // Re-validate once the row is given a real width so the wrapped height is recomputed and
        // the enclosing BoxLayout re-lays-out to fit every row.
        row.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                row.revalidate();
            }
        });
        return row;
    }

    /** Rebuilds the primary/sub flag rows from the current site and runner. Safe to call repeatedly. */
    public void rebuildFlagsRow() {
        if (primaryFlagsRow_ == null) return; // UI not built yet
        primaryFlagsRow_.removeAll();
        subFlagsRow_.removeAll();
        boolControls_.clear();
        textControls_.clear();
        fileControls_.clear();
        choiceControls_.clear();

        Site site = getCurrentSite();
        if (site == null) {
            return;
        }

        CommandRunner runner = resolveRunner();
        AlbumsFile siteAf = site.getAlbumsFile();
        String siteId = (siteAf != null && siteAf.getSettings() != null) ? siteAf.getSettings().getId() : "";

        DDLabel cmdLabel = new DDLabel(runner.getPrefsKey(true), CONSOLE_BLUE_STYLE);
        cmdLabel.setText(runner.getBinary());
        cmdLabel.setFont(cmdLabel.getFont().deriveFont(Font.BOLD));
        primaryFlagsRow_.add(cmdLabel);
        for (FlagDef def : runner.getCommandFlagDefs(site)) {
            buildFlags(primaryFlagsRow_, CONSOLE_BLUE_STYLE, def, siteId, false);
        }

        if (runner.getSubCommand() != null) {
            DDLabel subLabel = new DDLabel(runner.getPrefsKey(true), CONSOLE_GREEN_STYLE);
            subLabel.setText(runner.getSubCommand());
            subLabel.setFont(subLabel.getFont().deriveFont(Font.BOLD));
            subFlagsRow_.add(subLabel);

            for (FlagDef def : runner.getSubCommandFlagDefs(site)) {
                buildFlags(subFlagsRow_, CONSOLE_GREEN_STYLE, def, siteId, true);
            }
        }

        primaryFlagsRow_.revalidate();
        primaryFlagsRow_.repaint();
        subFlagsRow_.revalidate();
        subFlagsRow_.repaint();
    }

    private void buildFlags(JPanel flagsRow, String style, FlagDef def, String siteId, boolean isSubCommand) {
        // NOTE: labels, default values and help text come from client.properties for
        //       all flags.  Preferences are stored on a command/site basis since they
        //       can differ from site to site
        CommandRunner runner = resolveRunner();
        String prefsBase = "command." + siteId + "." + runner.getPrefsKey(isSubCommand);
        String prefsName = prefsBase + "." + def.getPrefsName();
        String widgetName = runner.getPrefsKey(isSubCommand) + "." + def.getPrefsName();

        switch (def.visibility()) {
            case HIDDEN -> { /* not shown */ }

            case VIEW_ONLY -> {
                switch (def) {
                    case FlagDef.FixedFlag ff -> {
                        String value = ff.value();
                        if (!value.isBlank()) {
                            DDLabel label = new DDLabel(widgetName, style);
                            label.setText(def.name() + " " + value);
                            flagsRow.add(label);
                        }
                    }
                    case FlagDef.Constant _ -> {
                        DDLabel label = new DDLabel(widgetName, style);
                        label.setText(def.name());
                        flagsRow.add(label);
                    }
                    case FlagDef.FixedField rc -> {
                        DDLabel label = new DDLabel(widgetName, style);
                        label.setText(rc.value());
                        flagsRow.add(label);
                    }
                    default -> throw new ApplicationError("No VIEW_ONLY implementation for" + def.name());
                }
            }

            case EDITABLE -> {
                switch (def) {
                    case FlagDef.BooleanFlag _ -> {
                        OptionBoolean ob = new OptionBoolean(prefsName, widgetName, style, dummy_);
                        boolControls_.put(def.name(), ob);
                        ob.resetToPrefs();
                        flagsRow.add(ob);
                    }

                    case FlagDef.ChoiceField pcf -> {
                        List<String> keys = new ArrayList<>(pcf.choices());
                        DataElement<String> element = new DataElement<>(widgetName, keys, new ArrayList<>(keys));
                        OptionCombo<String> oc = new OptionCombo<>(element, prefsName, widgetName,
                                style, dummy_, 250, true);
                        oc.setRequired(true);
                        choiceControls_.put(def.name(), oc);
                        flagsRow.add(oc);
                    }

                    case FlagDef.FilePickerField fld -> {
                        OptionFileChooser ofc = new OptionFileChooser(prefsName, widgetName,
                                style, dummy_, 200, 300, fld.requiredFilename());
                        ofc.getTextField().addValidationListener(this::updateButtonState);
                        fileControls_.put(def.name(), ofc);
                        flagsRow.add(ofc);
                    }

                    case FlagDef.ValidatedTextField vf -> {
                        OptionText ot = new OptionText(prefsName, widgetName,
                                    style, dummy_, 200,
                                    vf.pattern(), 240);
                        ot.getTextField().addValidationListener(this::updateButtonState);
                        textControls_.put(def.name(), ot);
                        flagsRow.add(ot);

                    }
                    default -> throw new ApplicationError("No EDITABLE implementation for" + def.name());
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Button state
    // ──────────────────────────────────────────────────────────────────────────────

    /** Sets an external gate on the Run button; ANDed with the panel's own validity check. */
    public void setRunEnabled(boolean allowed) {
        runAllowed_ = allowed;
        updateButtonState();
    }

    protected void updateButtonState() {
        if (runBtn_ == null) return; // UI not built yet
        boolean running = process_ != null && process_.isAlive();
        boolean textValid = textControls_.values().stream().allMatch(OptionText::isValidData);
        boolean filesValid = fileControls_.values().stream().allMatch(OptionFileChooser::isValidData);
        boolean comboValid = choiceControls_.values().stream().allMatch(OptionCombo::isValidData);
        runBtn_.setEnabled(runAllowed_ && !running && getCurrentSite() != null
                && textValid && comboValid && filesValid);
        stopBtn_.setEnabled(running);
        killBtn_.setEnabled(running);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Run support
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Reads the live editable-flag values into a name->value map for the runner to build its command.
     */
    protected Map<String, String> collectUserValues() {
        Map<String, String> values = new HashMap<>();
        boolControls_.forEach((k, ob) -> values.put(k, String.valueOf(ob.getCheckBox().isSelected())));
        textControls_.forEach((k, tf) -> values.put(k, tf.getText()));
        fileControls_.forEach((k, fc) -> values.put(k, fc.getText()));
        choiceControls_.forEach((k, combo) -> {
            Object sel = combo.getSelectedItem();
            values.put(k, sel != null ? sel.toString() : "");
        });
        return values;
    }

    /**
     * Pumps the process streams to the console and, when the process exits, clears {@link #process_},
     * prints the exit code, refreshes button state, and invokes {@code onComplete} on the EDT.
     */
    protected void startReaders(Process p, IntConsumer onComplete) {
        userStopped_ = false;
        Thread out = new Thread(() -> console_.pumpStream(p.getInputStream(), false));
        Thread err = new Thread(() -> console_.pumpStream(p.getErrorStream(), true));
        Thread mon = new Thread(() -> {
            try {
                int code = p.waitFor();
                SwingUtilities.invokeLater(() -> {
                    process_ = null;
                    // After a user stop/kill the exit code is an artifact of how we terminated the
                    // process (e.g. 1 on Windows), not a real result - don't present it as one.
                    console_.appendSystem(wasUserStop(code)
                            ? PropertyConfig.getMessage("msg.cmd.stopped")
                            : PropertyConfig.getMessage("msg.cmd.exited", code));
                    updateButtonState();
                    onComplete.accept(code);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        out.setDaemon(true);
        err.setDaemon(true);
        mon.setDaemon(true);
        out.start();
        err.start();
        mon.start();
    }

    protected void onStop() {
        userStopped_ = true;
        if (activeRunner_ != null) activeRunner_.stop(process_, console_.asOutputSink());
    }

    protected void onKill() {
        userStopped_ = true;
        if (activeRunner_ != null) activeRunner_.kill(process_, console_.asOutputSink());
    }

    /**
     * True when the just-finished process exited because the user stopped/killed it - either we
     * flagged it via {@link #onStop}/{@link #onKill}, or the exit code is a recognized stop code.
     * Subclasses use this to suppress failure feedback for a deliberate stop.
     */
    protected boolean wasUserStop(int code) {
        return userStopped_ || CommandRunner.isUserStopCode(code);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // AppEngine.CloseListener
    // ──────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean okayToClose() {
        if (process_ == null || !process_.isAlive() || activeRunner_ == null) return true;

        String sMsg = PropertyConfig.getMessage("msg.close.running.confirm", activeRunner_.getDisplayName());
        if (!EngineUtils.displayConfirmationDialog(context_, sMsg, "closerunning")) {
            return false;
        }
        userStopped_ = true;
        activeRunner_.stop(process_);
        return true;
    }
}
