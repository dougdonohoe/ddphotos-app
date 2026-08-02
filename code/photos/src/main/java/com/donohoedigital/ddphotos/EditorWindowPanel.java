package com.donohoedigital.ddphotos;

import com.donohoedigital.config.StylesConfig;
import com.donohoedigital.gui.LogoWindowPanel;

import javax.swing.*;

/**
 * The window chrome shared by the standalone editor windows ({@link PhotogenEditorPhase},
 * {@link TextEditorPhase}): the logo strip with a widget row beside it, the app panel background,
 * and content insets.
 *
 * <p>The main window's {@link LogoWindowPanel} is set up differently - no top component, and its
 * tabs run flush to the window edges - so it is not a user of this.
 */
class EditorWindowPanel extends LogoWindowPanel {

    EditorWindowPanel(JComponent topBar) {
        super("icon48", PhotosBasePhase.HELP_STYLE);
        setTopComponent(topBar);
        setCenterBackground(StylesConfig.getColor("app.panel.bg"));
        setContentInsets(10, 10, 5, 10);
    }
}
