package com.donohoedigital.base;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils
{
    public static final boolean ISMAC;
    public static final boolean ISLINUX;
    public static final boolean ISWINDOWS;
    public static final String OS;

    /**
     * Charset name we use
     */
    public static final String CHARSET_NAME = "UTF-8";

    /**
     * Charset we use
     */
    public static final Charset CHARSET = Charset.forName(CHARSET_NAME);

    /**
     * Figure out if this a Mac/Windows/linux
     */
    static
    {
        boolean ismac = false;
        boolean islinux = false;
        boolean iswindows = false;

        Properties props = System.getProperties();
        props.setProperty("file.encoding", CHARSET_NAME);

        // java/os version info
        String osVersion = (String) props.get("os.version");
        String os = (String) props.get("os.name");
        if (os == null) os = "";
        OS = os + " " + osVersion;
        os = os.toLowerCase();

        //System.out.println("OS: " + os + " version: " + sVersion);

        // mac
        if (isMacOS(os))
        {
            ismac = true;
        }
        // linux
        else if (isLinux(os))
        {
            islinux = true;
            // BUG 360 - use dns for host lookup
            props.setProperty("sun.net.spi.nameservice.provider.1", "dns,sun");
        }
        // windows
        else if (isWindows(os))
        {
            iswindows = true;
        }

        ISMAC = ismac;
        ISLINUX = islinux;
        ISWINDOWS = iswindows;
    }

    public static boolean isWindows(String os)
    {
        return os.toLowerCase().startsWith("windows");
    }

    public static boolean isLinux(String os)
    {
        return os.toLowerCase().startsWith("linux");
    }

    public static boolean isMacOS(String os)
    {
        return os.toLowerCase().startsWith("mac");
    }

    public static String getAllStacktraces()
    {
        StringBuilder sb = new StringBuilder();
        Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();

        // sort map
        Map<Thread, StackTraceElement[]> smap = new TreeMap<>(TC);
        smap.putAll(map);
        Iterator<Thread> iter = smap.keySet().iterator();
        Thread t;
        Object[] stackitems;
        while (iter.hasNext())
        {
            t = iter.next();
            //if (!t.getName().startsWith("Thread")) continue; // (testing)
            stackitems = smap.get(t);
            sb.append("Thread: ").append(t.getName()).append(" [").append(t.getClass().getName()).append("] daemon: ").append(t.isDaemon()).append('\n');
            for (Object stackitem : stackitems)
            {
                sb.append("   at ").append(stackitem).append('\n');
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private static final ThreadComparator TC = new ThreadComparator();

    private static class ThreadComparator implements Comparator<Thread>
    {
        public int compare(Thread t1, Thread t2)
        {
            return t1.getName().compareTo(t2.getName());
        }
    }

    /**
     * Return string showing exception message and stack trace
     * for output to regular text
     */
    public static String formatExceptionText(Throwable e)
    {
        if (e == null) return "null";
        ByteArrayOutputStream ostr = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(ostr));
        //return "Exception: " + e.toString() + "\n" + ostr.toString();
        return ostr.toString(); // this includes the message
    }


    /**
     * Get #FF00FF style hex string representation of this color
     */
    public static String getHtmlColor(Color c)
    {
        StringBuilder sb = new StringBuilder(7);
        sb.append('#');
        appendColorPart(c.getRed(), sb);
        appendColorPart(c.getGreen(), sb);
        appendColorPart(c.getBlue(), sb);
        return sb.toString();
    }

    /**
     * used by getHtmlColor - appends a 2 digit hex value
     * representation of c to the given string buffer
     */
    private static void appendColorPart(int c, StringBuilder sb)
    {
        String s = Integer.toHexString(c);
        if (s.length() == 1)
        {
            sb.append('0');
        }
        sb.append(s);
    }

    /**
     * Convenience function for sleeping - any exceptions are caught/ignored
     */
    public static void sleepMillis(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException ignored)
        {
            //noinspection ResultOfMethodCallIgnored
            Thread.interrupted();
        }
    }

    /**
     * Convenience function for sleeping - any exceptions are caught/ignored.
     * Argument 'seconds' is a double to allow passing of fractional seconds like ".5"
     */
    public static void sleepSeconds(double seconds)
    {
        sleepMillis((long) (seconds * 1000));
    }

    /**
     * convert string to boolean.  returns null if not a valid value (-/0/false/no, +/1/true/yes)
     */
    public static Boolean parseBoolean(String sValue)
    {
        if (sValue == null) return null;

        Boolean value = null;
        if (sValue.length() == 1)
        {
            if (sValue.charAt(0) == '0') value = Boolean.FALSE;
            if (sValue.charAt(0) == '-') value = Boolean.FALSE;
            if (sValue.charAt(0) == '1') value = Boolean.TRUE;
            if (sValue.charAt(0) == '+') value = Boolean.TRUE;
        }
        else
        {
            if (sValue.equalsIgnoreCase("false")) value = Boolean.FALSE;
            if (sValue.equalsIgnoreCase("true")) value = Boolean.TRUE;
            if (sValue.equalsIgnoreCase("no")) value = Boolean.FALSE;
            if (sValue.equalsIgnoreCase("yes")) value = Boolean.TRUE;
        }

        return value;
    }

    /**
     * replace given string
     */
    public static String replace(String sSrc, String sPattern, String sReplace)
    {
        Pattern pattern = Pattern.compile(sPattern);
        Matcher matcher = pattern.matcher(sSrc);
        return matcher.replaceAll(sReplace);
    }

    /**
     * Return new decoder for our charset
     */
    public static CharsetDecoder newDecoder()
    {
        CharsetDecoder decoder = CHARSET.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        return decoder;
    }

    /**
     * Open a URL in the OS default browser.
     */
    public static void openURL(String sURL)
    {
        // Prefer java.awt.Desktop — works on macOS, Windows, and modern Linux with a DE
        if (Desktop.isDesktopSupported())
        {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE))
            {
                try
                {
                    desktop.browse(new URI(sURL));
                    return;
                }
                catch (Exception e)
                {
                    System.err.println("openURL Desktop.browse error: " + formatExceptionText(e));
                }
            }
        }

        // Platform-specific fallbacks for headless or unsupported Desktop environments
        try
        {
            if (ISMAC)
            {
                new ProcessBuilder("/usr/bin/open", sURL).start();
            }
            else if (ISLINUX)
            {
                // xdg-open is the modern cross-DE standard (replaces gnome-open)
                new ProcessBuilder("xdg-open", sURL).start();
            }
        }
        catch (Exception e)
        {
            System.err.println("openURL error: " + formatExceptionText(e));
        }
    }

    /**
     * Open a folder (or reveal a file) in the OS default file manager.
     * Returns true if a file manager was successfully launched.
     */
    public static boolean openFolder(File folder)
    {
        // Prefer java.awt.Desktop — works on macOS, Windows, and modern Linux with a DE
        if (Desktop.isDesktopSupported())
        {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.OPEN))
            {
                try
                {
                    desktop.open(folder);
                    return true;
                }
                catch (Exception e)
                {
                    System.err.println("openFolder Desktop.open error: " + formatExceptionText(e));
                }
            }
        }

        // Platform-specific fallbacks for headless or unsupported Desktop environments
        try
        {
            if (ISMAC)
            {
                new ProcessBuilder("/usr/bin/open", folder.getAbsolutePath()).start();
                return true;
            }
            else if (ISWINDOWS)
            {
                new ProcessBuilder("explorer", folder.getAbsolutePath()).start();
                return true;
            }
            else if (ISLINUX)
            {
                new ProcessBuilder("xdg-open", folder.getAbsolutePath()).start();
                return true;
            }
        }
        catch (Exception e)
        {
            System.err.println("openFolder error: " + formatExceptionText(e));
        }

        return false;
    }

    public static SimpleDateFormat getRFC822()
    {
        return new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
    }
}
