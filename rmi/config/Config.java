package rmi.config;

/**
 * Configuration constants
 */
public interface Config {
    String SERVER_HOST = "127.0.0.1";
    int MIN_THREAD = 5;
    int MAX_THREAD = 10;
    int KEEP_ALIVE_TIME = 10;
    int MAX_CONNECTION = 20;
    int LISTENING_PORT = 8888;
}
