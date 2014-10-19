package rmi.server.task;

public interface Callback {

    public void onSuccess(Object retVal);
    public void onFail(Exception e);
}
