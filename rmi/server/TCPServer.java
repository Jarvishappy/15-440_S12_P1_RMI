package rmi.server;

import rmi.Skeleton;
import rmi.config.Config;
import rmi.server.task.Callback;
import rmi.server.task.CallbackTask;
import rmi.server.task.MethodInvocationTask;
import rmi.server.task.MethodInvocationCallback;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An TCP multithreaded server implementation
 */
public class TCPServer<T> extends RMIServer<T> {
    private static final Logger LOGGER = Logger.getLogger("TCPServer");

    private AtomicInteger state;   // TCP Server state

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

    public TCPServer(Class<T> clazz, T serviceImpl)
            throws IOException {
        super(clazz, serviceImpl);
        init(Config.LISTENING_PORT, Config.MAX_CONNECTION);
        try {
            addCallback(ServerState.STOPPED, Skeleton.class.getDeclaredMethod("stopped"));
            addCallback(ServerState.LISTEN_ERROR, Skeleton.class.getDeclaredMethod("listen_error"));
            addCallback(ServerState.SERVICE_ERROR, Skeleton.class.getDeclaredMethod("service_error"));
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.WARNING, "[TCPServer] add callbacks FAIL!", e);
        }

    }
    public TCPServer(Skeleton<T> skeleton, int port, int maxConnection, Class<T> clazz, T serviceImpl) throws IOException {
        super(clazz, serviceImpl);
        init(port, maxConnection);
    }

    private void init(int port, int maxConnection) throws IOException {
        workerThreads = Executors.newFixedThreadPool(Config.MIN_THREAD);
        serverSocket = new ServerSocket(port, maxConnection);
        state.set(ServerState.CREATED.getValue());
    }

    private void addCallback(ServerState state, Method callback) {
        if (null == callbacks) {
            callbacks = new HashMap<ServerState, Method>();
        }
        callbacks.put(state, callback);
    }


    @Override
    public void run() {
        while (!isShutDown()) {
            try {
                permission.acquire();
                LOGGER.log(Level.INFO, "[TCPServer] start listenning");
                listening();
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
        if (!state.compareAndSet(ServerState.CREATED.getValue(), ServerState.LISTENNING.getValue())) {
            throw new IllegalStateException("Server start fail, not in the created state!");
        }
        if (isShutDown()) {
            throw new IllegalStateException("Server start fail, has been started!");
        }

        // permit server to start
        permission.release();
        // start server thread
        super.start();
    }

    /**
     * Pauses listening for incoming connection.
     * Causes listening thread returns from listening(), blocking on permission.acquire().
     */
    public void stopListenning() {
        if (!state.compareAndSet(ServerState.LISTENNING.getValue(), ServerState.STOPPED.getValue())) {
            throw new IllegalStateException("Server has been started!");
        }
    }

    /**
     * Resumes listening
     */
    public void resumeServer() {
       if (!state.compareAndSet(ServerState.STOPPED.getValue(), ServerState.LISTENNING.getValue())) {
           throw new IllegalStateException("Server is not paused!");
       }
        permission.release();
    }


    /**
     * Shut down the TCP server, shut down all inner thread pool.
     */
    public void shutdown() {
        int currentStatus = state.get();
        if (ServerState.CREATED.getValue() == currentStatus
                || ServerState.SHUTDOWN.getValue() == currentStatus) {
            throw new IllegalStateException("Server isn't running!");
        }

        state.set(ServerState.SHUTDOWN.getValue());
    }


    /**
     * Listenning incoming connections.
     * Main logic for the server.
     */
    private void listening() {
        Socket clientSocket = null;
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        try {
            while (isShutDown() && !isStopped()) {
                clientSocket = serverSocket.accept();
                out = new ObjectOutputStream(clientSocket.getOutputStream());
                in = new ObjectInputStream(clientSocket.getInputStream());
                //TODO
                // read methond name and arguments from in
                Method method = (Method) in.readObject();
                Object[] args = (Object[]) in.readObject();

                LOGGER.info(String.format("Got a RMI connection:\nmethod=>%s\nargs=>%s", method.getName(),
                        Arrays.toString(args)));

                // submit a task to workerThreads
                Callable<Object> methodInvocation = new MethodInvocationTask(this.serviceImpl, method, args);
                Callback callback = new MethodInvocationCallback(out);
                CallbackTask task = new CallbackTask(methodInvocation, callback);

                workerThreads.submit(task);

            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "IO exception occured!", e);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unknown exception occured!", e);
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


    private boolean isShutDown() {
        return state.get() == ServerState.SHUTDOWN.getValue();
    }

    private boolean isStopped() {
        return state.get() == ServerState.STOPPED.getValue();
    }


}
