package rmi.server.callback;

/**
 * Callback for Callable
 */
public interface Callback {

    /**
     * 远程调用成功，将返回值写回客户端连接的socket
     * @param retVal
     */
    public void onSuccess(Object retVal);

    /**
     * 远程调用失败，将异常写回客户端连接的socket
     * @param e
     */
    public void onFail(Exception e);
}
