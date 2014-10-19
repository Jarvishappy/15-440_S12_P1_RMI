package rmi.server;

/**
 * Server state transition:
 *
 * CREATED --> RUNNING[ LISTENNING, PAUSE_LISTENNING] --> SHUTDOWN
 *
 */
public enum ServerState {
    CREATED(1),

    /** Running **/
    LISTENNING(2),
    PAUSE_LISTENNING(3),

    SHUTDOWN(4);

    private ServerState(int v) {
        this.value = v;
    }

    private int value;

    public int getValue() {
        return value;
    }


}
