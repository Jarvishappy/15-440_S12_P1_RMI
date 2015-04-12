package rmi.server;

import rmi.RMIException;
import rmi.Skeleton;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author siyuan
 * @date 2015-04-11 10:07 AM
 */
public class EventHandler {
    private static final Logger LOGGER = Logger.getLogger("EventHandler");

    private Object target;

    /**
     * 存放事件回调函数
     */
    private Map<ServerEvent, Method> callbacks;

    public EventHandler(Object target) {
        this.target = target;
        try {
            callbacks = new HashMap<>();
            callbacks.put(ServerEvent.STOPPED, Skeleton.class.getDeclaredMethod("stopped", Throwable.class));
            callbacks.put(ServerEvent.LISTEN_ERROR, Skeleton.class.getDeclaredMethod("listen_error", Exception.class));
            callbacks.put(ServerEvent.SERVICE_ERROR,
                    Skeleton.class.getDeclaredMethod("service_error", RMIException.class));
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.WARNING, "[TCPServer] add callbacks FAIL!", e);
        }
    }


    public void handleEvent(ServerEvent event, Object... args) {
        if (null == args) {
            throw new IllegalArgumentException("args cannot be null");
        }
        Method callback = callbacks.get(event);
        if (null != callback) {
            callback.setAccessible(true);
            try {
                callback.invoke(target, args);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception occrued while calling callback", e);
            }
        }

    }
}
