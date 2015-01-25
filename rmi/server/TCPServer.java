package rmi.server;

import rmi.RMIException;
import rmi.Skeleton;
import rmi.config.Config;
import rmi.server.callback.Callback;
import rmi.server.callback.MethodInvocationCallback;
import rmi.server.task.CallbackTask;
import rmi.server.task.MethodInvocationTask;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An TCP multithreaded server implementation
 */
public class TCPServer<T> extends Thread {
    private static final Logger LOGGER = Logger.getLogger("TCPServer");

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


    private ServerState state;   // TCP Server state
    private ExecutorService workerThreads;
    private ServerSocket serverSocket;

    /**
     * 存放Skelton类中的事件回调函数
     */
    private Map<ServerState, Method> callbacks;

    /**
     * If the server allow to listen for incoming connection
     */
    private Semaphore permission = new Semaphore(0);

    /**
     * Constructors **
     */
    public TCPServer(Skeleton<T> s, Class<T> clazz, T serviceImpl, InetSocketAddress address)
            throws IOException {
        this.skeleton = s;
        this.service = clazz;
        this.serviceImpl = serviceImpl;
        this.serverSocket = new ServerSocket(address.getPort(), Config.MAX_CONNECTION, address.getAddress());
        initServer();
    }

    public TCPServer(Skeleton<T> s, Class<T> clazz, T serviceImpl)
            throws IOException {
        this.skeleton = s;
        this.service = clazz;
        this.serviceImpl = serviceImpl;
        this.serverSocket = new ServerSocket(Config.LISTENING_PORT, Config.MAX_CONNECTION);
        initServer();
    }

    public TCPServer(Skeleton<T> s, Class<T> clazz, T serviceImpl, int port, int maxConnection)
            throws IOException {
        this.skeleton = s;
        this.service = clazz;
        this.serviceImpl = serviceImpl;
        this.serverSocket = new ServerSocket(port, maxConnection);
        initServer();
    }
    /*** end Constructors ***/


    /**
     * Initialize TCPServer
     *
     * @throws IOException
     */
    private void initServer() throws IOException {
        // TODO 使用ThreadPoolExecutor来做线程池
        workerThreads = Executors.newFixedThreadPool(Config.MIN_THREAD);
        state = ServerState.CREATED;

        try {
            addCallback(ServerState.STOPPED, Skeleton.class.getDeclaredMethod("stopped", Throwable.class));
            addCallback(ServerState.LISTEN_ERROR,
                    Skeleton.class.getDeclaredMethod("listen_error", Exception.class));
            addCallback(ServerState.SERVICE_ERROR, Skeleton.class.getDeclaredMethod("service_error",
                    RMIException.class));
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.WARNING, "[TCPServer] add callbacks FAIL!", e);
        }

    }

    /**
     * Add callback to specified server state
     *
     * @param serverState the server state
     * @param callback    corresponding callback for the state
     */
    private void addCallback(ServerState serverState, Method callback) {
        if (null == callbacks) {
            callbacks = new HashMap<ServerState, Method>();
        }
        callbacks.put(serverState, callback);
    }


    @Override
    public void run() {
        while (!isShutDown()) {
            try {
                permission.acquire();
                LOGGER.log(Level.INFO, "[TCPServer] start listenning");
                listeningLoop();
                LOGGER.log(Level.INFO, "[TCPServer] complete listenning");
            } catch (InterruptedException e) {
                LOGGER.log(Level.INFO, "[TCPServer] being interrupted!");
            }

        }

    }

    @Override
    public void start() {
        throw new UnsupportedOperationException("Please call startServer() to start");
    }

    /**
     * Start the TCP server
     */
    public void startServer() {
        switch (this.state) {
            case CREATED:
                stateTransition(ServerState.CREATED, ServerState.LISTENING);
                break;

            case LISTENING:
            case LISTEN_ERROR:
            case STOPPED:
            case SERVICE_ERROR:
            case SHUTDOWN:
                throw new IllegalStateException(
                        "TCPServer start fail, may has been started! state:" + this.state);

            default:
                break;
        }

        // permit server to start, increase the semaphore
        permission.release();
        // start server thread
        super.start();
    }

    /**
     * Pauses listening for incoming connection, if server is listening.
     * Causes listening thread returns from listeningLoop(), blocking on permission.acquire().
     */
    public void stopListenning() {
        if (this.state == ServerState.LISTENING) {
            stateTransition(ServerState.LISTENING, ServerState.STOPPED);
        }
    }

    /**
     * Resumes listening
     */
    public void resumeServer() {
        stateTransition(ServerState.STOPPED, ServerState.LISTENING);
        // increase permission to allow listening thread to wake up
        permission.release();
    }


    /**
     * Shut down the TCP server, shut down all inner thread pool.
     */
    public void shutdown() {
        if (this.state == ServerState.CREATED || this.state == ServerState.SHUTDOWN) {
            throw new IllegalStateException("Server isn't running!");
        }
        this.state = ServerState.SHUTDOWN;
    }


    /**
     * Listening incoming connections.
     * Main logic for the server.
     */
    private void listeningLoop() {
        Socket clientSocket = null;
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        try {
            while (!isShutDown() && !isStopped()) {
                clientSocket = serverSocket.accept();
                out = new ObjectOutputStream(clientSocket.getOutputStream());
                // flush it before crate OIS
                out.flush();
                in = new ObjectInputStream(clientSocket.getInputStream());
                // read methond name and arguments from inputStream
                Method method = (Method) in.readObject();
                Object[] args = (Object[]) in.readObject();

                LOGGER.info(String.format("Got a RMI connection:\nmethod=>%s\nargs=>%s", method.getName(),
                        Arrays.toString(args)));

                // submit a task to workerThreads
                Callable<Object> methodInvocation = new MethodInvocationTask(this.serviceImpl, method, args);
                Callback callback = new MethodInvocationCallback(out);
                CallbackTask<T> task = new CallbackTask<T>(this.skeleton, methodInvocation, callback);

                workerThreads.submit(task);

            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception occured while listenning!", e);
            stateTransition(ServerState.LISTENING, ServerState.LISTEN_ERROR, e);
            stateTransition(ServerState.LISTEN_ERROR, ServerState.STOPPED, e);
        } finally {
            try {
                if (null != clientSocket) {
                    clientSocket.close();
                }
                if (null != out) {
                    out.close();
                }
                if (null != in) {
                    in.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "close socket or stream error!", e);
            }
        }
    }


    /**
     * Change TCPServer state and invoke correspoding callbacks
     * @param before
     * @param after
     * @param callbackArgs arguments for callbacks in Skeleton
     */
    private void stateTransition(ServerState before, ServerState after, Object... callbackArgs) {
        if (this.state != before) {
            throw new IllegalStateException(String.format(
                    "Current state: [%s]\nServer state transition to [%s] fail, not in the [%s] state! ",
                    this.state.name(), after.name(), before.name()));
        }

        this.state = after;
        invokeCallback(callbackArgs);
    }

    /**
     * Only called by stateTransition()
     */
    private void invokeCallback(Object... args) {
        Method callback = callbacks.get(this.state);
        if (null != callback) {
            callback.setAccessible(true);
            try {
                callback.invoke(this.skeleton, args);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception occrued while calling callback", e);
            }
        }

    }

    private boolean isShutDown() {
        return this.state == ServerState.SHUTDOWN;
    }

    private boolean isStopped() {
        return this.state == ServerState.STOPPED;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }
}
