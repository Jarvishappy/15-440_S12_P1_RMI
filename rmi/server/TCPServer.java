package rmi.server;

import rmi.RMIException;
import rmi.Skeleton;
import rmi.config.Config;
import rmi.server.callback.Callback;
import rmi.server.callback.MethodInvocationCallback;
import rmi.server.task.MethodInvocationTask;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private InetSocketAddress address;  // Server socket address


    private EventHandler eventHandler;

    public static final Throwable DUMMY_THROWABLE = new Throwable("dumb");

    /**
     * Constructors **
     */
    public TCPServer(Skeleton<T> s, Class<T> clazz, T serviceImpl, InetSocketAddress address)
            throws IOException {
        super("TCPServer");
        this.skeleton = s;
        this.service = clazz;
        this.serviceImpl = serviceImpl;
        this.address = address;
        initServer();
    }

    public TCPServer(Skeleton<T> s, Class<T> clazz, T serviceImpl)
            throws IOException {
        super("TCPServer");
        this.skeleton = s;
        this.service = clazz;
        this.serviceImpl = serviceImpl;
        initServer();
    }

    public TCPServer(Skeleton<T> s, Class<T> clazz, T serviceImpl, int port, int maxConnection)
            throws IOException {
        super("TCPServer");
        this.skeleton = s;
        this.service = clazz;
        this.serviceImpl = serviceImpl;
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
        eventHandler = new EventHandler(this.skeleton);
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
            case CREATED:
                stateTransition(ServerState.CREATED, ServerState.LISTENING);
                break;

            case LISTENING:
            case STOPPED:
                throw new IllegalStateException(
                        "TCPServer start fail, may has been started! state:" + this.state);

            default:
                break;
        }

        // permit server to start, increase the semaphore
        // permission.release();
        // start server thread
        super.start();
    }

    /**
     * Pauses listening for incoming connection, if server is listening.
     * Causes listening thread returns from listeningLoop(), blocking on permission.acquire().
     */
    public void stopServer() {
        if (this.state == ServerState.LISTENING) {
            // 不能马上调用callback，因为server还没完全关闭
            stateTransition(ServerState.LISTENING, ServerState.STOPPED);
        }
    }

    /**
     * Resumes listening
     */
/*    public void resumeServer() {
        stateTransition(ServerState.STOPPED, ServerState.LISTENING);
        // increase permission to allow listening thread to wake up
        // permission.release();
    }*/


    /**
     * Shut down the TCP server, shut down all inner thread pool.
     */
/*    public void shutdown() {
        if (this.state == ServerState.CREATED || this.state == ServerState.SHUTDOWN) {
            throw new IllegalStateException("Server isn't running!");
        }
        this.state = ServerState.SHUTDOWN;
    }*/


    /**
     * Listening incoming connections.
     * Main logic for the server.
     */
    private void listeningLoop() {
        try (ServerSocket serverSocket = this.address != null ?
                new ServerSocket(address.getPort(), Config.MAX_CONNECTION, address.getAddress()) :
                new ServerSocket(Config.LISTENING_PORT, Config.MAX_CONNECTION)) {

            while (!isStopped()) {
                LOGGER.info("[TCPServer] blocking on accept()...");
                ObjectInputStream in = null;
                try (Socket clientSocket = serverSocket.accept();
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())
                ) {
                    LOGGER.info("[TCPServer] accepted a new connection...");
                    // flush it before crate OIS
                    out.flush();
                    in = new ObjectInputStream(clientSocket.getInputStream());

                    // read methond name and arguments from inputStream
                    Method method = (Method) in.readObject();
                    Object[] args = (Object[]) in.readObject();

                    LOGGER.info(String.format("Got a RMI connection:\nmethod=>%s\nargs=>%s", method.getName(),
                            Arrays.toString(args)));

                    // submit a task to workerThreads
                    final Callable<Object> methodInvocation = new MethodInvocationTask(this.serviceImpl, method, args);
                    final Callback taskCallback = new MethodInvocationCallback(out);
                    Runnable task = new Runnable() {
                        /**
                         * Actual task to run
                         */
                        protected final Callable task = methodInvocation;

                        /**
                         * Callback object
                         */
                        protected final Callback callback = taskCallback;

                        @Override
                        public void run() {
                            try {
                                Object retVal = task.call();
                                callback.onSuccess(retVal);
                            } catch (Exception e) {
                                callback.onFail(e);
                                eventHandler.handleEvent(ServerEvent.SERVICE_ERROR,
                                        new RMIException(e.getMessage(), e));
                            }
                        }
                    };

                    workerThreads.submit(task);
                } catch (SocketException e) {
                    LOGGER.log(Level.WARNING, "Socket exception occured: ", e);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "IO exception occured: ", e);
                } finally {
                    try {
                        if (in != null) {
                            in.close();
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "close object input stream error!", e);
                    }
                }
            }

            LOGGER.info("[TCPServer] exit the listening loop");

            // 除IO异常之外的，认为发生了LISTEN_ERROR
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Server exception while listenning!", e);
            onEventOccur(ServerEvent.LISTEN_ERROR, e);
        }
/*        finally {
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
        }*/
    }

    private void onEventOccur(ServerEvent event, Object... args) {
        eventHandler.handleEvent(event, args);
    }


    /**
     * Change TCPServer state and invoke correspoding callbacks
     * @param before
     * @param after
     */
    private void stateTransition(ServerState before, ServerState after) {
        if (this.state != before) {
            throw new IllegalStateException(String.format(
                    "Current state: [%s]\nServer state transition [%s]->[%s] fail! ",
                    this.state.name(), before.name(), after.name()));
        }

        this.state = after;
    }


    private boolean isStopped() {
        return this.state == ServerState.STOPPED;
    }

    public InetSocketAddress getAddress() {
        return address;
    }
}
