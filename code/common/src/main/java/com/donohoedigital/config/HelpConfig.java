/*
 * HelpConfig.java
 *
 * Created on August 05, 2003, 6:26 PM
 */

package com.donohoedigital.config;

import com.donohoedigital.base.*;
import org.apache.logging.log4j.*;
import org.jdom2.*;

import java.net.*;
import java.util.*;

/**
 * Loads help.xml files in the module directories defined by
 * the appconfig.xml file.
 *
 * @author  donohoe
 */
public class HelpConfig extends XMLConfigFileLoader
{
    private static final Logger hLogger = LogManager.getLogger(HelpConfig.class);
    
    private String HELP_CONFIG = "help.xml";

    private static HelpConfig helpConfig = null;
    
    private final Map<String, HelpTopic> helps_ = new HashMap<>();
    private final List<HelpTopic> helparray_ = new ArrayList<>();
    
    /** 
     * Creates a new instance of HelpConfig from the Appconfig file 
     */
    @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
    public HelpConfig(String[] modules, String sLocale) throws ApplicationError
    {
        ApplicationError.warnNotNull(helpConfig, "HelpConfig is already initialized");
        helpConfig = this;

        if (sLocale != null)
        {
            HELP_CONFIG = "help-"+sLocale+".xml";
        }
        init(modules);
    }
    
    /**
     * Get help topic list
     */
    public static List<HelpTopic> getHelpTopics()
    {
        ApplicationError.assertNotNull(helpConfig, "HelpConfig not initialized");
        return helpConfig.helparray_;
    }
    
    /**
     * Get help topic by name
     */
    public static HelpTopic getHelpTopic(String sName)
    {
        ApplicationError.assertNotNull(helpConfig, "HelpConfig not initialized");
        return helpConfig.helps_.get(sName);
    }
    
    /**
     * Load helps from modules
     */
    private void init(String[] modules) throws ApplicationError
    {
        ApplicationError.assertNotNull(modules, "Modules list is null");
        
        Document doc;
        for (String module : modules)
        {
            // if help file is missing, no big deal
            URL url = new MatchingResources("classpath*:config/" + module + "/" + HELP_CONFIG).getSingleResourceURL();
            if (url != null)
            {
                doc = this.loadXMLUrl(url, "help.xsd");
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
        
        // helpdir name
        String helpDir = getChildStringValueTrimmed(root, "helpdir", ns_, true, HELP_CONFIG);
        
        // get list of helps
        List<Element> helps = getChildren(root, "help", ns_, false, HELP_CONFIG);
        if (helps == null) return;
        
        // create HelpTopic for each one
        for (Element help : helps)
        {
            initHelp(help, module, helpDir);
        }
    }
    
    /**
     * Read help info
     */
    private void initHelp(Element help, String module, String helpdir) throws ApplicationError
    {
        String sName = getStringAttributeValue(help, "name", true, HELP_CONFIG);
        String sLocation = getStringAttributeValue(help, "location", true, HELP_CONFIG);
        String sDisplay = getStringAttributeValue(help, "display", true, HELP_CONFIG);
        Integer nIndent = getIntegerAttributeValue(help, "indent", true, HELP_CONFIG);

        String location = module + "/" + helpdir + "/" + sLocation;
        URL url = new MatchingResources("classpath*:config/" + location).getSingleResourceURL();
        if (url == null)
        {
            hLogger.warn("Help {} not found at {}.  Skipping", sName, location);
            return;
        }
        
        HelpTopic topic = new HelpTopic(sName, sDisplay, url, nIndent);
        helps_.put(sName, topic);
        helparray_.add(topic);
    }
}
