package rmi.server;

import rmi.config.Config;
import rmi.server.task.Callback;
import rmi.server.task.CallbackTask;
import rmi.server.task.MethodInvocation;
import rmi.server.task.MethodInvocationCallback;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
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
     * If the server allow to run
     */
    private Semaphore permission = new Semaphore(0);

    public TCPServer(Class<T> clazz, T serviceImpl) throws IOException {
        super(clazz, serviceImpl);
        init(Config.LISTENING_PORT, Config.MAX_CONNECTION);
    }
    public TCPServer(int port, int maxConnection, Class<T> clazz, T serviceImpl) throws IOException {
        super(clazz, serviceImpl);
        init(port, maxConnection);
    }

    @Override
    public void run() {
        while (isRunning()) {
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
        if (isRunning()) {
            throw new IllegalStateException("Server start fail, has been started!");
        }

        // permit server to start
        permission.release();
        // start server thread
        super.start();
    }

    public void pauseServer() {
        if (!state.compareAndSet(ServerState.LISTENNING.getValue(), ServerState.PAUSE_LISTENNING.getValue())) {
            throw new IllegalStateException("Server has been started!");
        }
    }

    public void resumeServer() {
       if (!state.compareAndSet(ServerState.PAUSE_LISTENNING.getValue(), ServerState.LISTENNING.getValue())) {
           throw new IllegalStateException("Server is not paused!");
       }
        permission.release();
    }


    /**
     * Shut down the TCP server, shut down all inner thread pool.
     */
    public void shutDown() {
        int currentStatus = state.get();
        if (ServerState.CREATED.getValue() == currentStatus
                || ServerState.SHUTDOWN.getValue() == currentStatus) {
            throw new IllegalStateException("Server isn't running!");
        }

        state.set(ServerState.SHUTDOWN.getValue());
    }


    private void init(int port, int maxConnection) throws IOException {
        workerThreads = Executors.newFixedThreadPool(Config.MIN_THREAD);
        serverSocket = new ServerSocket(port, maxConnection);
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
            while (isRunning() && !isPause()) {
                clientSocket = serverSocket.accept();
                out = new ObjectOutputStream(clientSocket.getOutputStream());
                in = new ObjectInputStream(clientSocket.getInputStream());
                //TODO
                // read methond name and arguments from in
                Method method = (Method) in.readObject();
                Object[] args = (Object[]) in.readObject();

                // submit a task to workerThreads
                Callable<Object> methodInvocation = new MethodInvocation(this.serviceImpl, method, args);
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


    private boolean isRunning() {
        return (state.get() == ServerState.LISTENNING.getValue() ||
                state.get() == ServerState.PAUSE_LISTENNING.getValue());
    }

    private boolean isPause() {
        return state.get() == ServerState.PAUSE_LISTENNING.getValue();
    }


}
