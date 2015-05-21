package com.strikew.rmi.server.callback;

import com.strikew.rmi.RMIException;
import com.strikew.rmi.Utils;
import com.strikew.rmi.server.EventDispatcher;
import com.strikew.rmi.server.ServerEvent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class MethodInvocationCallback implements Callback {
    private static final Log LOGGER = LogFactory.getLog(MethodInvocationCallback.class);

    /**
     * Output stream of client connection
     */
    private ObjectOutputStream out;

    public MethodInvocationCallback(ObjectOutputStream out) {
        if (null == out) {
            throw new IllegalArgumentException("args cannot be null");
        }
        this.out = out;
    }

    @Override
    public void onSuccess(Object retVal) {
        try {
            out.writeObject(retVal);
        } catch (Exception e) {
            LOGGER.error("onSuccess(): write return value FAIL!", e);
            EventDispatcher.fireEvent(ServerEvent.SERVICE_ERROR, new RMIException(e.getMessage(), e));
        } finally {
            Utils.closeResouce(out);

        }

    }

    @Override
    public void onFail(Throwable e) {
        try {
            out.writeObject(e);
        } catch (IOException ex) {
            LOGGER.error("onFail(): write exception to client FAIL!", ex);
        } finally {
            Utils.closeResouce(out);
        }
    }
}
