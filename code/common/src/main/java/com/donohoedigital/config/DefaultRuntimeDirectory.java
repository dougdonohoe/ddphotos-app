package com.donohoedigital.config;

import com.donohoedigital.base.*;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Dec 14, 2008
 * Time: 2:44:35 PM
 * To change this template use File | Settings | File Templates.
 */
public class DefaultRuntimeDirectory implements RuntimeDirectory {
    public File getClientHome(String appName) {
        ApplicationError.assertNotNull(appName, "appName is null");
        Properties props = System.getProperties();
        String sUserDir = (String) props.get("user.home");
        return new File(sUserDir + File.separatorChar + ".config" + File.separatorChar + appName);
    }
}
