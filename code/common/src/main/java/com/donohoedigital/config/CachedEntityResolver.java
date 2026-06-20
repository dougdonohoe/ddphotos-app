package com.donohoedigital.config;

import org.apache.logging.log4j.*;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.xml.sax.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 9, 2008
 * Time: 3:30:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class CachedEntityResolver implements EntityResolver, XMLEntityResolver
{
    private static Logger logger = LogManager.getLogger(XMLConfigFileLoader.class);

    private Map<String, URL> matches = new HashMap<String, URL>();

    private static CachedEntityResolver resolver = null;

    public synchronized static CachedEntityResolver instance()
    {
        if (resolver == null)
        {
            resolver = new CachedEntityResolver();
        }
        return resolver;
    }

    private CachedEntityResolver()
    {
    }

    public URL getMatch(String name) throws MalformedURLException
    {
        URL url = matches.get(name);
        if (url == null)
        {
            // if it starts with file:, we've already resolved it
            if (name.startsWith("file:"))
            {
                url = new URL(name);
            }
            else if (name.startsWith("classpath:"))
            {
                //logger.debug("Resolving XML include: " + name);
                String search = name.replace("classpath:", "classpath*:"); // * not allowed in XML file
                url = new MatchingResources(search).getSingleRequiredResourceURL();
            }
            if (url != null) matches.put(name, url);
        }
        return url;
    }

    /**
     * Resolve include statements
     */
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException
    {
        URL url = getMatch(systemId);
        if (url == null) return null;
        return new InputSource(url.openStream());
    }

    /**
     * Resolve includes
     */
    public XMLInputSource resolveEntity(XMLResourceIdentifier xmlResourceIdentifier) throws XNIException, IOException
    {
        String name = xmlResourceIdentifier.getLiteralSystemId();
        URL url = getMatch(name);
        if (url == null) return null;
        return new XMLInputSource(name, url.toString(), null);
    }
}
