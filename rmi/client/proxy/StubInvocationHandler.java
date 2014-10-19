package rmi.client.proxy;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Invocation handler for the client proxy class
 */
public class StubInvocationHandler implements InvocationHandler {

    /**
     * Server socket address (hostname, port)
     */
    private InetSocketAddress serverSockAddr;

    public StubInvocationHandler(InetSocketAddress address) {
        this.serverSockAddr = address;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        Socket socket = new Socket(serverSockAddr.getAddress(), serverSockAddr.getPort());
        // not need to connect like in C
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        // write out method and args to server
        out.writeObject(method);
        out.writeObject(args);


        // read return value from server
        Object retVal = in.readObject();

        return retVal;
    }
}
