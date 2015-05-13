package com.strikew.rmi;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 各种工具类函数
 *
 * @author siyuan
 * @date 2015-04-12 2:42 PM
 */
public class Utils {
    private static final Log LOGGER = LogFactory.getLog(Utils.class);

    /**
     * @throws Error                If <code>c</code> does not represent a remote interface -
     *                              an interface whose methods are all marked as throwing
     *                              <code>RMIException</code>.
     * @throws NullPointerException If <code>c</code> is null
     */
    public static void verifyInterface(Class c) {

        // check if c is a remote interface; if each methods throw RMIException
        Method[] methods = c.getMethods();
        for (Method method : methods) {
            Class<?>[] exceptions = method.getExceptionTypes();
            Set<Class<?>> exceptionSet = new HashSet<>();
            Collections.addAll(exceptionSet, exceptions);
            if (!exceptionSet.contains(RMIException.class)) {
                throw new Error("Not a remote interface");
            }
        }
    }

    public static void closeResouce(Closeable stream) {
        try {
            if (null != stream) {
                stream.close();
            }
        } catch (IOException e) {
            LOGGER.error("Close stream fail:", e);
        }
    }
}
