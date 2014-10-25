package rmi.server.task;

import rmi.RMIException;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MethodInvocationCallback implements Callback {
    private static final Logger LOGGER = Logger.getLogger("MethodInvocationCallback");

    /**
     * Output stream of client connection
     */
    private ObjectOutputStream out;

    public MethodInvocationCallback(ObjectOutputStream out) {
        this.out = out;
    }

    @Override
    public void onSuccess(Object retVal) {
        try {
            out.writeObject(retVal);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "onSuccess(): write return value FAIL!", e);
        } finally {
            if (null != out) {
                try {
                    out.close();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "onSuccess(): close output stream FAIL!", e);
                }
            }

        }

    }

    @Override
    public void onFail(Exception e) {
        LOGGER.info("Exception occured on RMI: " + e.getMessage());
        try {
            out.writeObject(e);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "onFail(): write exception to client FAIL!", ex);
        }

    }
}
