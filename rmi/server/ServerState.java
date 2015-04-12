package rmi.server;

/**
 * Server state transition:
 *
 * CREATED --> LISTENING --> STOPPED
 *
 */
public enum ServerState {
    CREATED(1),

    /** Running **/
    LISTENING(2),
    STOPPED(3);

    private ServerState(int v) {
        this.value = v;
    }

    private int value;

    public int getValue() {
        return value;
    }


}
