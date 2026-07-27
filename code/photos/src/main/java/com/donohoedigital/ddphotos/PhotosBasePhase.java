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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static javax.swing.JTabbedPane.TOP;

public class PhotosBasePhase extends BasePhase {

    public static final String PARAM_RERUN_WIZARD = "rerun-wizard";
    public static final String PARAM_SELECT_SITE  = "select-site";

    public static final String STYLE = "PhotosMain";

    private final SitesFile sitesFile_;

    private AppContext context_;
    protected LogoWindowPanel base_;
    protected DDHtmlArea helptext_;
    private TourController tourController_;

    // Held so the screenshot menu item can name the file after whatever is currently showing.
    private OptionTabbedPane tabs_;
    private WizardPanel wizardPanel_;

    public PhotosBasePhase() {
        Path sitesFilePath = AppConfigUtils.getSaveDir().toPath().resolve("sites.yaml");
        sitesFile_ = new SitesFile(sitesFilePath).load();
    }

    @Override
    public void init(AppEngine engine, AppContext context, AppPhase phase) {
        super.init(engine, context, phase);
        context_ = context;

        base_ = new LogoWindowPanel("icon48", STYLE);
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
        if (rerun || sitesFile_.getSites().isEmpty()
                || !Files.isExecutable(PhotosUtils.scriptPath())
                || !Files.isExecutable(Path.of(DockerStatus.dockerPath()))) {
            buildWizardUI(rerun);
        } else {
            buildRegularUI((Site) phase_.getObject(PARAM_SELECT_SITE));
        }
    }

    private void buildWizardUI(boolean rerun) {
        DDPanel wrapper = new DDPanel();
        wrapper.setLayout(new GridBagLayout());
        wrapper.setOpaque(true);
        wrapper.setBackground(StylesConfig.getColor("app.panel.bg"));
        wizardPanel_ = new WizardPanel(context_, sitesFile_, rerun);
        wrapper.add(wizardPanel_);

        // the wizard fills the window on its own (it sets its own welcome help message)
        base_.setTopBarVisible(false);
        base_.setCenterComponent(wrapper);
    }

    private void buildRegularUI(Site selectSite) {
        // set explicitly: finishing the wizard re-enters this phase, which may reuse the instance
        // (and its hidden logo strip) rather than building a fresh one
        base_.setTopBarVisible(true);

        SiteBarPanel siteBar = new SiteBarPanel(context_, sitesFile_, selectSite);
        base_.setTopComponent(siteBar);
        base_.setTopRightComponent(new DockerStatusPanel(context_));

        OptionTabbedPane tabs = new OptionTabbedPane(TAB_STYLE, TOP, PhotosConstants.PREFS_NODE_APP, "maintabs");
        tabs_ = tabs;
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_AREA_INSETS, new Insets(0, 10, 0, 0));

        // Held for the tour, which selects these tabs and runs their commands (see TourController).
        ConfigTab configTab = new ConfigTab(context_, siteBar);
        CommandRunnerPanel photogenTab = new CommandRunnerPanel(siteBar, new PhotogenRunner(), context_);
        CommandRunnerPanel runTab = new CommandRunnerPanel(siteBar, new RunRunner(), context_);
        CommandRunnerPanel buildTab = new CommandRunnerPanel(siteBar, new BuildRunner(), context_);
        CommandRunnerPanel serveTab = new CommandRunnerPanel(siteBar, new ServeRunner(), context_);
        CommandRunnerPanel deployTab = new CommandRunnerPanel(siteBar, new DeployRunner(), context_);

        tabs.addTab("msg.tab.config", null, null, configTab);
        tabs.addTab("msg.tab.photogen", null, null, photogenTab);
        tabs.addTab("msg.tab.run", null, null, runTab);
        tabs.addTab("msg.tab.build", null, null, buildTab);
        tabs.addTab("msg.tab.serve", null, null, serveTab);
        tabs.addTab("msg.tab.deploy", null, null, deployTab);
        tabs.addTab("msg.tab.export", null, null, new CommandRunnerPanel(siteBar, new ExportRunner(), context_));
        tabs.addTab("msg.tab.wrangler", null, null, new CommandRunnerPanel(siteBar, new WranglerRunner(), context_));
        tabs.addTab("msg.tab.surge", null, null, new CommandRunnerPanel(siteBar, new SurgeRunner(), context_));
        tabs.addTab("msg.tab.upgrade", null, null, new CommandRunnerPanel(siteBar, new UpgradeRunner(), context_));

        tourController_ = new TourController(context_, tabs, configTab, deployTab,
                photogenTab, runTab, buildTab, serveTab);

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
    }

    // -------------------------------------------------------------------------
    // Menu bar
    // -------------------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(buildFileMenu());
        menuBar.add(buildEditMenu());
        menuBar.add(buildHelpMenu());
        return menuBar;
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
        DDMenu menu = new DDMenu("file");

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

    private JMenu buildHelpMenu() {
        DDMenu menu = new DDMenu("help");

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

            JMenuItem ss = new JMenuItem("Take screenshot...");
            ss.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, GuiUtils.MENU_SHORTCUT_MASK));
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

    private void doRerunWizard() {
        TypedHashMap params = new TypedHashMap();
        params.setBoolean(PARAM_RERUN_WIZARD, true);
        context_.processPhaseNow("StartMenu", params);
    }

    private void doResetPrefs() {
        Prefs.clearAll();
        EngineUtils.displayInformationDialog(context_, PropertyConfig.getMessage("msg.resetprefs"));
    }

    private void doResetHiddenDialogs() {
        EnginePrefs.clearDialogPrefs();
        EngineUtils.displayInformationDialog(context_, PropertyConfig.getMessage("msg.resetdialog"));
    }
}
