/*
 * ImageDef.java
 *
 * Created on October 11, 2002, 6:02 PM
 */

package com.donohoedigital.config;

import com.donohoedigital.base.*;
import org.apache.logging.log4j.*;

import javax.imageio.*;
import javax.swing.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

/**
 *
 * @author  donohoe
 */
public class ImageDef 
{
    static Logger logger = LogManager.getLogger(ImageDef.class);

    private static final boolean DEBUG = false;

    private final String sName_;
    private final URL url_;
    private ImageIcon icon_;
    private BufferedImage bimage_;
    private final boolean bCache_;
    private final boolean bComposite_;
    private String[] saComponents_;
    private final int x_;
    private final int y_;

    /**
     * New image definition from name and its file
     */
    public ImageDef(String sName, URL url, Integer x, Integer y, Boolean bCache, boolean bComposite, String sComponents)
    {
        sName_ = sName;
        url_ = url;
        x_ = (x == null ? 0 : x);
        y_ = (y == null ? 0 : y);
        bCache_ = (bCache == null || bCache);
        bComposite_ = bComposite;
        if (sComponents != null)
        {
            List<String> a = new ArrayList<>();
            StringTokenizer tok = new StringTokenizer(sComponents, " ,");
            while (tok.hasMoreTokens())
            {
                a.add(tok.nextToken());
            }

            saComponents_ = new String[a.size()];
            for (int i = 0; i < saComponents_.length; i++)
            {
                saComponents_[i] = a.get(i);
            }
        }
    }
    
    /**
     * get file that this image resides in
     */
    public URL getImageURL()
    {
        return url_;
    }
    
    /**
     * get name of this image
     */
    public String getName()
    {
        return sName_;
    }
    
    /**
     * Get x hot spot for cursor
     */
    public int getX()
    {
        return x_;
    }
    
    /**
     * Get y hot spot for cursor
     */
    public int getY()
    {
        return y_;
    }

    /**
     * is Composite?
     */
    public boolean isComposite()
    {
        return bComposite_;
    }

    /**
     * get composite list
     */
    public String[] getComponents()
    {
        return saComponents_;
    }

    /**
     * Get image icon (wrapped around buffered image)
     */
    public ImageIcon getImageIcon()
    {
        if (icon_ == null)
        {
            BufferedImage bimage = getBufferedImage();
            if (bimage != null)
            {
                ImageIcon icon = new ImageIcon(bimage);
                if (!bCache_)
                {
                    return icon;
                }
                icon_ = icon;
            }
        }
        
        return icon_;
    }
    
    /**
     * Get buffered image for this image
     */
    public BufferedImage getBufferedImage()
    {
        if (bimage_ == null)
        {
            BufferedImage bimage = getBufferedImage(url_, bCache_);
            if (!bCache_)
            {
                return bimage;
            }
            bimage_ = bimage;
        }
        return bimage_;
    }


    /**
     * Get buffered image from a file
     */
    public static BufferedImage getBufferedImage(File file)
    {
        URL url;
        try
        {
            url = file.toURI().toURL();
        }
        catch (Exception e)
        {
            throw new ApplicationError(e);
        }
        return getBufferedImage(url, false);
    }

    /**
     * Get buffered image from a url
     */
    public static BufferedImage getBufferedImage(URL url)
    {
        return getBufferedImage(url, false);
    }

    /**
     * internal code - pass whether image is cached, for debugging
     */
    private static BufferedImage getBufferedImage(URL url, boolean bCache)
    {
        try {
            BufferedImage src = ImageIO.read(url);
            if (DEBUG) logSize(url.toString(), src, bCache);
            return src;
        }
        catch (Throwable e)
        {
            logger.error("Error creating buffered image from {}", url);
            logger.error(Utils.formatExceptionText(e));
        }
        return null;
    }

    /**
     * get new buffered image
     */
    public static BufferedImage createBufferedImage(int w, int h, int type)
    {
        BufferedImage buf = new BufferedImage(w, h, type);
        if (DEBUG) logSize(" **NEW** "+w+"x"+h+" image", buf, false);
        return buf;
    }

    /**
     * get new buffered image
     */
    public static BufferedImage createBufferedImage(int w, int h, int type, IndexColorModel icm)
    {
        BufferedImage buf = new BufferedImage(w, h, type, icm);
        if (DEBUG) logSize(" **NEW ICM*** "+w+"x"+h+" image", buf, false);
        return buf;
    }

    private static long totalSize_ = 0;
    private static long totalSizeCached_ = 0;
    /**
     * Log size for debugging
     */
    private static  void logSize(String sName, BufferedImage src, boolean bCache)
    {
        long size = getImageSize(src);
        totalSize_ += size;
        if (bCache) totalSizeCached_ += size;
        logger.debug(PropertyConfig.getMessage("msg.imagesize.debug",
                                               sName,
                                               size,
                                               totalSizeCached_,
                                               totalSize_,
                                               bCache ? " (cached)" : " (not cached)"));
    }

    /**
     * Return memory used by image
     */
    public static long getImageSize(BufferedImage image)
    {
        DataBuffer db = image.getRaster().getDataBuffer();
        int dataType = db.getDataType();
        int elementSizeInBits = DataBuffer.getDataTypeSize(dataType);
        return (long) db.getNumBanks() * db.getSize() * elementSizeInBits / 8;
    }
}
    
