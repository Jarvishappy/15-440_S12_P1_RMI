package rmi.server;

import rmi.Skeleton;
import rmi.config.Config;
import rmi.server.callback.Callback;
import rmi.server.callback.MethodInvocationCallback;
import rmi.server.event.Subject;
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
public class TCPServer<T> extends RMIServer<T> implements Subject {
    private static final Logger LOGGER = Logger.getLogger("TCPServer");

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
    public TCPServer(Skeleton<T> s, Class<T> clazz, T serverImpl, InetSocketAddress address)
            throws IOException {
        super(s, clazz, serverImpl);
        workerThreads = Executors.newFixedThreadPool(Config.MIN_THREAD);
        serverSocket = new ServerSocket(address.getPort(), Config.MAX_CONNECTION, address.getAddress());
        state = ServerState.CREATED;
    }

    public TCPServer(Skeleton<T> s, Class<T> clazz, T serviceImpl)
            throws IOException {
        super(s, clazz, serviceImpl);
        init(Config.LISTENING_PORT, Config.MAX_CONNECTION);
        try {
            addCallback(ServerState.STOPPED, Skeleton.class.getDeclaredMethod("stopped", Throwable.class));
            addCallback(ServerState.LISTEN_ERROR, Skeleton.class.getDeclaredMethod("listen_error", Exception.class));
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.WARNING, "[TCPServer] add callbacks FAIL!", e);
        }
    }

    public TCPServer(Skeleton<T> s, Class<T> clazz, T serviceImpl, int port, int maxConnection) throws IOException {
        super(s, clazz, serviceImpl);
        init(port, maxConnection);
    }
    /*** end Constructors ***/

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

    /**
     * Initialize TCPServer
     *
     * @param port          Listenning port
     * @param maxConnection maximum queue incoming connections
     * @throws IOException
     */
    private void init(int port, int maxConnection) throws IOException {
        // TODO 使用ThreadPoolExecutor来做线程池
        workerThreads = Executors.newFixedThreadPool(Config.MIN_THREAD);
        serverSocket = new ServerSocket(port, maxConnection);
        state = ServerState.CREATED;
    }

    /**
     * Change TCPServer state and invoke correspoding callbacks
     * @param before
     * @param after
     */
    private void stateTransition(ServerState before, ServerState after) {
        if (this.state != before) {
            throw new IllegalStateException(String.format(
                    "Server state transition to [%s] fail, not in the [%s] state!", after.name(), before.name()));
        }

        this.state = after;
        stateChanged();
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
                stateTransition(ServerState.CREATED, ServerState.LISTENNING);
                break;

            case LISTENNING:
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
     * Pauses listening for incoming connection.
     * Causes listening thread returns from listening(), blocking on permission.acquire().
     */
    public void stopListenning() {
        stateTransition(ServerState.LISTENNING, ServerState.STOPPED);
    }

    /**
     * Resumes listening
     */
    public void resumeServer() {
        stateTransition(ServerState.STOPPED, ServerState.LISTENNING);
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
     * Listenning incoming connections.
     * Main logic for the server.
     */
    private void listeningLoop() {
        Socket clientSocket = null;
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        try {
            while (!isShutDown() && !isStopped()) {
                clientSocket = serverSocket.accept();
                // TODO Why should create OOS first?
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
            LOGGER.log(Level.WARNING, "Exception occured while listenning!", e);
            stateTransition(ServerState.LISTENNING, ServerState.LISTEN_ERROR);
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

    @Override
    public void stateChanged(Object... arg) {
        invokeCallback(arg);
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
