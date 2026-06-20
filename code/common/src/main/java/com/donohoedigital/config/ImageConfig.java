/*
 * ImageConfig.java
 *
 * Created on October 11, 2002, 6:02 PM
 */

package com.donohoedigital.config;

import com.donohoedigital.base.*;
import org.apache.logging.log4j.*;
import org.jdom2.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;
import java.util.List;
import java.net.*;

/**
 * Loads image.xml files in the module directories defined by
 * the appconfig.xml file.
 *
 * @author  donohoe
 */
public class ImageConfig extends XMLConfigFileLoader
{
    private static final Logger iLogger = LogManager.getLogger(ImageConfig.class);
    
    private static final String IMAGE_CONFIG = "images.xml";

    private static ImageConfig imageConfig = null;
    
    private final Map<String, ImageDef> images_ = new HashMap<>();
    
    /** 
     * Creates a new instance of ImageConfig from the Appconfig file 
     */
    @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
    public ImageConfig(String[] modules) throws ApplicationError
    {
        ApplicationError.warnNotNull(imageConfig, "ImageConfig already initialized");
        imageConfig = this;
        init(modules);
    }
    
    /**
     * get image def
     */
    public static ImageDef getImageDef(String sName)
    {
        ApplicationError.assertNotNull(imageConfig, "ImageConfig not initialized");

        return imageConfig.images_.get(sName);
    }
    
    /**
     * Return ImageIcon file for requested image
     */
    public static ImageIcon getImageIcon(String sName)
    {
        ImageDef image = getImageDef(sName);
        if (image == null) 
        {
            iLogger.warn("No image found for {}", sName);
            return null;
        }
        return image.getImageIcon();
    }

    /**
     * Return ImageIcon file for requested image
     */
    public static ImageIcon getImageIcon(String sName, ImageIcon iconDefault)
    {
        ImageDef image = getImageDef(sName);
        if (image == null)
        {
            return iconDefault;
        }
        return image.getImageIcon();
    }

    /**
     * Return BufferedImage file for requested image
     */
    public static BufferedImage getBufferedImageMatchingFile(String sFileLookup)
    {
        if (sFileLookup == null) return null;

        // get file name
        int nLast = sFileLookup.lastIndexOf('/');
        if (nLast != -1)
        {
            sFileLookup = sFileLookup.substring(nLast + 1);
        }

        // look for match
        ApplicationError.assertNotNull(imageConfig, "ImageConfig not initialized");
        Map<String, ImageDef> images = imageConfig.images_;
        Iterator<String> iter = images.keySet().iterator();
        String sName;
        ImageDef config;
        while (iter.hasNext())
        {
            sName = iter.next();
            config = images.get(sName);
            if (!config.isComposite() && config.getImageURL().toString().endsWith(sFileLookup))
            {
                return getBufferedImage(sName, true);
            }
        }
        return null;
    }
    
    /**
     * Return BufferedImage file for requested image
     */
    public static BufferedImage getBufferedImage(String sName)
    {
        return getBufferedImage(sName, true);
    }
    
    /**
     * Return BufferedImage file for request image.  If
     * bReportMissing is true, log message if image not found
     */
    public static BufferedImage getBufferedImage(String sName, boolean bReportMissing)
    {
        ImageDef image = getImageDef(sName);
        if (image == null) 
        {
            if (bReportMissing && !sName.contains("default"))
            {
                iLogger.warn("No image found for {}", sName);
            }
            return null;
        }
        return image.getBufferedImage();
    }
    
    /**
     * Load images from modules
     */
    private void init(String[] modules) throws ApplicationError
    {
        ApplicationError.assertNotNull(modules, "Modules list is null");

        Document doc;
        for (String module : modules)
        {
            // if image file is missing, no big deal
            URL url = new MatchingResources("classpath*:config/" + module + "/" + IMAGE_CONFIG).getSingleResourceURL();
            if (url != null)
            {
                doc = this.loadXMLUrl(url, "images.xsd");
                init(doc, module);
            }
        }
    }
    
    /**
     * Initialize from JDOM doc
     */
    private void init(Document doc, String module) throws ApplicationError
    {
        Element root = doc.getRootElement();
        
        // imagedir name
        String imagedir = getChildStringValueTrimmed(root, "imagedir", ns_, true, IMAGE_CONFIG);
        
        // get list of images
        List<Element> images = getChildren(root, "image", ns_, false, IMAGE_CONFIG);
        if (images == null) return;

        // create ImageDef for each one
        for (Element image : images)
        {
            initImage(image, module, imagedir);
        }
    }
    
    /**
     * Read image info
     */
    private void initImage(Element image, String module, String imagedir) throws ApplicationError
    {
        String sName = getStringAttributeValue(image, "name", true, IMAGE_CONFIG);
        String sLocation = getStringAttributeValue(image, "location", true, IMAGE_CONFIG);
        Integer x = getIntegerAttributeValue(image, "x", false, IMAGE_CONFIG, null);
        Integer y = getIntegerAttributeValue(image, "y", false, IMAGE_CONFIG, null);
        Boolean cache = getBooleanAttributeValue(image, "cache", false, IMAGE_CONFIG, null);
        String sComponents = getStringAttributeValue(image, "components", false, IMAGE_CONFIG, null);
        boolean bComposite = sLocation.equals("COMPOSITE");

        URL url = null;
        if (!bComposite)
        {
            String location = module + "/" + imagedir + "/" + sLocation;
            url = new MatchingResources("classpath*:config/" + location).getSingleResourceURL();
            if (url == null)
            {
                iLogger.warn("Image {} not found at {}.  Skipping", sName, location);
                return;
            }
        }

        images_.put(sName, new ImageDef(sName, url, x, y, cache, bComposite, bComposite ? sComponents : null));
    }
    
}
