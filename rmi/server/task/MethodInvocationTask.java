package rmi.server.task;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * Represents a RMI task
 */
public class MethodInvocationTask implements Callable<Object> {
    /**
     * Service implementaino instance
     */
    private Object instance;
    private Method method;
    private Object[] args;

    public MethodInvocationTask(Object instance, Method method, Object[] args) {
        this.instance = instance;
        this.method = method;
        this.args = args;
    }

    @Override
    public Object call() throws Exception {
        return method.invoke(instance, args);
    }
}
