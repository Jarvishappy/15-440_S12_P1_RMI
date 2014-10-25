package rmi.server;

import rmi.Skeleton;

public abstract class RMIServer<T> extends Thread {

    /**
     * The remote interface type
     */
    protected Class<T> service;

    /**
     * Actual object offering serivce to client
     */
    protected T serviceImpl;

    /**
     * Reference to the containing skeleton of the server
     */
    protected Skeleton<T> skeleton;

    protected RMIServer() {}

    protected RMIServer(Skeleton<T> skeleton, Class<T> c, T server) {
        this.skeleton = skeleton;
        this.service = c;
        this.serviceImpl = server;
    }

}
