package com.strikew.rmi.server.callback;

import com.strikew.rmi.RMIException;
import com.strikew.rmi.Utils;
import com.strikew.rmi.server.EventHandler;
import com.strikew.rmi.server.ServerEvent;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MethodInvocationCallback implements Callback {
    private static final Logger LOGGER = Logger.getLogger("MethodInvocationCallback");

    private EventHandler eventHandler;

    /**
     * Output stream of client connection
     */
    private ObjectOutputStream out;

    public MethodInvocationCallback(EventHandler eventHandler, ObjectOutputStream out) {
        if (null == eventHandler || null == out) {
            throw new IllegalArgumentException("args cannot be null");
        }
        this.eventHandler = eventHandler;
        this.out = out;
    }

    @Override
    public void onSuccess(Object retVal) {
        try {
            out.writeObject(retVal);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "onSuccess(): write return value FAIL!", e);
            eventHandler.handleEvent(ServerEvent.SERVICE_ERROR, new RMIException(e.getMessage(), e));
        } finally {
            Utils.closeResouce(out);

        }

    }

    @Override
    public void onFail(Throwable e) {
        try {
            out.writeObject(e);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "onFail(): write exception to client FAIL!", ex);
        } finally {
            Utils.closeResouce(out);
        }
    }
}
