/*
 * DDImageView.java
 *
 * Created on March 29, 2003, 3:42 PM
 */
package com.donohoedigital.gui;

import com.donohoedigital.config.*;
import org.apache.logging.log4j.*;

import javax.swing.text.*;
import javax.swing.text.html.*;
import java.awt.*;
import java.awt.image.*;

/**
 *
 * @author  Doug Donohoe
 */
public class DDImageView extends DDView
{
    public static final String YADJ = "yadj";

    BufferedImage image_;
    static Logger logger = LogManager.getLogger(DDImageView.class);
    int nWidth_ = 0;
    int nHeight_= 0;
    int nYadj_ = 0;

    /**
     * Creates a new instance of DDImageView 
     */    
    public DDImageView(Element elem)
    {
        super(elem);

        String sTag = elem.getAttributes().getAttribute(StyleConstants.NameAttribute).toString();

        String src = (String)getElement().getAttributes().
                             getAttribute(HTML.Attribute.SRC);
        if (sTag.equals("ddimg"))
        {
            image_ = ImageConfig.getBufferedImage(src);
        }
        else
        {
            image_ = ImageConfig.getBufferedImageMatchingFile(src);
        }

		if (image_ == null)
		{
            logger.warn("No image for src {}", src);
			return;
		}
        
        nWidth_ = image_.getWidth();
        nHeight_ = image_.getHeight();
        
        boolean nWidthChanged = false;
        boolean nHeightChanged = false;
        
        String sValue = (String) getElement().getAttributes().getAttribute(HTML.Attribute.WIDTH);
        if (sValue != null && !sValue.isEmpty())
        {
            nWidth_ = Integer.parseInt(sValue);
            nWidthChanged = true;
        }
        
        sValue = (String) getElement().getAttributes().getAttribute(HTML.Attribute.HEIGHT);
        if (sValue != null && !sValue.isEmpty())
        {
            nHeight_ = Integer.parseInt(sValue);
            nHeightChanged = true;
        }
        
        if (nWidthChanged && !nHeightChanged)
        {
            double nRatio = (double) image_.getWidth() / (double) nWidth_;
            nHeight_ = (int) (nHeight_ / nRatio); 
        }
        
        if (!nWidthChanged && nHeightChanged)
        {
            double nRatio = (double) image_.getHeight() / (double) nHeight_;
            nWidth_ = (int) (nWidth_ / nRatio); 
        }

        sValue = (String) getElement().getAttributes().getAttribute(YADJ);
        if (sValue != null && !sValue.isEmpty())
        {
            nYadj_ = Integer.parseInt(sValue);
        }
    }
  
    /**
     * paint
     */
    public void paint(Graphics g, Shape a) 
    {
		if (image_ == null) return;

        Rectangle rect = (a instanceof Rectangle) ? (Rectangle)a :
                         a.getBounds();

        g.drawImage(image_, rect.x, rect.y+nYadj_, rect.x+nWidth_, rect.y+nHeight_+nYadj_,
                                0, 0, image_.getWidth(), image_.getHeight(), null);
    }
    
    /** Determines the preferred span for this view along an
     * axis.
     *
     * @param axis may be either <code>View.X_AXIS</code> or
     * 		<code>View.Y_AXIS</code>
     * @return   the span the view would like to be rendered into.
     *           Typically, the view is told to render into the span
     *           that is returned, although there is no guarantee.
     *           The parent may choose to resize or break the view
     * @see View#getPreferredSpan
     *
     */
    public float getPreferredSpan(int axis) {
        if (axis == View.X_AXIS) return nWidth_;
        return nHeight_;
    }
}
