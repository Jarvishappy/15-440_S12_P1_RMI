package rmi.server.task;

import java.util.concurrent.Callable;

/**
 * A task with a callback, in order to get
 * the return value from task without blocking on
 * Future instance returned by ExecutorService.submit()
 *
 */
public final class CallbackTask implements Runnable {

    /**
     * Actual task to run
     */
    protected final Callable task;

    /**
     * Callback object
     */
    protected final Callback callback;


    public CallbackTask(Callable task, Callback callback) {
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
        }

    }
}
