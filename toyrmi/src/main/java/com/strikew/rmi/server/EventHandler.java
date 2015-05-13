package com.strikew.rmi.server;

import com.strikew.rmi.RMIException;
import com.strikew.rmi.Skeleton;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author siyuan
 * @date 2015-04-11 10:07 AM
 */
public class EventHandler {
    private static final Log LOGGER = LogFactory.getLog(EventHandler.class);

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
            LOGGER.warn("[TCPServer] add callbacks FAIL!", e);
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
                LOGGER.error("Exception occrued while calling callback", e);
            }
        }

    }
}
