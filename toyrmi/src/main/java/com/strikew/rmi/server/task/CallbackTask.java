package com.strikew.rmi.server.task;

import com.strikew.rmi.RMIException;
import com.strikew.rmi.RMIPacket;
import com.strikew.rmi.Utils;
import com.strikew.rmi.server.EventDispatcher;
import com.strikew.rmi.server.ServerEvent;
import com.strikew.rmi.server.callback.Callback;
import com.strikew.rmi.server.callback.MethodInvocationCallback;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Callable;

/**
 * A task with a callback, in order to get
 * the return value from task without blocking on
 * Future instance returned by ExecutorService.submit()
 *
 */
public final class CallbackTask<T> implements Runnable {
    private static final Log LOGGER = LogFactory.getLog(CallbackTask.class);

    /**
     * Actual task to run
    private Callable task;

    /**
     * Callback object
    private Callback callback;
     */

    private T serviceImpl;

    /**
     * Accepted client socket
     */
    private Socket sock;

    public CallbackTask(T target, Socket sock) {
        if (null == target || null == sock) {
            throw new IllegalArgumentException("args cannot be null");
        }
        this.serviceImpl = target;
        this.sock = sock;
    }

    @Override
    public void run() {
        try (ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
            out.flush();

            RMIPacket packet;
            try (ObjectInputStream in = new ObjectInputStream(sock.getInputStream())) {
                packet = (RMIPacket) in.readObject();

                if (null != packet) {
                    Method method = serviceImpl.getClass()
                            .getDeclaredMethod(packet.getMethodName(), packet.getParamTypes());

                    Callable<Object> callable = new MethodInvocationTask(serviceImpl, method, packet.getArgs());
                    Callback callback = new MethodInvocationCallback(out);
                    try {
                        Object retVal = callable.call();
                        callback.onSuccess(retVal);
                    } catch (InvocationTargetException e) {
                        callback.onFail(e.getCause());
                    }
                } else {
                    EventDispatcher.fireEvent(ServerEvent.SERVICE_ERROR, new RMIException("Received empty packet"));
                }

            } catch (SocketException e) {
                LOGGER.warn("Socket exception occurred during create ObjectInputStream: ", e);
                EventDispatcher.fireEvent(ServerEvent.SERVICE_ERROR, new RMIException(e.getMessage(), e));

            } catch (ClassNotFoundException e) {
                LOGGER.error("RMIPacket.class not found exception: ", e);
                EventDispatcher.fireEvent(ServerEvent.SERVICE_ERROR, new RMIException(e.getMessage(), e));
            }


        } catch (Exception e) {
            LOGGER.error("[CallbackTask] exception occurred:", e);
            EventDispatcher.fireEvent(ServerEvent.SERVICE_ERROR, new RMIException(e.getMessage(), e));
        } finally {
            Utils.closeResouce(sock);
        }

    }

}