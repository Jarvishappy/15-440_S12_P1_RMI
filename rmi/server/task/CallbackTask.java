package rmi.server.task;

import rmi.RMIException;
import rmi.Skeleton;
import rmi.server.callback.Callback;
import rmi.server.event.Subject;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A task with a callback, in order to get
 * the return value from task without blocking on
 * Future instance returned by ExecutorService.submit()
 *
 */
public final class CallbackTask<T> implements Runnable, Subject {
    private static final Logger LOGGER = Logger.getLogger("CallbackTask");

    /**
     * Actual task to run
     */
    protected final Callable task;

    /**
     * Callback object
     */
    protected final Callback callback;

    /**
     * The associcated skeleton instance, need to be informed if RMI request fail to serve
     */
    private Skeleton<T> skeleton;


    public CallbackTask(Skeleton<T> s, Callable task, Callback callback) {
        this.skeleton = s;
        this.task = task;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            Object retVal = task.call();
            callback.onSuccess(retVal);
        } catch (Exception e) {
            callback.onFail(e);
            stateChanged(new RMIException("CallbackTask fail", e));
        }

    }

    @Override
    public void stateChanged(Object... args) {
        try {
            Method callback = Skeleton.class.getDeclaredMethod("service_error", RMIException.class);
            callback.setAccessible(true);
            callback.invoke(this.skeleton, args);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "stateChanged(): Exception occured!", e);
        }
    }
}
