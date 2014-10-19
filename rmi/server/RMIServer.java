package rmi.server;

public abstract class RMIServer<T> extends Thread {

    /**
     * The remote interface type
     */
    protected Class<T> service;

    /**
     * Actual object offering serivce to client
     */
    protected T serviceImpl;

    protected RMIServer() {}

    protected RMIServer(Class<T> c, T server) {
        this.service = c;
        this.serviceImpl = server;
    }

}
