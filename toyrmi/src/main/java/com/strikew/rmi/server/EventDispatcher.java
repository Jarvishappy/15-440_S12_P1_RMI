package com.strikew.rmi.server;

import com.strikew.rmi.RMIException;
import com.strikew.rmi.Skeleton;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Single thread event dispatcher
 *
 * @author siyuan
 * @date 2015-04-11 10:07 AM
 */
public final class EventDispatcher {
    private static final Log LOGGER = LogFactory.getLog(EventDispatcher.class);

    private static Skeleton<?> target;

    private static ExecutorService dispatcherThread = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("[TOYRMI-event-dispatcher]");
            t.setDaemon(true);
            return t;
        }
    });

    /**
     * Store callbacks for ServerEvent
     */
    private static Map<ServerEvent, Method> callbacks;

    /**
     * Must initialize before use
     * @param target
     */
    public static synchronized void init(Skeleton<?> target) {
        if (null != EventDispatcher.target || null == target) {
            return;
        }
        EventDispatcher.target = target;
        try {
            callbacks = new HashMap<>();
            callbacks.put(ServerEvent.STOPPED, Skeleton.class.getDeclaredMethod("stopped", Throwable.class));
            callbacks.put(ServerEvent.LISTEN_ERROR, Skeleton.class.getDeclaredMethod("listen_error", Exception.class));
            callbacks.put(ServerEvent.SERVICE_ERROR,
                    Skeleton.class.getDeclaredMethod("service_error", RMIException.class));
        } catch (NoSuchMethodException e) {
            LOGGER.warn("[TCPServer] add callbacks FAIL!", e);
        }
    }


    private EventDispatcher() {
    }

    /**
     * Fire an event
     * @param event
     * @param args
     */
    public static void fireEvent(final ServerEvent event, final Object... args) {
        if (null == target) {
            throw new IllegalStateException("Should initiliaze before use");
        }

        dispatcherThread.execute(new Runnable() {
            @Override
            public void run() {
                // delegate to enclosing class
                EventDispatcher.handleEvent(event, args);
            }
        });
    }

    /**
     * Call the callback on target
     * @param event
     * @param args
     */
    private static void handleEvent(ServerEvent event, Object... args) {
        if (null == args) {
            throw new IllegalArgumentException("args cannot be null");
        }
        Method callback = callbacks.get(event);
        if (null != callback) {
            callback.setAccessible(true);
            try {
                callback.invoke(target, args);
            } catch (Exception e) {
                LOGGER.error("Exception occrued while calling callback", e);
            }
        }

    }
}
