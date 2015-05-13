package com.strikew.rmi.client.proxy;

import com.strikew.rmi.RMIException;
import com.strikew.rmi.RMIPacket;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;

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
        Object retVal = null;

        // Theses methods also dispatched to here
        if ("equals".equals(method.getName())) {
            retVal = this.proxyEquals(proxy, args);

        } else if ("toString".equals(method.getName())) {
            retVal = this.proxyToString(proxy);

        } else if ("hashCode".equals(method.getName())) {
            retVal = this.proxyHashCode(proxy);

        }

        if (null == retVal) {
            try (Socket socket = new Socket(serverSockAddr.getAddress(), serverSockAddr.getPort());
                    // not need to call connect() like in C
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
            ) {
                // flush before
                out.flush();
                try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    RMIPacket packet = new RMIPacket();
                    packet.setMethodName(method.getName());
                    packet.setParamTypes(method.getParameterTypes());
                    packet.setArgs(args);

                    // transport the packet to server
                    out.writeObject(packet);

                    // read return value from server
                    retVal = in.readObject();
                    if (retVal instanceof Throwable) {
                        throw (Throwable) retVal;
                    }
                }
            } catch (SocketException e) {
                throw new RMIException(e.getMessage(), e);
            }
        }

        return retVal;
    }

    private boolean proxyEquals(Object thisProxy, Object[] args) {
        Object otherProxy = args[0];
        if (null == otherProxy) {
            return false;
        }
        // check if otherProxy.class is a proxy class
        if (!Proxy.isProxyClass(otherProxy.getClass())) {
            return false;
        }

        // compare implemented interface
        Class<?>[] thisInterfs = thisProxy.getClass().getInterfaces();
        Class<?>[] otherInterfs = otherProxy.getClass().getInterfaces();

        if (!Arrays.equals(thisInterfs, otherInterfs)) {
            return false;
        }

        // compare remote host
        InvocationHandler handler = Proxy.getInvocationHandler(otherProxy);
        if (handler instanceof StubInvocationHandler) {
            StubInvocationHandler thisHandler = (StubInvocationHandler) Proxy.getInvocationHandler(thisProxy);
            StubInvocationHandler otherHandler = (StubInvocationHandler) handler;
            return thisHandler.getServerSockAddr().equals(otherHandler.getServerSockAddr());
        } else {
            return false;
        }
    }

    private String proxyToString(Object proxy) {
        StringBuilder sb = new StringBuilder();
        Class<?>[] interfs = proxy.getClass().getInterfaces();

        for (int i = 0; i < interfs.length; i++) {
            if (i != interfs.length - 1) {
                sb.append(interfs[i].getSimpleName()).append(", ");
            } else {
                sb.append(interfs[i].getSimpleName());
            }

        }
        return sb.toString() + "[host: " + serverSockAddr.getHostName() + ", port: "
                + serverSockAddr.getPort() + "]";
    }

    private int proxyHashCode(Object proxy) {
        String clazzName = proxy.getClass().getCanonicalName();
        return this.serverSockAddr.hashCode() + clazzName.hashCode();
    }

    public InetSocketAddress getServerSockAddr() {
        return serverSockAddr;
    }

    /*
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }*/
}
