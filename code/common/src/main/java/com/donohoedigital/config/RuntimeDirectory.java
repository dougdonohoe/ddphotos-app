package com.donohoedigital.config;

import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Dec 14, 2008
 * Time: 2:43:49 PM
 * To change this template use File | Settings | File Templates.
 */
public interface RuntimeDirectory
{
    File getClientHome(String appName);
}
