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
import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import static javax.swing.JTabbedPane.TOP;

import static com.donohoedigital.app.engine.EngineUtils.STANDARD_BORDER_GAP;

public class PhotosBasePhase extends BasePhase {

    public static final String PARAM_RERUN_WIZARD = "rerun-wizard";
    public static final String PARAM_SELECT_SITE  = "select-site";

    private static final String STYLE = "PhotosMain";

    private final SitesFile sitesFile_;

    private AppContext context_;
    protected DDPanel centerPanel_;
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

        centerPanel_ = new DDPanel();
        centerPanel_.setLayout(new BorderLayout());

        helptext_ = new DDHtmlArea(GuiManager.DEFAULT, STYLE);
        helptext_.setDisplayOnly(true);
        helptext_.setOpaque(true);
        helptext_.setBorder(BorderFactory.createEmptyBorder(STANDARD_BORDER_GAP, STANDARD_BORDER_GAP, STANDARD_BORDER_GAP, STANDARD_BORDER_GAP));
        helptext_.setPreferredSize(new Dimension(10000, 40)); // fix height as diff fonts can make it twitchy
        centerPanel_.add(helptext_, BorderLayout.SOUTH);

        context_.setMainUIComponent(this, centerPanel_, true, null);
        context_.getWindow().setHelpTextWidget(helptext_);
        // Fallback for windows without their own help widget (internal dialogs,
        // Help/Support windows), so their hover-help shows in this main help area.
        HelpTextManager.setGlobalHelpTextWidget(helptext_);
        context_.getFrame().setJMenuBar(buildMenuBar());
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

        centerPanel_.add(wrapper, BorderLayout.CENTER);
    }

    private void buildRegularUI(Site selectSite) {
        SiteBarPanel siteBar = new SiteBarPanel(context_, sitesFile_, selectSite);
        DockerStatusPanel dockerStatus = new DockerStatusPanel(context_);
        DDPanel northStrip = new DDPanel();
        northStrip.setLayout(new BorderLayout());
        northStrip.add(siteBar, BorderLayout.CENTER);
        northStrip.add(GuiUtils.NORTH(dockerStatus), BorderLayout.EAST);
        centerPanel_.add(northStrip, BorderLayout.NORTH);

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

        DDPanel tabsContainer = new DDPanel();
        tabsContainer.setLayout(new BorderLayout());
        tabsContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        tabsContainer.add(tabs, BorderLayout.CENTER);
        centerPanel_.add(tabsContainer, BorderLayout.CENTER);

        context_.getWindow().showHelp(siteBar.getLogoComponent()); // init help
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
        menuBar.add(buildHelpMenu());
        return menuBar;
    }

    private JMenu buildFileMenu() {
        DDMenu menu = new DDMenu("file");

        DDMenuItem rerunWizard = new DDMenuItem("rerunwizard");
        rerunWizard.addActionListener(_ -> doRerunWizard());
        menu.add(rerunWizard);

        menu.addSeparator();

        DDMenuItem resetPrefs = new DDMenuItem("resetprefs");
        resetPrefs.addActionListener(_ -> doResetPrefs());
        menu.add(resetPrefs);

        DDMenuItem resetHiddenDialogs = new DDMenuItem("resethidden");
        resetHiddenDialogs.addActionListener(_ -> doResetHiddenDialogs());
        menu.add(resetHiddenDialogs);

        menu.addSeparator();

        // Mac has Quit item under main app menu; see BaseApp.setupMac
        if (!Utils.ISMAC) {
            DDMenuItem quit = new DDMenuItem("quit");
            quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
            quit.addActionListener(_ -> engine_.quit());
            menu.add(quit);
        }

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
        rerunTour.addActionListener(_ -> {
            if (tourController_ != null) tourController_.startFromMenu();
        });
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
            ss.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
            ss.addActionListener(_ ->  {
                context_.screenshot(screenshotName());
            });
            menu.add(ss);
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
