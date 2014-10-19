package rmi.server.task;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MethodInvocationCallback implements Callback {
    private static final Logger LOGGER = Logger.getLogger("MethodInvocationCallback");
    private ObjectOutputStream out;

    public MethodInvocationCallback(ObjectOutputStream out) {
        this.out = out;
    }

    @Override
    public void onSuccess(Object retVal) {
        try {
            out.writeObject(retVal);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "MethodInvocationCallback: write object exception!", e);

        } finally {
            if (null != out) {
                try {
                    out.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "MethodInvocationCallback: close output stream error!", e);
                }
            }

        }

    }

    @Override
    public void onFail(Exception e) {

    }
}
