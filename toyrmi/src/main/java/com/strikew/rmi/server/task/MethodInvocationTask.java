package com.strikew.rmi.server.task;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * Represents a RMI task
 */
public class MethodInvocationTask implements Callable<Object> {
    /**
     * Service implementation instance
     */
    private Object instance;

    /**
     * The remote method client want to call
     */
    private Method method;

    /**
     * Arguments for the remote method
     */
    private Object[] args;

    public MethodInvocationTask(Object instance, Method method, Object[] args) {
        this.instance = instance;
        this.method = method;
        this.args = args;
    }

    @Override
    public Object call() throws InvocationTargetException, IllegalAccessException {
        return method.invoke(instance, args);
    }
}
