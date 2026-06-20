package com.donohoedigital.config;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Oct 27, 2008
 * Time: 8:47:33 AM
 * To change this template use File | Settings | File Templates.
 */
public interface ShutdownListener
{
    void shutdown(ShutdownManager.Type type, String details);
}
