/*
 * SplashScreen.java
 *
 * Created on October 8, 2002, 1:58 PM
 */

package com.donohoedigital.app.engine;

import com.donohoedigital.config.*;
import com.donohoedigital.gui.*;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.net.*;

/**
 * Splash screen.  Displayed from AppEngine as soon as possible, then later updated
 * when config files have been loaded.  Best not to use logger / PropertyConfig or other
 * config related APIs in constructor.
 *
 * @author Doug Donohoe
 */
public class SplashScreen extends JFrame
{
    private AppEngine engine_;
    private final ImageComponent ic_;
    private final URL bgFile_;

    /**
     * initial splash - shown as soon as possible
     */
    public SplashScreen(URL bg, URL icon, String sTitle)
    {
        super();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setUndecorated(true);
        setResizable(false);
        setTitle(sTitle);

        // load file directly (since config files not loaded)
        bgFile_ = bg;
        BufferedImage img = ImageDef.getBufferedImage(bg);
        ic_ = new ImageComponent(img, 1.0d);
        ic_.setLayout(new XYLayout());
        setContentPane(ic_);

        // icon
        setIconImage(ImageDef.getBufferedImage(icon));

        // frame final setup
        validate();
        pack();
        center();
    }

    /**
     * Called by AppEngine after config files loaded
     */
    public void changeUI(AppEngine engine, String sErrorMessage)
    {
        engine_ = engine;

        int BUTTONSIZE = 15;
        XYConstraints xy;
        setTitle(PropertyConfig.getMessage("msg.title.splash"));

        String sKey = "splash";
        if (sErrorMessage != null) sKey = "splash-empty";

        // localize
        sKey = PropertyConfig.localize(sKey, engine_.getLocale());
        ImageDef img = ImageConfig.getImageDef(sKey);
        if (!img.getImageURL().toString().equals(bgFile_.toString()))
        {
            ic_.changeName(sKey);
        }

        // version label
        DDLabel version = new DDLabel("version", "Splash");
        GuiManager.setLabelAsMessage(version, engine_.getVersion());
        version.setHorizontalAlignment(SwingConstants.CENTER);
        Dimension size = version.getPreferredSize();
        JComponent versionpanel = GuiUtils.NORTH(version);
        xy = new XYConstraints(ic_.getWidth() - size.width - BUTTONSIZE - 11, 5, size.width, size.height);
        ic_.add(versionpanel, xy);

        if (sErrorMessage != null)
        {
            DDLabel wrong = new DDLabel(GuiManager.DEFAULT, "SplashWrong");
            wrong.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));
            wrong.setOpaque(true);
            wrong.setText(sErrorMessage);
            Dimension wrongPreferred = wrong.getPreferredSize();
            int x = (ic_.getWidth() - wrongPreferred.width)/2;
            int y = (ic_.getHeight() - wrongPreferred.height)/2;
            xy = new XYConstraints(x, y, wrongPreferred.width, wrongPreferred.height);
            ic_.add(wrong, xy);

            MouseAdapter listener = new MouseAdapter()
            {
                // exit if clicked
                public void mouseReleased(MouseEvent e)
                {
                    engine_.exit(0);
                }
            };
            GuiUtils.addMouseListenerChildren(this, listener);
            getContentPane().addMouseListener(listener);
        }

        // exit button
        DDButton exit = new DDButton("exitsplash", "Splash");
        exit.addActionListener((_) -> engine_.exit(0));
        exit.setFocusable(false);
        exit.setFocusPainted(false);
        xy = new XYConstraints(ic_.getWidth() - BUTTONSIZE - 6, 5, BUTTONSIZE, BUTTONSIZE);
        ic_.add(exit, xy);

        // frame final setup
        validate();
        repaint();
    }

    /**
     * center
     */
    private void center()
    {
        Dimension size = getSize();
        Point center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        int nX = center.x - (size.width / 2);
        int nY = center.y - (size.height / 2);
        setLocation(nX, nY);
    }
}
