/*
 * HelpTopic.java
 *
 * Created on August 05, 2003, 6:26 PM
 */

package com.donohoedigital.config;

import java.awt.*;
import java.net.*;

/**
 *
 * @author  donohoe
 */
public class HelpTopic
{
    //static Logger logger = LogManager.getLogger(HelpTopic.class);
    
    String sName_;
    String sDisplay_;
    URL url_;

    /**
     * New help definition from name and its file
     */
    public HelpTopic(String sName, String sDisplay, URL url, int nIndent)
    {
        sName_ = sName;
        url_ = url;
        StringBuilder sb = new StringBuilder();
        sb.append("<HTML>");
        if (nIndent == 0)
        {
            sb.append("<B>");
        }
        sb.append("&nbsp;&nbsp;&nbsp;&nbsp;".repeat(Math.max(0, nIndent)));
        sb.append(sDisplay);
        sDisplay_ = sb.toString();
    }
    
    /**
     * get file that this help resides in
     */
    public URL getHelpFile()
    {
        return url_;
    }
    
    /**
     * get name of this help
     */
    public String getName()
    {
        return sName_;
    }
    
    /**
     * get display name of this help topic
     */
    public String getDisplay()
    {
        return sDisplay_;
    }
    
    /**
     * Get contents
     */
    public String getContents()
    {
        return ConfigUtils.readURL(url_);
    }

    // seed value so 1st display is correct
    private Rectangle rect_ = new Rectangle(0,0,50,50);
    
    /**
     * set scroll pos (runtime only)
     */
    public void setScrollPosition(Rectangle rect)
    {
        rect_ = rect;
    }
    
    /**
     * Get scroll pos (runtime only)
     */
    public Rectangle getScrollPosition()
    {
        return rect_;
    }
}
    
