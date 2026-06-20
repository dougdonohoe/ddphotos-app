/*
 * ConfigUtils.java
 *
 * Created on February 10, 2003, 9:16 AM
 */

package com.donohoedigital.config;

import com.donohoedigital.base.ApplicationError;
import com.donohoedigital.base.ErrorCodes;
import com.donohoedigital.base.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetDecoder;
import java.util.Objects;
import java.util.StringTokenizer;

/**
 * @author Doug Donohoe
 */
public class ConfigUtils
{
    static Logger logger = LogManager.getLogger(ConfigUtils.class);

    // allow resetting logger (needed since this is created before LoggingConfig runs)
    static void resetLogger() {
        logger = LogManager.getLogger(ConfigUtils.class);
    }

    ///
    /// Convenience functions for loading classes
    ///

    /**
     * Get a class given a class name
     */
    public static Class<?> getClass(String sClass)
    {
        return getClass(sClass, true);
    }

    /**
     * Get a class given a class name.  Throw an exception if bThrowExceptionOnError true and
     * class not found.  Otherwise return null and log a warning message.
     */
    public static Class<?> getClass(String sClass, boolean throwExceptionOnError)
    {
        try
        {
            return Class.forName(sClass);
        }
        catch (ClassNotFoundException cne)
        {
            String sMsg = "Class " + sClass + " was not found";
            if (throwExceptionOnError)
            {
                throw new ApplicationError(sMsg, cne);
            }
            else
            {
                logger.warn(sMsg);
            }
        }

        return null;
    }

    /**
     * Create new instance of given class using a particular constructor
     */
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<?> cClass, Class<?>[] signature, Object[] params)
    {
        try
        {
            return (T) cClass.getConstructor(signature).newInstance(params);
        }
        catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException ie)
        {
            throw new ApplicationError(ie);
        }
    }

    /**
     * Verify directory exists, if not create it
     */
    public static void verifyNewDirectory(File dir)
    {
        if (!dir.exists())
        {
            if (isNotTestPath(dir.getAbsolutePath())) {
                logger.info("Creating directory: {}", dir.getAbsolutePath());
            }
            if (!dir.mkdirs())
            {
                //logger.error("Unable to create dir " + dir.getAbsolutePath());
                throw new ApplicationError(ErrorCodes.ERROR_CREATE, "Unable to create directory",
                                           dir.getAbsolutePath(), "Check permissions on parent directories");
            }
        }

        if (!dir.isDirectory())
        {
            //logger.error("Path is not a directory: " + dir.getAbsolutePath());
            throw new ApplicationError(ErrorCodes.ERROR_CREATE, "Path should be a directory",
                                       dir.getAbsolutePath(), "Rename existing item");
        }
    }

    /**
     * Verify dir exists
     */
    public static void verifyDirectory(File dir)
    {
        if (!dir.exists() || !dir.canRead() || !dir.isDirectory())
        {
            //logger.error("Directory doesn't exist or can't be read: " + dir.getAbsolutePath());
            throw new ApplicationError(ErrorCodes.ERROR_FILE_NOT_FOUND, "Directory should exist and be readable",
                                       dir.getAbsolutePath(), "Check permissions");
        }
    }

    /**
     * Verify file exists
     */
    public static void verifyFile(File file)
    {
        if (!file.exists() || !file.canRead() && !file.isFile())
        {
            //logger.error("File doesn't exist or can't be read: " + file.getAbsolutePath());
            throw new ApplicationError(ErrorCodes.ERROR_FILE_NOT_FOUND, "File should exist and be readable",
                                       file.getAbsolutePath(), "Check permissions");
        }
    }

    /**
     * Get reader for given file
     */
    public static Reader getReader(File file)
    {
        FileInputStream fis = getFileInputStream(file);

        FileChannel in = fis.getChannel();
        CharsetDecoder decoder = Utils.newDecoder();

        return Channels.newReader(in, decoder, -1);
    }

    /**
     * Get reader for given url
     */
    public static Reader getReader(URL url)
    {
        InputStream fis;
        try
        {
            fis = url.openStream();
        }
        catch (IOException fnfe)
        {
            throw new ApplicationError(fnfe);
        }

        return new InputStreamReader(fis);
    }

    /**
     * Get an InputStream for the given file, throws ApplicationError if problem
     */
    public static FileInputStream getFileInputStream(File file)
    {
        FileInputStream fis;
        try
        {
            fis = new FileInputStream(file);
        }
        catch (FileNotFoundException fnfe)
        {
            throw new ApplicationError(fnfe);
        }

        return fis;
    }

    /**
     * close stream, throws ApplicationError if IOException occurs
     */
    public static void close(Closeable out)
    {
        if (out == null) return;

        try
        {
            out.close();
        }
        catch (IOException ioe)
        {
            throw new ApplicationError(ioe);
        }
    }

    /**
     * Read given file and return a String.  File is read by
     * lines and each line is appended with a newline (including
     * the last line).
     */
    public static String readFile(File file)
    {
        verifyFile(file);
        Reader reader = getReader(file);
        BufferedReader sreader = new BufferedReader(reader);
        StringBuilder sb = new StringBuilder();
        String sLine;
        try
        {
            while ((sLine = sreader.readLine()) != null)
            {
                sb.append(sLine);
                sb.append('\n');
            }
            close(reader);
        }
        catch (IOException ioe)
        {
            throw new ApplicationError(ioe);
        }
        return sb.toString();
    }

    /**
     * Read given url and return a String.  File is read by
     * lines and each line is appended with a newline (including
     * the last line).
     */
    public static String readURL(URL url)
    {
        Reader reader = getReader(url);
        BufferedReader sreader = new BufferedReader(reader);
        StringBuilder sb = new StringBuilder();
        String sLine;
        try
        {
            while ((sLine = sreader.readLine()) != null)
            {
                sb.append(sLine);
                sb.append('\n');
            }
        }
        catch (IOException ioe)
        {
            throw new ApplicationError(ioe);
        }
        finally
        {
            close(reader);
        }
        return sb.toString();
    }

    /**
     * Delete a directory and all of its contents
     */
    public static boolean deleteDir(File dir)
    {
        File[] files = dir.listFiles();
        File file;

        for (int i = 0; files != null && i < files.length; i++)
        {
            file = files[i];

            if (file.isDirectory())
            {
                // recurse through subdirectory
                deleteDir(file);
            }
            else
            {
                // delete a file
                if (isNotTestPath(file.getAbsolutePath())) {
                    logger.info("Delete file {}", file.getAbsolutePath());
                }
                if (!file.delete())
                {
                    return false;
                }
            }
        }

        // delete the directory
        if (isNotTestPath(dir.getAbsolutePath())) {
            logger.info("Delete directory {}", dir.getAbsolutePath());
        }
        return dir.delete();
    }

    /**
     * Get local host name, return null if unknown
     */
    public static String getLocalHost(boolean bLogError)
    {
        String sLocalHost;

        try
        {
            InetAddress local = InetAddress.getLocalHost();
            sLocalHost = local.getHostName();

            // strip ".local" at end of Mac host name
            if (Utils.ISMAC)
            {
                sLocalHost = sLocalHost.replaceAll("\\.local$", "");
            }
        }
        catch (UnknownHostException uhe)
        {
            StringTokenizer st = new StringTokenizer(uhe.getMessage(), ":");
            if (st.hasMoreTokens())
            {
                sLocalHost = st.nextToken();
                if (bLogError) logger.warn("Unable to determine local host name, guessing it is: {}", sLocalHost);
            }
            else
            {
                if (bLogError) logger.warn("Unable to determine local host name: {}", uhe.getMessage());
                sLocalHost = null;
            }
        }

        return sLocalHost;
    }

    /**
     * Get username
     */
    public static String getUserName()
    {
        String name = System.getProperties().getProperty("user.name");
        return Objects.equals(name, "xboxl") ? "donohoe" : name; // Doug hack for his windows laptop :-)
    }

    // hack to skip certain log messages that hamper LoggingConfigTest
    public static final String SKIP_LOGGING_PATH = "skip-logging";
    private static boolean isNotTestPath(String path) {
       return !path.contains(SKIP_LOGGING_PATH);
    }
}
