package com.donohoedigital.config;

import org.apache.logging.log4j.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Class to get matching resources from the classpath.
 * <p/>
 * Patterns are an exact resource path, optionally prefixed with {@code classpath:} or
 * {@code classpath*:} (both are treated the same - all matching resources on the classpath
 * are returned).  Ant-style wildcards are not supported.
 *
 * @author Doug Donohoe
 */
public class MatchingResources
{
    private final URL[] resources;
    private final String pattern;

    /**
     * Initialize list of matching resources by searching the classpath via
     * {@link ClassLoader#getResources(String)}.
     *
     * @param sPattern the pattern to search for
     */
    public MatchingResources(String sPattern)
    {
        pattern = sPattern.replace('\\', '/'); // fix DOS paths

        // strip classpath prefix - we always search all classpath resources
        String path = pattern;
        if (path.startsWith("classpath*:")) path = path.substring("classpath*:".length());
        else if (path.startsWith("classpath:")) path = path.substring("classpath:".length());
        if (path.startsWith("/")) path = path.substring(1);

        try
        {
            Enumeration<URL> urls = getClassLoader().getResources(path);
            List<URL> list = new ArrayList<>();
            while (urls.hasMoreElements())
            {
                list.add(urls.nextElement());
            }
            resources = list.toArray(new URL[0]);

            // get on demand since used before LoggingConfig is run
            Logger logger = LogManager.getLogger(MatchingResources.class);
            logger.debug("Found {} resource(s) for: {}", resources.length, pattern);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the classloader used to search for resources.
     */
    private static ClassLoader getClassLoader()
    {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = MatchingResources.class.getClassLoader();
        return cl;
    }

    /**
     * Get all matching resources as URLs.
     *
     * @return {@link URL} array of all matches.  If no matches are found this is a zero-length array.
     */
    public URL[] getAllMatchesURL()
    {
        return resources;
    }

    /**
     * Get a single matching resource as a {@link URL}.  Throws an exception if multiple are found.  This is
     * useful if you are expecting to find only one instance of an item on the classpath.
     *
     * @return The single matching {@link URL}, or null if none found
     * @throws RuntimeException if more than one resource was found
     */
    public URL getSingleResourceURL()
    {
        if (resources.length > 1)
        {
            throw new RuntimeException("Found more than one resource in classpath for " + pattern + ": " + this);
        }

        if (resources.length == 0) return null;
        return resources[0];
    }

    /**
     * Get a single required matching resource as a {@link URL}.  Throws an exception if zero or multiple are
     * found.  This is useful if you are expecting to find one and only one instance of an item on the classpath.
     *
     * @return The single matching {@link URL}
     * @throws RuntimeException if zero or more than one resource was found
     */
    public URL getSingleRequiredResourceURL()
    {
        if (resources.length == 0)
        {
            throw new RuntimeException("Could not find required resource for " + pattern);
        }

        if (resources.length > 1)
        {
            throw new RuntimeException("Found more than one resource in classpath for " + pattern + ": " + this);
        }

        return resources[0];
    }

    /**
     * @return string representing all matching resources as URLs
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (URL url : resources)
        {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(url.toString());
        }
        return sb.toString();
    }
}
