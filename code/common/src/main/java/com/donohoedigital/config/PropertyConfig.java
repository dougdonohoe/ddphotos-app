/*
 * PropertyConfig.java
 *
 * Created on November 16, 2002, 6:49 PM
 */

package com.donohoedigital.config;

import com.donohoedigital.base.*;
import org.apache.logging.log4j.*;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

/**
 * @author Doug Donohoe
 */
public class PropertyConfig extends Properties
{
    private static final Logger logger = LogManager.getLogger(PropertyConfig.class);

    // config file names
    private static final String PROPS_CONFIG_COMMON = "common.properties";
    private static final String PROPS_CONFIG_CLIENT = "client.properties";
    private static final String PROPS_CONFIG_CMDLINE = "cmdline.properties";

    // the one instance
    private static PropertyConfig propConfig = null;

    // testing - don't throw missing exceptions
    private static final boolean testing = false;

    /**
     * Creates a new instance of PropertyConfig from the Appconfig file
     */
    @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
    public PropertyConfig(String appName, String[] modules, ApplicationType type, String sLocale, boolean allowOverrides)
    {
        ApplicationError.warnNotNull(propConfig, "PropertyConfig already initialized");
        propConfig = this;

        init(appName, modules, type, sLocale, allowOverrides);
    }

    /**
     * is initialized?
     */
    public static boolean isInitialized()
    {
        return propConfig != null;
    }

    /**
     * Load each .properties file from each module
     */
    private void init(String appName, String[] modules, ApplicationType type, String sLocale, boolean allowOverrides)
    {
        ApplicationError.assertNotNull(modules, "Modules list is null");

        // file only used for combining dir/name
        File file;

        for (String module : modules)
        {
            // common props
            file = new File(module, PROPS_CONFIG_COMMON);
            loadURL(file, sLocale, false);

            // props specific to client/cmdline
            String name = switch (type) {
                case CLIENT, HEADLESS_CLIENT -> PROPS_CONFIG_CLIENT;
                case COMMAND_LINE -> PROPS_CONFIG_CMDLINE;
            };
            file = new File(module, name);
            loadURL(file, sLocale, false);

            // look for user specific overrides
            if (allowOverrides)
            {
                String user = ConfigUtils.getUserName();
                file = new File(module + "/override/" + user.toLowerCase() + ".properties");
                loadURL(file, sLocale, true);
            }
        }

        // look for overrides in data dir
        if (type == ApplicationType.CLIENT)
        {
            RuntimeDirectory dir = new DefaultRuntimeDirectory();
            File userdir = dir.getClientHome(appName);
            File override = new File(userdir, "testing.properties");
            if (override.exists())
            {
                logger.info("Loading testing overrides from {}", override.getPath());
                FileInputStream stream = ConfigUtils.getFileInputStream(override);
                try
                {
                    load(stream);
                }
                catch (IOException e)
                {
                    throw new ApplicationError(e);
                }
                finally
                {
                    ConfigUtils.close(stream);
                }
            }

            // look for buildnumber.properties
            file = new File("buildnumber.properties");
            loadURL(file, null, false);
        }
    }

    private void loadURL(File file, String sLocale, boolean bOverride)
    {
        URL props = null;

        // if locale, look for file based on that locale
        if (sLocale != null)
        {
            props = new MatchingResources("classpath*:config/" + file.getPath() + '.' + sLocale).getSingleResourceURL();
        }

        // if no locale, look for regular file
        if (props == null)
        {
            props = new MatchingResources("classpath*:config/" + file.getPath()).getSingleResourceURL();
        }

        // if props file is not there, no big deal
        if (props == null) return;

        // log if doing overrides
        if (bOverride)
        {
            logger.info("Loading local overrides from {}", file.getPath());
        }

        //logger.debug("Loading: {}", props);

        InputStream is = null;
        try
        {
            is = props.openStream();
            load(is);
        }
        // since we verified, file should exist
        catch (Exception e)
        {
            throw new ApplicationError(e);
        }
        finally
        {
            ConfigUtils.close(is);
        }
    }

    public static int getRequiredIntegerProperty(String sKey)
    {
        getRequiredStringProperty(sKey);
        return getIntegerProperty(sKey, 0);
    }

    /**
     * Get property, return nDefault if not there
     */
    public static int getIntegerProperty(String sKey, int nDefault)
    {
        try
        {
            String sNum = getStringProperty(sKey, null, false);
            if (sNum == null) return nDefault;
            return Integer.parseInt(sNum);
        }
        catch (NumberFormatException ignored)
        {
            if (testing) return 0;
            String sMsg = "Error converting '" + sKey + "' value to int: " + getStringProperty(sKey);
            logger.warn(sMsg);
            throw new ApplicationError(ErrorCodes.ERROR_VALIDATION, sMsg, "Make sure value is a valid integer");
        }
    }

    public static double getRequiredDoubleProperty(String sKey)
    {
        getRequiredStringProperty(sKey);
        return getDoubleProperty(sKey, 0.0);
    }

    /**
     * Get property, return dDefault if not there
     */
    public static double getDoubleProperty(String sKey, double dDefault)
    {
        try
        {
            String sNum = getStringProperty(sKey, null, false);
            if (sNum == null) return dDefault;
            return Double.parseDouble(sNum);
        }
        catch (NumberFormatException ignored)
        {
            if (testing) return 0d;
            String sMsg = "Error converting '" + sKey + "' value to double: " + getStringProperty(sKey);
            logger.error(sMsg);
            throw new ApplicationError(ErrorCodes.ERROR_VALIDATION, sMsg, "Make sure value is a valid double");
        }
    }

    public static boolean getRequiredBooleanProperty(String sKey)
    {
        getRequiredStringProperty(sKey);
        return getBooleanProperty(sKey, false);
    }

    /**
     * Get property, return sDefault if not there or not valid boolean
     */
    public static boolean getBooleanProperty(String sKey, boolean bDefault)
    {
        return getBooleanProperty(sKey, bDefault, true);
    }

    /**
     * Get property, return sDefault if not there or not valid boolean
     */
    public static boolean getBooleanProperty(String sKey, boolean bDefault, boolean bReportMissing)
    {
        String sValue = getStringProperty(sKey, null, bReportMissing);
        if (sValue == null) return bDefault;

        Boolean bool = Utils.parseBoolean(sValue);
        if (bool == null)
        {
            if (testing) return false;
            String sMsg = "Error converting '" + sKey + "' value to boolean: " + getStringProperty(sKey);
            logger.error(sMsg);
            throw new ApplicationError(ErrorCodes.ERROR_VALIDATION, sMsg, "Make sure value is a valid boolean");
        }
        return bool;
    }

    /**
     * Get a string property that should be there, throw
     * runtime error if not
     */
    public static String getRequiredStringProperty(String sKey)
    {
        String sValue = getStringProperty(sKey);
        if (sValue == null)
        {
            logger.error("Property value not found for: '{}'", sKey);
        }
        return sValue;
    }

    /**
     * Get property, return sDefault if not there
     */
    public static String getStringProperty(String sKey, String sDefault)
    {
        String s = getStringProperty(sKey);
        if (s == null) return sDefault;
        return s;
    }

    /**
     * Get property, return sDefault if not there
     */
    public static String getStringProperty(String sKey, String sDefault, boolean bReportMissing)
    {
        String s = getStringProperty(sKey, bReportMissing);
        if (s == null) return sDefault;
        return s;
    }

    // marker used to redirect a property to another property's value: COPY=[other.key]
    private static final String COPY_PREFIX = "COPY=[";
    private static final int MAX_COPY_DEPTH = 10;

    /**
     * Get a string property
     */
    private static String getStringProperty(String sKey, boolean bReportMissing)
    {
        ApplicationError.assertNotNull(propConfig, "PropertyConfig has not been initialized");
        String sValue = propConfig.getProperty(sKey);
        if (sValue != null) sValue = sValue.trim(); // need to remove trailing spaces

        // a value of COPY=[other.key] redirects the lookup to other.key, so values can be shared
        sValue = resolveCopy(sKey, sValue, bReportMissing, 0);

        if (sValue == null && testing) return "TESTING-MISSING-" + sKey;

        if (sValue == null && bReportMissing)
        {
            if (!sKey.contains("default"))
            {
                logger.warn("Property value not found for: '{}'", sKey);
            }
        }

        return sValue;
    }

    public static String getStringProperty(String sKey)
    {
        return getStringProperty(sKey, true);
    }

    /**
     * Resolve COPY=[other.key] redirects so a property can reuse another's value.
     * Follows chains (the target may itself be a COPY) and guards against cycles.
     */
    private static String resolveCopy(String sKey, String sValue, boolean bReportMissing, int depth)
    {
        if (sValue == null || !sValue.startsWith(COPY_PREFIX) || !sValue.endsWith("]"))
        {
            return sValue;
        }

        if (depth >= MAX_COPY_DEPTH)
        {
            logger.warn("COPY redirect too deep (possible cycle) starting at: '{}'", sKey);
            return sValue;
        }

        String target = sValue.substring(COPY_PREFIX.length(), sValue.length() - 1).trim();
        String resolved = propConfig.getProperty(target);
        if (resolved != null) resolved = resolved.trim();

        if (resolved == null && bReportMissing)
        {
            logger.warn("COPY target not found: '{}' (referenced by '{}')", target, sKey);
        }

        return resolveCopy(target, resolved, bReportMissing, depth + 1);
    }

    // cache formats
    private static final Map<String, MessageFormat> formats_ = new HashMap<>();

    /**
     * Get a message and insert the params into it (params replaced
     * where strings of the form {#} are found).
     */
    public static String getMessage(String sKey, Object... oParams)
    {
        String sMsg = null;
        String sFormatThis = getRequiredStringProperty(sKey);
        if (sFormatThis != null)
        {
            sMsg = sFormatThis;
            if (oParams != null && oParams.length > 0)
            {
                // cache long ones since they seem to take longer to parse
                if (oParams.length > 4)
                {
                    synchronized (formats_)
                    {
                        MessageFormat format = formats_.get(sKey);
                        if (format == null)
                        {
                            format = new MessageFormat(sFormatThis);
                            formats_.put(sKey, format);
                        }

                        sMsg = format.format(oParams);
                    }
                }
                else
                {
                    sMsg = MessageFormat.format(sFormatThis, oParams);
                }
            }
        }
        return sMsg;
    }

    /**
     * Get a message and insert the params into it (params replaced
     * where strings of the form {#} are found).
     */
    public static String getLocalizedMessage(String sKey, String sLocale, Object... oParams)
    {
        // localize key
        String keyLocalized = localize(sKey, sLocale);

        // get message
        String message = getMessage(keyLocalized, oParams);

        // if no message, see if default value is there (only if key was localized)
        if (message == null && !sKey.equals(keyLocalized))
        {
            message = getMessage(sKey, oParams);
        }
        return message;
    }

    /**
     * Return localized version of message key
     */
    public static String localize(String sKey, String sLocale)
    {
        if (sLocale == null) return sKey;
        return sLocale + '.' + sKey;
    }

    // store locales
    private static final Map<String, Locale> locales_ = new HashMap<>();

    /**
     * Get java locale for string
     */
    public static Locale getLocale(String sLocale)
    {
        if (sLocale != null)
        {
            synchronized (locales_)
            {
                Locale locale = locales_.get(sLocale);
                if (locale == null)
                {
                    locale = new Locale(sLocale, "", "");
                    locales_.put(sLocale, locale);
                }
                return locale;
            }
        }
        else
        {
            return Locale.US;
        }
    }

    /**
     * Get build number
     */
    public static String getBuildNumber()
    {
        return getStringProperty("build.number", "[dev]", false);
    }
}
