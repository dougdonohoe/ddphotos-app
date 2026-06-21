package com.donohoedigital.ddphotos;

import com.donohoedigital.base.Utils;
import com.donohoedigital.config.HelpConfig;
import com.donohoedigital.config.HelpTopic;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.SitesFile;
import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.ddphotos.runner.InitRunner;
import com.donohoedigital.ddphotos.runner.InstallScriptRunner;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.app.engine.DisplayMessage;
import com.donohoedigital.app.engine.Phase;
import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.gui.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

public class WizardPanel extends DDPanel implements DockerStatus.Listener {

    //private static final Logger logger = LogManager.getLogger(WizardPanel.class);

    private static final String STYLE       = "Wizard";
    private static final String STYLE_BIG       = "WizardBig";
    private static final String PREFS_NODE  = PhotosConstants.PREFS_NODE_APP;

    /** Set once the user accepts the beta/license terms on the Welcome step. */
    private static final String PREF_WELCOME_ACCEPTED = "welcome.license.accepted";

    // ── Step IDs ──────────────────────────────────────────────────────────────

    private enum Step { WELCOME, DOCKER, BASH, SCRIPT, CHOICE, INIT }

    private static final Step[] ALL_STEPS = { Step.WELCOME, Step.DOCKER, Step.BASH, Step.SCRIPT, Step.CHOICE, Step.INIT };

    // ── State ─────────────────────────────────────────────────────────────────

    private final AppContext context_;
    private final SitesFile   sitesFile_;
    private final boolean     rerun_;   // launched via "Rerun Welcome Wizard..." menu item

    private int     stepIndex_     = 0;
    private Path    initDir_       = null;   // set in INIT step, forwarded to SiteDialog

    private boolean dockerOk_  = false;
    private boolean scriptOk_  = false;
    private boolean initOk_    = false;
    private boolean justRanInit_ = false;   // true right after a successful "Run Init"

    // ── Layout ────────────────────────────────────────────────────────────────

    private CardLayout cardLayout_;
    private JPanel     cardPanel_;
    private DDLabel    stepLabel_;
    private DDButton   backBtn_;
    private DDButton   nextBtn_;

    // ── Docker step widgets ───────────────────────────────────────────────────

    private OptionFileChooser dockerBinary_;
    private DDHtmlArea        dockerStatusArea_;

    // ── Bash step widgets (Windows only) ──────────────────────────────────────

    private OptionFileChooser bashBinary_;
    private DDHtmlArea        bashStatusArea_;

    // ── Script step widgets ───────────────────────────────────────────────────

    private DDHtmlArea         scriptStatusArea_;
    private WizardRunnerPanel  scriptRunner_;

    // ── Init step widgets ─────────────────────────────────────────────────────

    private OptionFileChooser initParentDir_;
    private OptionText        initDirName_;
    private OptionText        initPreview_;
    private DDHtmlArea        initStatusArea_;
    private WizardRunnerPanel initRunner_;

    // ── Constructor ───────────────────────────────────────────────────────────

    public WizardPanel(AppContext context, SitesFile sitesFile, boolean rerun) {
        context_   = context;
        sitesFile_ = sitesFile;
        rerun_     = rerun;
        buildUI();
        DockerStatus.addListener(this);
        dockerOk_ = DockerStatus.isDockerRunning();
        showStep(0);

        context_.getWindow().setHelpMessage(PropertyConfig.getMessage("button.icon48.help")); // init help
        context_.getWindow().ignoreNextHelp(); // ignore enter so main help message doesn't go away immediately
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        DockerStatus.removeListener(this);
    }

    // ── DockerStatus.Listener ─────────────────────────────────────────────────

    @Override
    public void onDockerStatusChanged(boolean running) {
        dockerOk_ = running;
        if (currentStep() == Step.DOCKER) {
            refreshDockerStep();
            updateNavButtons();
        }
    }

    // ── Top-level layout ──────────────────────────────────────────────────────

    private void buildUI() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(1000, 650));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createRaisedBevelBorder(),
                new EmptyBorder(10, 16, 10, 16)));

        // Header: step title
        stepLabel_ = new DDLabel("wizardsteptitle", STYLE_BIG);
        stepLabel_.setBorder(new EmptyBorder(0, 0, 12, 0));

        // Card area
        cardLayout_ = new CardLayout();
        cardPanel_  = new JPanel(cardLayout_);
        cardPanel_.add(buildWelcomePanel(), Step.WELCOME.name());
        cardPanel_.add(buildDockerPanel(),  Step.DOCKER.name());
        cardPanel_.add(buildBashPanel(),    Step.BASH.name());
        cardPanel_.add(buildScriptPanel(),  Step.SCRIPT.name());
        cardPanel_.add(buildChoicePanel(),  Step.CHOICE.name());
        cardPanel_.add(buildInitPanel(),    Step.INIT.name());

        JPanel headerArea = new DDPanel();
        headerArea.setLayout(new BorderLayout());
        headerArea.add(stepLabel_, BorderLayout.NORTH);
        headerArea.add(cardPanel_, BorderLayout.CENTER);

        // Footer: Back / Next
        backBtn_ = new DDButton("wizard.back", STYLE);
        backBtn_.setText(PropertyConfig.getMessage("button.wizard.back.label"));
        backBtn_.addActionListener(_ -> onBack());

        nextBtn_ = new DDButton("wizard.next", STYLE);
        nextBtn_.setText(PropertyConfig.getMessage("button.wizard.next.label"));
        nextBtn_.addActionListener(_ -> onNext());

        // Only shown when the wizard was launched via "Rerun Welcome Wizard..." —
        // lets the user back out without forcing them through to the end.
        DDButton cancelBtn = new DDButton("wizard.cancelwizard", STYLE);
        cancelBtn.setText(PropertyConfig.getMessage("button.wizard.cancelwizard.label"));
        cancelBtn.addActionListener(_ -> returnToStartMenu(null));
        cancelBtn.setVisible(rerun_);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(10, 0, 0, 0));

        JSeparator sep = new JSeparator();
        JPanel btnRow = new JPanel();
        btnRow.setLayout(new BoxLayout(btnRow, BoxLayout.X_AXIS));
        btnRow.setOpaque(false);
        btnRow.setBorder(new EmptyBorder(8, 0, 0, 0));
        btnRow.add(backBtn_);
        btnRow.add(Box.createHorizontalStrut(8));
        btnRow.add(nextBtn_);
        btnRow.add(Box.createHorizontalGlue());
        btnRow.add(cancelBtn);
        footer.add(sep,    BorderLayout.NORTH);
        footer.add(btnRow, BorderLayout.CENTER);

        add(headerArea, BorderLayout.CENTER);
        add(footer,     BorderLayout.SOUTH);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Common styling for the read-only HTML areas used throughout the wizard. */
    private DDHtmlArea buildHtmlArea(String name, String style) {
        DDHtmlArea area = new DDHtmlArea(name, style);
        area.setDisplayOnly(true);
        area.setFocusable(true);
        area.setOpaque(true);
        area.setBorder(DDHtmlArea.createHtmlAreaBorder(5, 10, 5, 10));
        area.addHyperlinkListener(GuiUtils.HYPERLINK_HANDLER);
        return area;
    }

    // ── WELCOME step ──────────────────────────────────────────────────────────

    private JPanel buildWelcomePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        // Load intro.html via HelpConfig
        DDHtmlArea welcome = buildHtmlArea(GuiManager.DEFAULT, "Help");
        HelpTopic topic = HelpConfig.getHelpTopic("wizard");
        welcome.setText(topic.getContents());

        JScrollPane scroll = new JScrollPane(welcome,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ── DOCKER step ───────────────────────────────────────────────────────────

    private static final int DOCKER_ICON_SIZE = 72;

    private JPanel buildDockerPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        DockerStatusPanel statusPanel = new DockerStatusPanel(context_, DOCKER_ICON_SIZE);
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        statusRow.setOpaque(false);
        statusRow.add(statusPanel);

        dockerBinary_ = new OptionFileChooser(PhotosConstants.PREFS_NODE_APP,
                DockerStatus.PREFS_KEY_DOCKER_BINARY, STYLE, new TypedHashMap(), 1024, 500,
                Utils.ISWINDOWS ? "docker.exe" : "docker");
        dockerBinary_.setCustomValidator(s -> !s.isBlank() && Files.isExecutable(Path.of(s.trim())));

        // Autopopulate from system paths on first use (before registering the change listener
        // so the initial setText does not trigger onBinaryChanged)
        if (dockerBinary_.getText().isBlank()) {
            String found = DockerStatus.findDockerBinary();
            if (!found.isBlank()) {
                dockerBinary_.setText(found);
            }
        }

        dockerBinary_.addChangeListener(_ -> {
            DockerStatus.onBinaryChanged();
            refreshDockerStep();
            updateNavButtons();
        });

        JPanel form = new JPanel();
        form.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 4, VerticalFlowLayout.FILL));
        form.setOpaque(false);
        form.add(dockerBinary_);
        GuiUtils.setDDOptionLabelWidths(form, 16);

        dockerStatusArea_ = buildHtmlArea(GuiManager.DEFAULT, STYLE);
        dockerStatusArea_.addHyperlinkListener(GuiUtils.HYPERLINK_HANDLER);

        JPanel topSection = new JPanel(new BorderLayout(0, 8));
        topSection.setOpaque(false);
        topSection.add(statusRow, BorderLayout.NORTH);
        topSection.add(GuiUtils.WEST(form), BorderLayout.CENTER);

        panel.add(topSection,        BorderLayout.NORTH);
        panel.add(dockerStatusArea_, BorderLayout.CENTER);
        return panel;
    }

    private void refreshDockerStep() {
        if (!isDockerBinaryValid()) {
            dockerStatusArea_.setText(PropertyConfig.getMessage("msg.wizard.docker.binary.undefined"));
            return;
        }
        boolean running = DockerStatus.isDockerRunning();
        String msgKey = running ? "msg.wizard.docker.running" : "msg.wizard.docker.notrunning";
        String extraKey = Utils.ISLINUX   ? "msg.wizard.docker.notrunning.linux"
                        : Utils.ISMAC     ? "msg.wizard.docker.notrunning.mac"
                        : Utils.ISWINDOWS ? "msg.wizard.docker.notrunning.windows"
                        : null;
        String extra = extraKey != null ? PropertyConfig.getMessage(extraKey) : "";
        dockerStatusArea_.setText(PropertyConfig.getMessage(msgKey, extra));
    }

    // ── BASH step (Windows only) ──────────────────────────────────────────────

    private JPanel buildBashPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        bashBinary_ = new OptionFileChooser(PhotosConstants.PREFS_NODE_APP,
                BashSupport.PREFS_KEY_BASH_BINARY, STYLE, new TypedHashMap(), 1024, 500, "bash.exe");
        bashBinary_.setCustomValidator(s -> !s.isBlank() && Files.isExecutable(Path.of(s.trim())));

        // Autopopulate from well-known Git for Windows paths on first use (before registering the
        // change listener so the initial setText does not trigger refreshBashStep)
        if (bashBinary_.getText().isBlank()) {
            String found = BashSupport.findBashBinary();
            if (!found.isBlank()) {
                bashBinary_.setText(found);
            }
        }

        bashBinary_.addChangeListener(_ -> {
            refreshBashStep();
            updateNavButtons();
        });

        JPanel form = new JPanel();
        form.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 4, VerticalFlowLayout.FILL));
        form.setOpaque(false);
        form.add(bashBinary_);
        GuiUtils.setDDOptionLabelWidths(form, 16);

        bashStatusArea_ = buildHtmlArea(GuiManager.DEFAULT, STYLE);
        bashStatusArea_.addHyperlinkListener(GuiUtils.HYPERLINK_HANDLER);

        panel.add(GuiUtils.WEST(form), BorderLayout.NORTH);
        panel.add(bashStatusArea_,     BorderLayout.CENTER);
        return panel;
    }

    private void refreshBashStep() {
        String msgKey = isBashBinaryValid() ? "msg.wizard.bash.found" : "msg.wizard.bash.binary.undefined";
        bashStatusArea_.setText(PropertyConfig.getMessage(msgKey));
    }

    // ── SCRIPT step ───────────────────────────────────────────────────────────

    private JPanel buildScriptPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);

        scriptStatusArea_ = buildHtmlArea(GuiManager.DEFAULT, STYLE);
        scriptStatusArea_.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));

        scriptRunner_ = new WizardRunnerPanel(context_,
                InstallScriptRunner::new,
                // InstallScriptRunner ignores the site; a non-null one is needed so the flag row renders
                () -> new Site("script", System.getProperty("user.home"), null),
                this::onScriptInstalled,
                "msg.wizard.script.success",
                "msg.wizard.script.failure");
        scriptRunner_.setPreferredSize(new Dimension(Integer.MAX_VALUE, 180));

        panel.add(scriptStatusArea_, BorderLayout.NORTH);
        panel.add(scriptRunner_,   BorderLayout.CENTER);
        return panel;
    }

    private void refreshScriptStep() {
        Path script = PhotosUtils.scriptPath();
        scriptOk_ = Files.isExecutable(script);
        if (scriptOk_) {
            scriptStatusArea_.setText(PropertyConfig.getMessage("msg.wizard.script.found", script));
        } else {
            scriptStatusArea_.setText(PropertyConfig.getMessage("msg.wizard.script.missing"));
        }
        scriptRunner_.setRunEnabled(true); // always allow (re)running to fetch the latest copy
    }

    private void onScriptInstalled() {
        scriptOk_ = Files.isExecutable(PhotosUtils.scriptPath());
        if (scriptOk_) {
            refreshScriptStep();
        }
        updateNavButtons();
    }

    // ── CHOICE step ───────────────────────────────────────────────────────────

    private JPanel buildChoicePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        DDHtmlArea prompt = buildHtmlArea(GuiManager.DEFAULT, STYLE);
        prompt.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));
        prompt.setText(PropertyConfig.getMessage("msg.wizard.choice.prompt"));

        DDButton newSiteBtn = new DDButton("wizard.newsite", STYLE);
        newSiteBtn.setText(PropertyConfig.getMessage("button.wizard.newsite.label"));
        newSiteBtn.addActionListener(_ -> onChooseNewSite());

        DDButton existingBtn = new DDButton("wizard.existingsite", STYLE);
        existingBtn.setText(PropertyConfig.getMessage("button.wizard.existingsite.label"));
        existingBtn.addActionListener(_ -> onChooseExistingSite());

        JPanel btnRow = new JPanel();
        btnRow.setLayout(new BoxLayout(btnRow, BoxLayout.X_AXIS));
        btnRow.setOpaque(false);
        btnRow.add(Box.createHorizontalGlue());
        btnRow.add(newSiteBtn);
        btnRow.add(Box.createHorizontalStrut(16));
        btnRow.add(existingBtn);
        btnRow.add(Box.createHorizontalGlue());

        // Nested BorderLayout so prompt keeps its preferred height (BorderLayout.CENTER
        // would otherwise stretch it) and btnRow sits directly below it.
        JPanel topSection = new JPanel(new BorderLayout(0, 16));
        topSection.setOpaque(false);
        topSection.add(prompt, BorderLayout.NORTH);
        topSection.add(btnRow, BorderLayout.CENTER);

        panel.add(topSection, BorderLayout.NORTH);
        return panel;
    }

    // ── INIT step ─────────────────────────────────────────────────────────────

    private JPanel buildInitPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);

        // Parent folder picker — validates that the chosen directory exists
        initParentDir_ = new OptionFileChooser(PREFS_NODE, "wizard.parentdir", STYLE,
                new TypedHashMap(), 1024, 500, null);
        initParentDir_.setDirectoryMode(true);
        initParentDir_.setText(System.getProperty("user.home"));
        initParentDir_.setCustomValidator(s -> s.isBlank() || Files.isDirectory(Path.of(s.trim())));
        initParentDir_.setOpaque(false);
        initParentDir_.addChangeListener(_ -> {
            refreshInitPreview();
            // Retrigger dirname validation — its validity depends on the parent path
            if (initDirName_ != null) {
                initDirName_.getTextField().setText(initDirName_.getTextField().getText());
            }
        });

        // Folder name — OptionText gives us a matching styled label automatically
        initDirName_ = new OptionText(PREFS_NODE, "wizard.dirname", STYLE, new TypedHashMap(),
                100, "[^/\\\\\\s]+", 500);
        initDirName_.getTextField().setText("my-ddphotos");
        // Red when the resolved path already exists and is not a ddphotos site
        initDirName_.getTextField().setCustomValidator(s -> {
            if (s == null || s.isBlank()) return true;
            String parent = initParentDir_.getText().trim();
            Path parentPath = Path.of(parent);
            if (parent.isEmpty() || !Files.isDirectory(parentPath)) return true;
            Path full = parentPath.resolve(s.trim());
            // OK if it doesn't exist, is an already-initialized site, or is an empty leftover folder
            return !Files.exists(full) || Files.isDirectory(full.resolve("config")) || isEmptyDir(full);
        });
        initDirName_.getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { refreshInitPreview(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { refreshInitPreview(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshInitPreview(); }
        });

        // Full path preview — read-only field, aligned with the fields above
        initPreview_ = new OptionText(PREFS_NODE, "wizard.initpreview", STYLE, new TypedHashMap(),
                1024, null, 500);
        initPreview_.setDisplayOnly(true);
        initPreview_.getTextField().setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));

        JPanel form = new JPanel();
        form.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 4, VerticalFlowLayout.FILL));
        form.setOpaque(false);
        form.add(initParentDir_);
        form.add(initDirName_);
        form.add(initPreview_);
        GuiUtils.setDDOptionLabelWidths(form, 16);

        // Status area — always visible; shows current state of the chosen path
        initStatusArea_ = buildHtmlArea(GuiManager.DEFAULT, STYLE);
        initStatusArea_.setPreferredSize(new Dimension(Integer.MAX_VALUE, 75));
        DDPanel statusPanel = new DDPanel();
        statusPanel.add(initStatusArea_, BorderLayout.NORTH);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        JPanel topSection = new JPanel(new BorderLayout(0, 6));
        topSection.setOpaque(false);
        topSection.add(GuiUtils.WEST(form), BorderLayout.NORTH);
        topSection.add(statusPanel, BorderLayout.CENTER);

        // Runner panel — its own Run button is enabled/disabled via setRunEnabled()
        initRunner_ = new WizardRunnerPanel(context_,
                this::buildInitRunner,
                // Site tracks the live parent/name fields so the flag row's --dir reflects them
                () -> new Site(null, resolveInitDir().toString(), null),
                this::onInitCompleted,
                "msg.wizard.init.success",
                "msg.wizard.init.failure");
        initRunner_.setPreferredSize(new Dimension(Integer.MAX_VALUE, 160));

        panel.add(topSection,  BorderLayout.NORTH);
        panel.add(initRunner_, BorderLayout.CENTER);

        refreshInitPreview();
        return panel;
    }

    private InitRunner buildInitRunner() {
        return new InitRunner(resolveInitDir());
    }

    private Path resolveInitDir() {
        String parent = initParentDir_.getText().trim();
        String name   = initDirName_.getTextField().getText().trim();
        return Path.of(parent).resolve(name);
    }

    private void refreshInitPreview() {
        justRanInit_ = false; // user is editing the path again — forget any prior run result
        String parent = initParentDir_.getText().trim();
        String name   = initDirName_.getTextField().getText().trim();
        if (!parent.isEmpty() && !name.isEmpty()) {
            Path full = Path.of(parent).resolve(name);
            initPreview_.setText(full.toString());
        } else {
            initPreview_.setText("");
        }
        // Keep the runner panel's flag row (--dir) in sync with the chosen path
        if (initRunner_ != null) initRunner_.rebuildFlagsRow();
        refreshInitStep();
    }

    private void refreshInitStep() {
        if (!isInitInputValid()) {
            initOk_ = false;
            initStatusArea_.setText(PropertyConfig.getMessage("msg.wizard.init.incomplete"));
            initRunner_.setRunEnabled(false);
            updateNavButtons();
            return;
        }
        Path full = resolveInitDir();
        if (Files.isDirectory(full.resolve("config"))) {
            // Already initialized — skip Run, enable Next
            initOk_  = true;
            initDir_ = full;
            String msgKey = justRanInit_ ? "msg.wizard.init.success" : "msg.wizard.init.alreadydone";
            initStatusArea_.setText(PropertyConfig.getMessage(msgKey, full.toString()));
            initRunner_.setRunEnabled(false);
        } else if (Files.exists(full) && !isEmptyDir(full)) {
            // Chosen full path exists and is a file or non-empty dir — can't init into it
            initOk_ = false;
            initStatusArea_.setText(PropertyConfig.getMessage("msg.wizard.init.exists", full));
            initRunner_.setRunEnabled(false);
        } else {
            // Ready to run init (path doesn't exist, or is an empty leftover folder)
            initOk_ = false;
            initStatusArea_.setText(PropertyConfig.getMessage("msg.wizard.init.ready", full.toString()));
            initRunner_.setRunEnabled(true);
        }
        updateNavButtons();
    }

    /** True if {@code path} is a directory that contains no entries (e.g. a leftover from a prior attempt). */
    private static boolean isEmptyDir(Path path) {
        if (!Files.isDirectory(path)) return false;
        try (Stream<Path> entries = Files.list(path)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isInitInputValid() {
        String parent = initParentDir_.getText().trim();
        String name   = initDirName_.getTextField().getText().trim();
        return !parent.isEmpty()
                && Files.isDirectory(Path.of(parent))
                && !name.isEmpty()
                && name.matches("[^/\\\\\\s]+");
    }

    private void onInitCompleted() {
        justRanInit_ = true;
        // Lock the path fields so further user/cascade interaction can't trigger
        // refreshInitPreview() (which would reset justRanInit_ and lose the success message)
        initParentDir_.setDisplayOnly(true);
        initDirName_.setDisplayOnly(true);
        refreshInitStep();
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private Step currentStep() {
        return ALL_STEPS[stepIndex_];
    }

    /** Lower-case id of the step currently showing (e.g. "welcome", "docker"), for screenshot names. */
    public String currentStepName() {
        return currentStep().name().toLowerCase();
    }

    /** The BASH step only applies on Windows; everywhere else it is skipped during navigation. */
    private boolean isStepEnabled(Step step) {
        return step != Step.BASH || Utils.ISWINDOWS;
    }

    private int indexOf(Step step) {
        for (int i = 0; i < ALL_STEPS.length; i++) {
            if (ALL_STEPS[i] == step) return i;
        }
        return 0;
    }

    /** Next enabled step index at or after {@code from + 1} (clamped to the last index). */
    private int nextStepIndex(int from) {
        int i = from + 1;
        while (i < ALL_STEPS.length - 1 && !isStepEnabled(ALL_STEPS[i])) i++;
        return i;
    }

    /** Previous enabled step index at or before {@code from - 1} (clamped to WELCOME). */
    private int prevStepIndex(int from) {
        int i = from - 1;
        while (i > 0 && !isStepEnabled(ALL_STEPS[i])) i--;
        return i;
    }

    private void showStep(int index) {
        stepIndex_ = index;
        Step step = ALL_STEPS[stepIndex_];

        // Refresh dynamic content when entering a step
        switch (step) {
            case DOCKER -> refreshDockerStep();
            case BASH   -> refreshBashStep();
            case SCRIPT -> refreshScriptStep();
            case INIT   -> refreshInitStep();
            default     -> {}
        }

        cardLayout_.show(cardPanel_, step.name());

        boolean isWelcome = step == Step.WELCOME;
        stepLabel_.setText(isWelcome ? "" : stepTitle(step));
        stepLabel_.setVisible(!isWelcome);

        updateNavButtons();
    }

    private String stepTitle(Step step) {
        if (step == Step.WELCOME) return "";
        String label = PropertyConfig.getMessage("msg.wizard.step." + step.name().toLowerCase());
        return "<HTML>Step " + displayNumber(step) + " - " + label + "</HTML>";
    }

    /** 1-based position of an enabled step among the enabled, non-WELCOME steps (for the title). */
    private int displayNumber(Step step) {
        int n = 0;
        for (int i = 1; i < ALL_STEPS.length; i++) {
            if (isStepEnabled(ALL_STEPS[i])) n++;
            if (ALL_STEPS[i] == step) break;
        }
        return n;
    }

    private void updateNavButtons() {
        if (backBtn_ == null || nextBtn_ == null) return;
        Step step = currentStep();
        boolean isChoice = step == Step.CHOICE;
        boolean isLast   = stepIndex_ == ALL_STEPS.length - 1;

        backBtn_.setVisible(true);
        backBtn_.setEnabled(stepIndex_ > 0);

        nextBtn_.setVisible(!isChoice);
        nextBtn_.setEnabled(isNextEnabled());

        if (isLast) {
            nextBtn_.rename("wizard.addnewsite");
        } else {
            nextBtn_.rename("wizard.next");
        }
    }

    private boolean isNextEnabled() {
        return switch (currentStep()) {
            case WELCOME -> true;
            case DOCKER  -> dockerOk_ && isDockerBinaryValid();
            case BASH    -> isBashBinaryValid();
            case SCRIPT  -> scriptOk_;
            case CHOICE  -> false;
            case INIT    -> initOk_;
        };
    }

    private boolean isDockerBinaryValid() {
        if (dockerBinary_ == null) return false;
        String text = dockerBinary_.getText().trim();
        return !text.isBlank() && Files.isExecutable(Path.of(text));
    }

    private boolean isBashBinaryValid() {
        if (bashBinary_ == null) return false;
        String text = bashBinary_.getText().trim();
        return !text.isBlank() && Files.isExecutable(Path.of(text));
    }

    private void onChooseNewSite() {
        showStep(indexOf(Step.INIT)); // advance from CHOICE to INIT
    }

    private void onChooseExistingSite() {
        doAddSite(); // no init needed — open SiteDialog directly
    }

    private void onBack() {
        if (stepIndex_ > 0) showStep(prevStepIndex(stepIndex_));
    }

    private void onNext() {
        // First time Next is clicked on the Welcome step, require the user to accept the
        // beta warning and license terms before proceeding.
        if (currentStep() == Step.WELCOME && !confirmWelcomeTerms()) {
            return;
        }

        boolean isLast = stepIndex_ == ALL_STEPS.length - 1;
        if (isLast) {
            doAddSite();
        } else if (!rerun_ && currentStep() == Step.SCRIPT && !sitesFile_.getSites().isEmpty()) {
            // Wizard was launched to repair Docker/script, not to add a site — sites already exist
            returnToStartMenu(null);
        } else {
            showStep(nextStepIndex(stepIndex_));
        }
    }

    /**
     * Shows the beta/license confirmation the first time the user advances past the Welcome step.
     * Returns true if it is OK to proceed (already accepted, or the user clicked OK now). If the
     * user clicks Cancel, the app is quit and false is returned.
     */
    private boolean confirmWelcomeTerms() {
        Preferences prefs = PhotosConstants.getAppPreferences();
        if (prefs.getBoolean(PREF_WELCOME_ACCEPTED, false)) {
            return true;
        }

        TypedHashMap params = new TypedHashMap();
        params.setString(DisplayMessage.PARAM_MESSAGE_KEY, "msg.wizard.welcome.confirm");
        Phase confirm = context_.processPhaseNow("WizardWelcomeConfirm", params);
        AppButton pressed = (AppButton) confirm.getResult();

        if (pressed != null && pressed.getName().equals("okay")) {
            prefs.putBoolean(PREF_WELCOME_ACCEPTED, true);
            return true;
        }

        PhotosMain.getBaseApp().exit(0);
        return false;
    }

    // ── Add site (final action) ───────────────────────────────────────────────

    private void doAddSite() {
        Site added = PhotosUtils.openAddSiteDialog(context_, sitesFile_, initDir_);
        if (added != null) {
            EngineUtils.displayInformationDialog(context_,
                    PropertyConfig.getMessage("msg.wizard.addsite.success", added.getDisplayName()),
                    "msg.windowtitle.cmdSuccess", null);
            returnToStartMenu(added);
        }
        // if canceled: stay on current step, user can click Add Site again
    }

    private void returnToStartMenu(Site selectSite) {
        TypedHashMap params = new TypedHashMap();
        if (selectSite != null) {
            params.setObject(PhotosBasePhase.PARAM_SELECT_SITE, selectSite);
        }
        context_.processPhaseNow("StartMenu", params);
    }
}
