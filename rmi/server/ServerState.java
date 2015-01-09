package rmi.server;

/**
 * Server state transition:
 *
 * CREATED --> RUNNING[ LISTENNING, STOPPED ] --> SHUTDOWN
 *
 */
public enum ServerState {
    CREATED(1),

    /** Running **/
    LISTENNING(2),
    STOPPED(3), // stop listenning

    SHUTDOWN(4),

    /** Events **/
    LISTEN_ERROR(5),
    SERVICE_ERROR(6);

    private ServerState(int v) {
        this.value = v;
    }

    private int value;

    public int getValue() {
        return value;
    }


}
