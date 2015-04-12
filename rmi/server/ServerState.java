package rmi.server;

/**
 * Server state transition:
 *
 * CREATED --> INITED --> RUNNING --> STOPPED
 *
 */
public enum ServerState {
    CREATED,    // TCPServer对象被创建
    INITED,     // TCPServer对象初始化成功
    RUNNING,    // Listening thread成功启动
    STOPPED     // Listening thread terminated
}
