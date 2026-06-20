package com.donohoedigital.ddphotos;

import com.donohoedigital.app.engine.AppContext;
import com.donohoedigital.base.Utils;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.app.engine.EngineUtils;
import com.donohoedigital.gui.DDButton;
import com.donohoedigital.gui.DDIconButtons;
import com.donohoedigital.gui.DDPanel;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DockerStatusPanel extends DDPanel implements DockerStatus.Listener {

    private static final String STYLE = "SiteBar";
    private static final int DEFAULT_ICON_SIZE = 36;

    // Original Docker SVG blue (#0091e2 = RGB 0,145,226)
    private static final int DOCKER_BLUE_R = 0;
    private static final int DOCKER_BLUE_G = 145;
    private static final int DOCKER_BLUE_B = 226;

    private static final Color COLOR_RUNNING       = new Color(46, 160, 67);
    private static final Color COLOR_RUNNING_HOVER = new Color(28, 110, 45);
    private static final Color COLOR_STOPPED       = new Color(200, 40, 40);
    private static final Color COLOR_STOPPED_HOVER = new Color(145, 25, 25);

    private final AppContext context_;
    private final int iconSize_;
    private final DDButton button_;
    private final FlatSVGIcon iconRunning_;
    private final FlatSVGIcon iconRunningHover_;
    private final FlatSVGIcon iconStopped_;
    private final FlatSVGIcon iconStoppedHover_;

    private boolean hovering_ = false;

    public DockerStatusPanel(AppContext context) {
        this(context, DEFAULT_ICON_SIZE);
    }

    public DockerStatusPanel(AppContext context, int iconSize) {
        context_  = context;
        iconSize_ = iconSize;

        iconRunning_      = dockerIcon(COLOR_RUNNING);
        iconRunningHover_ = dockerIcon(COLOR_RUNNING_HOVER);
        iconStopped_      = dockerIcon(COLOR_STOPPED);
        iconStoppedHover_ = dockerIcon(COLOR_STOPPED_HOVER);

        button_ = DDIconButtons.iconButton("dockerstatus", STYLE, iconRunning_);
        button_.setPreferredSize(new Dimension(iconSize_+4, iconSize_+4));
        button_.addActionListener(_ -> onDockerClick());
        button_.setBorder(BorderFactory.createEmptyBorder());
        button_.setContentAreaFilled(false);
        button_.setFocusPainted(false);
        button_.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button_.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                hovering_ = true;
                button_.setIcon(DockerStatus.isDockerRunning() ? iconRunningHover_ : iconStoppedHover_);
            }
            @Override public void mouseExited(MouseEvent e) {
                hovering_ = false;
                button_.setIcon(DockerStatus.isDockerRunning() ? iconRunning_ : iconStopped_);
            }
        });

        setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(button_);

        DockerStatus.addListener(this);
        onDockerStatusChanged(DockerStatus.isDockerRunning());
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        DockerStatus.removeListener(this);
    }

    @Override
    public void onDockerStatusChanged(boolean running) {
        button_.setIcon(running ? (hovering_ ? iconRunningHover_ : iconRunning_)
                                : (hovering_ ? iconStoppedHover_ : iconStopped_));
    }

    private FlatSVGIcon dockerIcon(Color fill) {
        FlatSVGIcon icon = new FlatSVGIcon("icons/docker.svg", iconSize_, iconSize_);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> {
            if (c.getRed() == DOCKER_BLUE_R && c.getGreen() == DOCKER_BLUE_G && c.getBlue() == DOCKER_BLUE_B) {
                return fill;
            }
            return c;
        }));
        return icon;
    }

    private void onDockerClick() {
        String msg;
        if (DockerStatus.isDockerRunning()) {
            msg = PropertyConfig.getMessage("msg.docker.running");
        } else {
            if (Utils.ISLINUX) {
                msg = PropertyConfig.getMessage("msg.docker.notrunning.linux");
            } else if (Utils.ISMAC) {
                msg = PropertyConfig.getMessage("msg.docker.notrunning.mac");
            } else {
                msg = PropertyConfig.getMessage("msg.docker.notrunning.windows");
            }
        }
        EngineUtils.displayInformationDialog(context_, msg, "msg.windowtitle.dockerStatus", null);
    }
}
