package com.donohoedigital.app.engine;

import com.donohoedigital.base.*;
import com.donohoedigital.config.*;
import com.donohoedigital.app.config.*;
import com.donohoedigital.gui.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;

/**
 * Standalone support window - shows version/log info and provides
 * buttons to copy it to the clipboard or open the folder containing it.
 */
public class Support extends BasePhase {
    private LogoWindowPanel base_;
    private DDTextArea log_;
    private boolean bRunning_ = false;

    /**
     * init data
     */
    @Override
    public void init(AppEngine engine, AppContext context, AppPhase phase) {
        super.init(engine, context, phase);

        createDialogContents();
    }

    /**
     * create UI
     */
    private void createDialogContents() {
        String STYLE = phase_.getString(DialogPhase.PARAM_STYLE, GuiManager.DEFAULT);

        base_ = new LogoWindowPanel("icon48", phase_.getString(EngineConstants.PARAM_HELP_STYLE, null));
        base_.setTopComponent(new DDLabel("supportwindow", STYLE));

        // the buttons sit below the gray area, on the window's white background, so the contents
        // are a transparent wrapper rather than the gray panel itself
        DDPanel content = new DDPanel();
        base_.setCenterComponent(content);

        DDPanel middlebase = new DDPanel();
        middlebase.setBackground(StylesConfig.getColor("app.panel.bg"));
        middlebase.setOpaque(true);
        content.add(middlebase, BorderLayout.CENTER);

        DDHtmlArea info = new DDHtmlArea("support", STYLE);
        middlebase.add(info, BorderLayout.NORTH);
        info.setText(PropertyConfig.getMessage("msg.support"));
        info.setBorder(BorderFactory.createEmptyBorder(10, 2, 10, 2));
        info.addHyperlinkListener(GuiUtils.HYPERLINK_HANDLER);

        log_ = new DDTextArea("support", STYLE);
        log_.setOpaque(true);
        log_.setEditable(false);
        log_.setFocusable(true);

        DDScrollPane scroll = new DDScrollPane(log_, STYLE, DDScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                DDScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        middlebase.add(scroll, BorderLayout.CENTER);

        DDPanel buttons = new DDPanel();
        buttons.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        DDButton openFolder = new DDButton("support-openfolder", STYLE);
        openFolder.addActionListener(_ -> doOpenFolder());
        buttons.add(openFolder);

        DDButton copy = new DDButton("support-copy", STYLE);
        copy.addActionListener(_ -> doClipboard());
        buttons.add(copy);

        content.add(buttons, BorderLayout.SOUTH);
    }

    /**
     * Start of phase
     */
    @Override
    public void start() {
        updateLog();

        if (bRunning_) return;
        bRunning_ = true;

        // place the whole thing in the Engine's base panel
        context_.setMainUIComponent(this, base_, true, log_);
        if (base_.getHelpText() != null) context_.getWindow().setHelpTextWidget(base_.getHelpText());
    }

    /**
     * finish
     */
    @Override
    public void finish() {
        bRunning_ = false;
        super.finish();
    }

    /**
     * set current log file into text area
     */
    private void updateLog() {
        Properties props = System.getProperties();
        StringBuilder data = new StringBuilder();

        data.append("------------------------------------------------------------------------------------\n");
        data.append("Version:           ").append(engine_.getVersion()).append("\n");
        data.append("Build Number:      ").append(PropertyConfig.getBuildNumber()).append("\n");
        data.append("Java Version:      ").append(props.getProperty("java.runtime.version")).append("\n");
        data.append("Operating System:  ").append(Utils.OS).append("\n");
        data.append("User directory:    ").append(ConfigManager.getUserHome()).append("\n");
        data.append("Free memory:       ").append(Runtime.getRuntime().freeMemory()).append("\n");
        data.append("Total memory:      ").append(Runtime.getRuntime().totalMemory()).append("\n");
        data.append("Max memory:        ").append(Runtime.getRuntime().maxMemory()).append("\n");
        data.append("------------------------------------------------------------------------------------\n");
        data.append("Log file:\n\n");

        File logFile = LoggingConfig.getLoggingConfig().getLogFile();
        if (logFile != null && logFile.exists() && logFile.isFile()) {
            data.append(ConfigUtils.readFile(logFile));
        } else {
            data.append("No log file found.");
        }

        log_.setText(data.toString());
        log_.setCaretPosition(0);
    }

    /**
     * open the folder containing the log file (or the user's data folder
     * if the log file doesn't exist) in the OS file manager
     */
    private void doOpenFolder() {
        File logFile = LoggingConfig.getLoggingConfig().getLogFile();
        File folder = (logFile != null && logFile.getParentFile() != null) ?
                logFile.getParentFile() : ConfigManager.getUserHome();

        if (!Utils.openFolder(folder)) {
            EngineUtils.displayErrorDialog(context_, PropertyConfig.getMessage("msg.error.openfolder", folder.getAbsolutePath()));
        }
    }

    /**
     * copy log file and other debug information to clipboard
     */
    private void doClipboard() {
        GuiUtils.copyToClipboard(log_.getText());
        EngineUtils.displayInformationDialog(context_, PropertyConfig.getMessage("msg.support.copied"));
    }
}
