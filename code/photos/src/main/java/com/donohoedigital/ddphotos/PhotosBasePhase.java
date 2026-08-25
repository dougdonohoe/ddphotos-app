package com.donohoedigital.ddphotos;

import com.donohoedigital.base.TypedHashMap;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.DebugConfig;
import com.donohoedigital.config.Prefs;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.config.StylesConfig;
import com.donohoedigital.ddphotos.config.Site;
import com.donohoedigital.ddphotos.config.SitesFile;
import com.donohoedigital.ddphotos.runner.BuildRunner;
import com.donohoedigital.ddphotos.runner.DeployRunner;
import com.donohoedigital.ddphotos.runner.ExportRunner;
import com.donohoedigital.ddphotos.runner.PhotogenRunner;
import com.donohoedigital.ddphotos.runner.RunRunner;
import com.donohoedigital.ddphotos.runner.ServeRunner;
import com.donohoedigital.ddphotos.runner.SurgeRunner;
import com.donohoedigital.ddphotos.runner.UpgradeRunner;
import com.donohoedigital.ddphotos.runner.WranglerRunner;
import com.donohoedigital.app.config.AppButton;
import com.donohoedigital.app.config.AppConfigUtils;
import com.donohoedigital.app.config.AppPhase;
import com.donohoedigital.app.engine.*;
import com.donohoedigital.gui.*;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static javax.swing.JTabbedPane.TOP;

public class PhotosBasePhase extends BasePhase {

    public static final String PARAM_RERUN_WIZARD = "rerun-wizard";
    public static final String PARAM_NEW_SITE     = "new-site";
    public static final String PARAM_SELECT_SITE  = "select-site";

    public static final String HELP_STYLE = "PhotosHelp";

    private final SitesFile sitesFile_;

    private AppContext context_;
    protected LogoWindowPanel base_;
    protected DDHtmlArea helptext_;
    private TourController tourController_;
    private PublishController publishController_;

    // Held so the screenshot menu item can name the file after whatever is currently showing.
    private OptionTabbedPane tabs_;
    private WizardPanel wizardPanel_;

    // True while the wizard owns the window instead of the tabs - see buildWizardUI.
    private boolean wizardUp_;

    // Held so the Run menu can name and enable its items for whichever site is selected.
    private SiteBarPanel siteBar_;

    // Held so Run -> Photogen... can select that tab and press its Run button.
    private CommandRunnerPanel photogenTab_;

    // Every command tab, so anything that discards them can first deal with what they are
    // running - see okayToLeaveEditor.  Empty while the wizard is up.
    private List<CommandRunnerPanel> runnerTabs_ = List.of();

    /**
     * Every menu whose items {@link #refreshMenu} governs, built so far.  There is one set per
     * window on a Mac (see {@link #init}) and they all have to be re-labeled and re-enabled when
     * the app's state changes, but a closed window's menu must not keep that window alive - hence
     * the weak references, dropped as they are found cleared in {@link #refreshMenus}.
     */
    private final List<WeakReference<DDMenu>> menus_ = new ArrayList<>();

    public PhotosBasePhase() {
        Path sitesFilePath = AppConfigUtils.getSaveDir().toPath().resolve("sites.yaml");
        sitesFile_ = new SitesFile(sitesFilePath).load();
    }

    @Override
    public void init(AppEngine engine, AppContext context, AppPhase phase) {
        super.init(engine, context, phase);
        context_ = context;

        base_ = new LogoWindowPanel("icon48", HELP_STYLE);
        base_.setContentInsets(0, 0, 0, 0); // tabs run flush to the window edges
        helptext_ = base_.getHelpText();

        context_.setMainUIComponent(this, base_, true, null);
        context_.getWindow().setHelpTextWidget(helptext_);
        // Fallback for windows without their own help widget (internal dialogs,
        // Help/Support windows), so their hover-help shows in this main help area.
        HelpTextManager.setGlobalHelpTextWidget(helptext_);
        context_.getFrame().setJMenuBar(buildMenuBar());

        // On a Mac the menu bar is the screen menu bar and belongs to whichever window has
        // focus, so the Help, Support and photogen editor windows would show no menus at all.
        // Give each new window its own copy instead; every item still acts on the main window
        // (the listeners below close over this phase), so the menus behave identically no
        // matter which window they are pulled down from.  Elsewhere, the menu bar is drawn in
        // the window, where the main window's stays put and per-window copies would only take
        // up space.
        if (Utils.ISMAC) {
            engine_.setWindowMenuBarFactory(this::buildMenuBar);
        }
    }

    private static final String TAB_STYLE = "PhotosTabs";

    @Override
    public void start() {
        boolean rerun = phase_.getBoolean(PARAM_RERUN_WIZARD, false);
        if (phase_.getBoolean(PARAM_NEW_SITE, false)) {
            // File -> New Site: everything the setup steps check has already been checked, so the
            // wizard opens on the step that creates the site folder.
            buildWizardUI(WizardPanel.Mode.NEW_SITE);
        } else if (rerun || sitesFile_.getSites().isEmpty() || setupIncomplete()) {
            buildWizardUI(rerun ? WizardPanel.Mode.RERUN : WizardPanel.Mode.FIRST_RUN);
        } else {
            buildRegularUI((Site) phase_.getObject(PARAM_SELECT_SITE));
        }
    }

    /**
     * True when any of the pieces the setup wizard puts in place is missing: the {@code ddphotos}
     * script, the Docker binary, and on Windows the Git Bash that the script's {@code .cmd}
     * wrapper runs under.  Any of them can go away after setup - uninstalled, moved, or its stored
     * location cleared by Reset Preferences - and the wizard is where they are found again.  Bash
     * is checked on Windows only because that is the only platform whose wizard has the step (see
     * {@code WizardPanel.isStepEnabled}); elsewhere it would strand the user on a step that is
     * skipped.  Each stored path falls back to a bare command name, which is relative and so never
     * executable - that is what makes a cleared preference read as missing here.
     */
    private static boolean setupIncomplete() {
        return !Files.isExecutable(PhotosUtils.scriptPath())
                || !Files.isExecutable(Path.of(DockerStatus.dockerPath()))
                || (Utils.ISWINDOWS && !Files.isExecutable(Path.of(BashSupport.bashPath())));
    }

    private void buildWizardUI(WizardPanel.Mode mode) {
        DDPanel wrapper = new DDPanel();
        wrapper.setLayout(new GridBagLayout());
        wrapper.setOpaque(true);
        wrapper.setBackground(StylesConfig.getColor("app.panel.bg"));
        wizardPanel_ = new WizardPanel(context_, sitesFile_, mode);
        wrapper.add(wizardPanel_);

        // the wizard fills the window on its own (it sets its own welcome help message)
        base_.setTopBarVisible(false);
        base_.setCenterComponent(wrapper);

        // The command tabs are gone with the editor - okayToLeaveEditor has already settled
        // anything they were running.
        runnerTabs_ = List.of();

        // The menu bar is built in init(), before start() has decided wizard or tabs, so the
        // wizard's rules can only be applied from here.
        wizardUp_ = true;
        refreshMenus();
    }

    private void buildRegularUI(Site selectSite) {
        // all set explicitly: finishing the wizard re-enters this phase, which may reuse the
        // instance (and its hidden logo strip, and the wizard it has just finished with) rather
        // than building a fresh one
        base_.setTopBarVisible(true);
        wizardUp_ = false;
        wizardPanel_ = null;

        SiteBarPanel siteBar = new SiteBarPanel(context_, sitesFile_, selectSite);
        siteBar_ = siteBar;
        base_.setTopComponent(siteBar);
        base_.setTopRightComponent(new DockerStatusPanel(context_));

        OptionTabbedPane tabs = new OptionTabbedPane(TAB_STYLE, TOP, PhotosConstants.PREFS_NODE_APP, "maintabs");
        tabs_ = tabs;
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_AREA_INSETS, new Insets(0, 10, 0, 0));

        // Held for the tour, which selects these tabs and watches their commands (see TourController).
        ConfigTab configTab = new ConfigTab(context_, siteBar);
        CommandRunnerPanel photogenTab = new CommandRunnerPanel(siteBar, new PhotogenRunner(), context_);
        photogenTab_ = photogenTab;
        CommandRunnerPanel runTab = new CommandRunnerPanel(siteBar, new RunRunner(), context_);
        CommandRunnerPanel buildTab = new CommandRunnerPanel(siteBar, new BuildRunner(), context_);
        CommandRunnerPanel serveTab = new CommandRunnerPanel(siteBar, new ServeRunner(), context_);
        CommandRunnerPanel deployTab = new CommandRunnerPanel(siteBar, new DeployRunner(), context_);
        // Held for publish, which selects these tabs and runs their commands in turn.
        CommandRunnerPanel exportTab = new CommandRunnerPanel(siteBar, new ExportRunner(), context_);
        CommandRunnerPanel wranglerTab = new CommandRunnerPanel(siteBar, new WranglerRunner(), context_);
        CommandRunnerPanel surgeTab = new CommandRunnerPanel(siteBar, new SurgeRunner(), context_);

        // Every command tab is added with its own idle lamp, which the panel swaps for a green
        // bolt while its command runs - see PhotosTabIcons. The lamps are per-tab because they
        // brighten on the selected tab, which each one has to work out for itself.
        CommandRunnerPanel upgradeTab = new CommandRunnerPanel(siteBar, new UpgradeRunner(sitesFile_), context_);
        tabs.addTab("msg.tab.config", PhotosTabIcons.config(configTab), null, configTab);
        tabs.addTab("msg.tab.photogen", PhotosTabIcons.idle(photogenTab), null, photogenTab);
        tabs.addTab("msg.tab.run", PhotosTabIcons.idle(runTab), null, runTab);
        tabs.addTab("msg.tab.build", PhotosTabIcons.idle(buildTab), null, buildTab);
        tabs.addTab("msg.tab.serve", PhotosTabIcons.idle(serveTab), null, serveTab);
        tabs.addTab("msg.tab.deploy", PhotosTabIcons.idle(deployTab), null, deployTab);
        tabs.addTab("msg.tab.export", PhotosTabIcons.idle(exportTab), null, exportTab);
        tabs.addTab("msg.tab.wrangler", PhotosTabIcons.idle(wranglerTab), null, wranglerTab);
        tabs.addTab("msg.tab.surge", PhotosTabIcons.idle(surgeTab), null, surgeTab);
        tabs.addTab("msg.tab.upgrade", PhotosTabIcons.idle(upgradeTab), null, upgradeTab);

        runnerTabs_ = List.of(photogenTab, runTab, buildTab, serveTab, deployTab,
                exportTab, wranglerTab, surgeTab, upgradeTab);

        tourController_ = new TourController(context_, tabs, configTab, deployTab,
                photogenTab, runTab, buildTab, serveTab, this::refreshMenus);

        Map<PublishSettings.Step, CommandRunnerPanel> publishPanels =
                new EnumMap<>(PublishSettings.Step.class);
        publishPanels.put(PublishSettings.Step.PHOTOGEN, photogenTab);
        publishPanels.put(PublishSettings.Step.BUILD, buildTab);
        publishPanels.put(PublishSettings.Step.DEPLOY, deployTab);
        publishPanels.put(PublishSettings.Step.EXPORT, exportTab);
        publishPanels.put(PublishSettings.Step.WRANGLER, wranglerTab);
        publishPanels.put(PublishSettings.Step.SURGE, surgeTab);
        publishController_ = new PublishController(context_, tabs, siteBar, publishPanels,
                this::refreshMenus);

        // The Run menu items name the selected site and are disabled without one.  Menus already
        // built (the main window's, from init()) are refreshed here too - they were built before
        // there was a site bar to ask.
        siteBar.addSiteListener(_ -> refreshMenus());
        refreshMenus();

        // If no selected site (site.yaml deleted, or not created yet), don't restore to previous selected tab,
        // as only the Config tab can handle no sites)
        if (siteBar.getSelectedSite() != null) {
            tabs.restoreFromPrefs();
        }

        base_.setCenterComponent(tabs);

        context_.getWindow().showHelp(base_.getLogoComponent()); // init help
        context_.getWindow().ignoreNextHelp(); // ignore enter so main help message doesn't go away immediately

        // Offer the new-user tour on every launch until it is completed or opted out of (the
        // Welcome dialog's no-show option suppresses it after that). Deferred so the frame is
        // realized first, like the showHelp call above.
        SwingUtilities.invokeLater(tourController_::start);

        // Ask GitHub whether a newer DD Photos has been released.  Only from here: the wizard has
        // enough to say already, and the check runs off the EDT and stays quiet unless there is
        // news, so it never holds up the editor appearing.
        UpdateCheck.checkAtStartup(context_, this::isBusy);
    }

    // -------------------------------------------------------------------------
    // Menu bar
    // -------------------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(buildFileMenu());
        menuBar.add(buildEditMenu());
        menuBar.add(buildRunMenu());
        menuBar.add(buildHelpMenu());
        return menuBar;
    }

    /**
     * A menu whose items {@link #refreshMenu} governs.  Tracked so a state change elsewhere can
     * reach it (there is a copy per window on a Mac), and refreshed again as it opens - the
     * selected site's name can change under us, and pushed refreshes are one-way.
     */
    private DDMenu trackedMenu(String name) {
        DDMenu menu = new DDMenu(name);
        menus_.add(new WeakReference<>(menu));
        menu.addMenuListener(new MenuListener() {
            public void menuSelected(MenuEvent e) { refreshMenu(menu); }

            public void menuDeselected(MenuEvent e) {}

            public void menuCanceled(MenuEvent e) {}
        });
        return menu;
    }

    /**
     * Wraps an action that runs in the main window, bringing that window forward first.
     * Every window gets its own copy of these menus on a Mac (see init), and each copy runs
     * the main window's command - so without this the wizard, the tour or a confirmation
     * dialog would appear behind whichever window the menu was pulled down from.
     */
    private ActionListener mainWindowAction(Runnable action) {
        return _ -> {
            context_.getWindow().toFront();
            action.run();
        };
    }

    private JMenu buildFileMenu() {
        DDMenu menu = trackedMenu("file");

        DDMenuItem newSite = new DDMenuItem("newsite");
        newSite.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, GuiUtils.MENU_SHORTCUT_MASK));
        newSite.addActionListener(mainWindowAction(this::doNewSite));
        menu.add(newSite);

        menu.addSeparator();

        DDMenuItem rerunWizard = new DDMenuItem("rerunwizard");
        rerunWizard.addActionListener(mainWindowAction(this::doRerunWizard));
        menu.add(rerunWizard);

        menu.addSeparator();

        DDMenuItem resetPrefs = new DDMenuItem("resetprefs");
        resetPrefs.addActionListener(mainWindowAction(this::doResetPrefs));
        menu.add(resetPrefs);

        DDMenuItem resetHiddenDialogs = new DDMenuItem("resethidden");
        resetHiddenDialogs.addActionListener(mainWindowAction(this::doResetHiddenDialogs));
        menu.add(resetHiddenDialogs);

        menu.addSeparator();

        // Mac has Quit item under main app menu; see BaseApp.setupMac
        if (!Utils.ISMAC) {
            DDMenuItem quit = new DDMenuItem("quit");
            quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, GuiUtils.MENU_SHORTCUT_MASK));
            quit.addActionListener(_ -> engine_.quit());
            menu.add(quit);
        }

        refreshMenu(menu);
        return menu;
    }

    /**
     * Wraps an action that runs against whichever text widget has keyboard focus.  Deliberately
     * not mainWindowAction: undo has to reach the field the user is actually typing in, which is
     * often a caption in the photogen editor window rather than anything in the main window.
     */
    private ActionListener focusedTextAction(Function<DDUndoManager, Action> which) {
        return e -> {
            DDUndoManager undo = DDUndoManager.forFocusOwner();
            if (undo != null) which.apply(undo).actionPerformed(e);
        };
    }

    private JMenu buildEditMenu() {
        DDMenu menu = new DDMenu("edit");

        DDMenuItem undo = new DDMenuItem("undo");
        undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, GuiUtils.MENU_SHORTCUT_MASK));
        undo.addActionListener(focusedTextAction(DDUndoManager::getUndoAction));
        menu.add(undo);

        DDMenuItem redo = new DDMenuItem("redo");
        redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                GuiUtils.MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK));
        redo.addActionListener(focusedTextAction(DDUndoManager::getRedoAction));
        menu.add(redo);

        // Whether there is anything to undo depends on which field has focus, so work it out as
        // the menu opens.  Both go back to enabled once it closes: a disabled item doesn't fire
        // its accelerator, and the keys have to keep working with no menu showing.
        menu.addMenuListener(new MenuListener() {
            public void menuSelected(MenuEvent e) {
                DDUndoManager mgr = DDUndoManager.forFocusOwner();
                undo.setEnabled(mgr != null && mgr.canUndo());
                redo.setEnabled(mgr != null && mgr.canRedo());
            }

            public void menuDeselected(MenuEvent e) { enable(); }

            public void menuCanceled(MenuEvent e) { enable(); }

            private void enable() {
                undo.setEnabled(true);
                redo.setEnabled(true);
            }
        });

        return menu;
    }

    /**
     * The Run menu: run this site's most-repeated command, {@code photogen}, and set up and run
     * the whole publishing sequence.  Every item names the selected site and is disabled without
     * one; Publish additionally waits until the settings have been opened (see
     * {@link PublishSettings#isConfigured}).
     */
    private JMenu buildRunMenu() {
        DDMenu menu = trackedMenu("run");

        DDMenuItem photogen = new DDMenuItem("photogen");
        photogen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, GuiUtils.MENU_SHORTCUT_MASK));
        photogen.addActionListener(mainWindowAction(this::doPhotogen));
        menu.add(photogen);

        menu.addSeparator();

        DDMenuItem settings = new DDMenuItem("publishsettings");
        settings.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,
                GuiUtils.MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK));
        settings.addActionListener(mainWindowAction(this::doPublishSettings));
        menu.add(settings);

        DDMenuItem publish = new DDMenuItem("publishrun");
        publish.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, GuiUtils.MENU_SHORTCUT_MASK));
        publish.addActionListener(mainWindowAction(this::doPublish));
        menu.add(publish);

        refreshMenu(menu);
        return menu;
    }

    // -------------------------------------------------------------------------
    // Which menu items make sense right now
    // -------------------------------------------------------------------------

    /** Re-labels and re-enables every live menu, forgetting those whose window is gone. */
    private void refreshMenus() {
        menus_.removeIf(ref -> {
            DDMenu menu = ref.get();
            if (menu == null) return true;
            refreshMenu(menu);
            return false;
        });
    }

    /**
     * True while the wizard, the tour or a publish run owns the main UI: the wizard replaces the
     * tabs, the tour walks the user through them, and a publish run presses their Run buttons in
     * turn.  Nothing that rebuilds or drives that UI may start meanwhile, and nothing may
     * interrupt it with a dialog of its own (see {@link UpdateCheck#checkAtStartup}).
     *
     * <p>For the menus this means disabling the items outright rather than relying on modality: a
     * publish run's dialogs are modal, but that blocks the mouse only - a menu accelerator would
     * still fire, and a disabled item doesn't fire its accelerator (as the Edit menu notes).
     */
    private boolean isBusy() {
        return wizardUp_
                || (tourController_ != null && tourController_.isRunning())
                || (publishController_ != null && publishController_.isRunning());
    }

    /**
     * The rules for every item whose availability depends on what the app is doing.  Items not
     * named here - Reset Preferences, Quit, Help, Support, About - are always available.
     */
    private void refreshMenu(DDMenu menu) {
        Site site = siteBar_ != null ? siteBar_.getSelectedSite() : null;

        boolean busy = isBusy();

        for (int i = 0; i < menu.getItemCount(); i++) {
            if (!(menu.getItem(i) instanceof DDMenuItem item)) continue; // separators
            switch (item.getName()) {
                case "rerunwizard", "reruntour" -> item.setEnabled(!busy);
                // A command tab running does not gray this one out: it hands the window to the
                // wizard, and okayToLeaveEditor asks about that when the item is picked.
                case "newsite" -> item.setEnabled(!busy && site != null);
                case "photogen" -> {
                    setSiteLabel(item, site);
                    // Off mid-run too: the tab's Run button is disabled then, and startRun()
                    // reports that as an invalid flag rather than as the "already running" it is.
                    item.setEnabled(!busy && site != null
                            && photogenTab_ != null && !photogenTab_.isRunning());
                }
                case "publishsettings" -> {
                    setSiteLabel(item, site);
                    item.setEnabled(!busy && site != null);
                }
                case "publishrun" -> {
                    setSiteLabel(item, site);
                    item.setEnabled(!busy && PublishSettings.isConfigured(site));
                }
                default -> { }
            }
        }
    }

    /**
     * Confirms leaving the editor for the wizard.  The wizard takes the window, so the command
     * tabs go with it - and with them the only handle on any process they have running, which
     * would otherwise carry on unwatched with no way to stop it.  Each tab is asked the same way
     * quitting asks (see {@link AbstractRunnerPanel#confirmStopRunning}), stopping its command
     * when the user agrees; a no from any of them abandons the whole thing.
     */
    private boolean okayToLeaveEditor() {
        for (CommandRunnerPanel tab : runnerTabs_) {
            if (!tab.confirmStopRunning("msg.leave.running.confirm", null)) return false;
        }
        return true;
    }

    /**
     * Run menu items name the site they act on - "Publish (Manly Man)..." - falling back to a
     * bare label when there is none (the wizard, or every site removed).
     */
    private static void setSiteLabel(DDMenuItem item, Site site) {
        if (site != null) {
            GuiManager.setLabelAsMessage(item, site.getDisplayName());
        } else {
            item.setText(PropertyConfig.getMessage("menuitem." + item.getName() + ".nosite"));
        }
    }

    // -------------------------------------------------------------------------
    // Run menu actions
    // -------------------------------------------------------------------------

    /**
     * Selects the Photogen tab and presses its Run button, the same way a publish step does (see
     * {@link PublishController}) - {@code photogen} is rerun after every config, album or caption
     * change, so it is worth a keystroke.
     */
    private void doPhotogen() {
        if (tabs_ == null || photogenTab_ == null) return;

        tabs_.setSelectedComponent(photogenTab_);
        // The tab's UI is built lazily, and we are about to press its Run button. Harmless if the
        // selection above already triggered it - initUI() only builds once.
        photogenTab_.initUI();

        // Already running: the menu item is disabled for this, but the accelerator can arrive
        // between refreshes. The tab is now in front with its Run button disabled, which says it
        // better than startRun()'s "a required value is missing or invalid" console line would.
        if (photogenTab_.isRunning()) return;

        photogenTab_.startRun();
    }

    private void doPublishSettings() {
        Site site = siteBar_ != null ? siteBar_.getSelectedSite() : null;
        if (site == null) return;

        TypedHashMap params = new TypedHashMap();
        params.setObject(PublishSettingsDialog.PARAM_SITE, site);
        // Modal, so this returns only once the dialog is gone and its result is in.
        Phase settings = context_.processPhaseNow("PublishSettingsDialog", params);
        // Closing the dialog is what enables the Publish item.
        refreshMenus();

        // Its Publish button is a shortcut past the menu - run it now that the dialog is out of
        // the way, so the publish dialogs have the screen to themselves.
        if (settings.getResult() instanceof AppButton button
                && PublishSettingsDialog.BUTTON_PUBLISH.equals(button.getName())) {
            doPublish();
        }
    }

    private void doPublish() {
        if (publishController_ != null) publishController_.start();
    }

    private JMenu buildHelpMenu() {
        DDMenu menu = trackedMenu("help");

        // Mac has About item under main app menu; see BaseApp.setupMac
        if (!Utils.ISMAC) {
            DDMenuItem about = new DDMenuItem("about");
            about.addActionListener(_ -> PhotosMain.getBaseApp().showAbout());
            menu.add(about);

            menu.addSeparator();
        }

        DDMenuItem rerunTour = new DDMenuItem("reruntour");
        rerunTour.addActionListener(mainWindowAction(() -> {
            if (tourController_ != null) tourController_.startFromMenu();
        }));
        menu.add(rerunTour);

        DDMenuItem checkUpdate = new DDMenuItem("checkupdate");
        checkUpdate.addActionListener(mainWindowAction(() -> UpdateCheck.checkFromMenu(context_)));
        menu.add(checkUpdate);

        menu.addSeparator();

        DDMenuItem help = new DDMenuItem("help");
        help.addActionListener(_ -> PhotosMain.getBaseApp().showHelp());
        menu.add(help);

        DDMenuItem support = new DDMenuItem("support");
        support.addActionListener(_ -> PhotosMain.getBaseApp().showSupport());
        menu.add(support);

        if (DebugConfig.isTestingOn()) {
            menu.addSeparator();

            JMenuItem boom = new JMenuItem("BOOM!");
            boom.addActionListener(_ ->  { throw new RuntimeException("BOOM!"); });
            menu.add(boom);

            // Cmd/Ctrl-R: the Run menu has Cmd-P and Cmd-G, and this one is debug-only.
            JMenuItem ss = new JMenuItem("Take screenshot...");
            ss.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, GuiUtils.MENU_SHORTCUT_MASK));
            ss.addActionListener(mainWindowAction(() -> context_.screenshot(screenshotName())));
            menu.add(ss);

            JMenuItem splash = new JMenuItem("Show Splashscreen");
            splash.addActionListener(_ -> engine_.showSplashScreenAgain());
            menu.add(splash);

            JMenuItem display = new JMenuItem("Display Info...");
            display.addActionListener(mainWindowAction(() -> EngineUtils.displayInformationDialog(
                    context_, GuiUtils.getDisplayInfoHtml(context_.getFrame()))));
            menu.add(display);
        }

        refreshMenu(menu);
        return menu;
    }

    /**
     * Name for a screenshot of whatever is currently showing: the running wizard step
     * (e.g. "wizard-docker") when the wizard panel is up, otherwise the selected tab's
     * title slugified (e.g. "config", "photogen"). Falls back to "screenshot".
     */
    private String screenshotName() {
        if (wizardPanel_ != null && wizardPanel_.isShowing()) {
            return "wizard-" + wizardPanel_.currentStepName();
        }
        if (tabs_ != null && tabs_.getSelectedIndex() >= 0) {
            return slug(tabs_.getTitleAt(tabs_.getSelectedIndex()));
        }
        return "screenshot";
    }

    /** Lower-case, filename-safe form of a tab title (e.g. "Photo Gen" -> "photo-gen"). */
    private static String slug(String title) {
        if (title == null || title.isBlank()) return "screenshot";
        return title.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /** Re-enters this phase with the wizard opened on its create-site-folder step - see WizardPanel.Mode. */
    private void doNewSite() {
        if (!okayToLeaveEditor()) return;

        TypedHashMap params = new TypedHashMap();
        params.setBoolean(PARAM_NEW_SITE, true);
        context_.processPhaseNow("StartMenu", params);
    }

    private void doRerunWizard() {
        if (!okayToLeaveEditor()) return;

        TypedHashMap params = new TypedHashMap();
        params.setBoolean(PARAM_RERUN_WIZARD, true);
        context_.processPhaseNow("StartMenu", params);
    }

    /**
     * Throws away every saved preference, so it asks first - there is no undo, and nothing else
     * in the app hints at how much is stored (see msg.confirm.resetprefs for the list).  A
     * warning (orange title bar) with no "don't show again" option: this one has to be read.
     */
    private void doResetPrefs() {
        if (!EngineUtils.displayWarningConfirmationDialog(context_,
                PropertyConfig.getMessage("msg.confirm.resetprefs"),
                "msg.windowtitle.resetPrefs", null)) {
            return;
        }

        Prefs.clearAll();
        EngineUtils.displayInformationDialog(context_, PropertyConfig.getMessage("msg.resetprefs"));
    }

    private void doResetHiddenDialogs() {
        EnginePrefs.clearDialogPrefs();
        EngineUtils.displayInformationDialog(context_, PropertyConfig.getMessage("msg.resetdialog"));
    }
}
