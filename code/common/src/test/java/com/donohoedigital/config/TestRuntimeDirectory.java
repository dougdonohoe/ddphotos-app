package com.donohoedigital.config;

import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Oct 30, 2008
 * Time: 8:11:38 AM
 * To change this template use File | Settings | File Templates.
 */
public class TestRuntimeDirectory implements RuntimeDirectory
{
    private static final String TEMPDIR = System.getProperty("java.io.tmpdir");
    private final File client = new File(TEMPDIR, "/" + ConfigUtils.SKIP_LOGGING_PATH + "/client-testing-dir");

    public File getClientHome(String appName)
    {
        return client;
    }
}