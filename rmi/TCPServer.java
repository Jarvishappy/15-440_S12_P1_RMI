package rmi;

import rmi.config.Config;
import rmi.server.EventHandler;
import rmi.server.ServerEvent;
import rmi.server.ServerState;
import rmi.server.task.CallbackTask;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An TCP multithreaded server implementation
 * only visible to Skeleton
 */
class TCPServer<T> extends Thread {
    private static final Logger LOGGER = Logger.getLogger(TCPServer.class.getName());

    private static final int DEFAULT_PORT = 3182;

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

    /**
     * Access to state must be synchronized
     */
    private volatile ServerState state;   // TCP Server state
    private ExecutorService workerThreads;  // service thread pool
    private InetSocketAddress address;  // Server socket address
    private ServerSocket serverSocket;


    private EventHandler eventHandler;

    public static final Throwable DUMMY_THROWABLE = new Throwable("dumb");

    /**
     * Constructors **
     */
    public TCPServer(Skeleton<T> s, Class<T> clazz, T serviceImpl, InetSocketAddress address) {
        this.skeleton = s;
        this.service = clazz;
        this.serviceImpl = serviceImpl;
        this.address = address;
        this.state = ServerState.CREATED;
    }

    public TCPServer(Skeleton<T> s, Class<T> clazz, T serviceImpl) {
        this.skeleton = s;
        this.service = clazz;
        this.serviceImpl = serviceImpl;
        this.state = ServerState.CREATED;
    }

    /*** end Constructors ***/


    /**
     * Initialize TCPServer, must call this method before start()
     */
    public void initServer() throws IOException {
        setName("TCPServer");

        // assign a default address
        if (null == getAddress()) {
            address = new InetSocketAddress(DEFAULT_PORT);
        }
        // TODO 使用ThreadPoolExecutor来做线程池
        workerThreads = Executors.newFixedThreadPool(Config.MIN_THREAD);
        stateTransition(ServerState.CREATED, ServerState.INITED);
        eventHandler = new EventHandler(skeleton);
        serverSocket = address != null ?
                new ServerSocket(address.getPort(), Config.MAX_CONNECTION, address.getAddress()) :
                new ServerSocket(Config.LISTENING_PORT, Config.MAX_CONNECTION);
    }


    @Override
    public void run() {

        listeningLoop();
        // 退出循环后，尝试关闭线程池
        // 等线程池完全关闭了再调用stopped()函数

        boolean terminated = false;
        try {
            terminated = workerThreads.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (terminated) {
            LOGGER.info("[TCPServer] server terminated succesfully");
        } else {
            LOGGER.warning("[TCPServer] server terminated fail");
        }

        eventHandler.handleEvent(ServerEvent.STOPPED, DUMMY_THROWABLE);

    }

    @Override
    public void start() {
        switch (this.state) {
            case INITED:
                stateTransition(ServerState.INITED, ServerState.RUNNING);
                break;

            case CREATED:
            case RUNNING:
            case STOPPED:
                throw new IllegalStateException(
                        "TCPServer start fail, may has been started! state:" + getServerState());

            default:
                break;
        }

        // start server thread
        super.start();
    }

    /**
     * Terminate the listening thread if the server is in LISTENING state
     */
    public void stopServer() throws IOException {
        if (!isStopped()) {
            // 不能马上调用callback，因为server还没完全关闭
            stateTransition(ServerState.RUNNING, ServerState.STOPPED);
            serverSocket.close();
        }
    }


    /**
     * Listening incoming connections.
     * Main logic for the server.
     */
    private void listeningLoop() {
        try {
            while (!isStopped()) {
                LOGGER.info("[TCPServer] blocking on accept()...");
                try {
                    // clientSocket的状态应该由Worker thread来维护，而不是Listening thread

                    Socket clientSocket = serverSocket.accept();
                    CallbackTask<T> task = new CallbackTask<>(eventHandler, serviceImpl, clientSocket);
                    workerThreads.submit(task);
                } catch (SocketException e) {
                    //LOGGER.log(Level.WARNING, "Socket exception while listening: ", e);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "IO exception while listening: ", e);
                }
            }

            LOGGER.info("[TCPServer] exit the listening loop");

            // 除IO异常之外的，认为发生了LISTEN_ERROR
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Server exception while listenning:", e);
            eventHandler.handleEvent(ServerEvent.LISTEN_ERROR, e);
        }
    }

    /**
     * Change TCPServer state and invoke correspoding callbacks
     * @param before
     * @param after
     */
    private synchronized void stateTransition(ServerState before, ServerState after) {
        if (getServerState() != before) {
            throw new IllegalStateException(String.format(
                    "Current state: [%s]\nServer state transition [%s]->[%s] fail! ",
                    getServerState().name(), before.name(), after.name()));
        }

        this.state = after;
    }

    public synchronized boolean isStopped() {
        return getServerState() == ServerState.STOPPED;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public ServerState getServerState() {
        return state;
    }
}
