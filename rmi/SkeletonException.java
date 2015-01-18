package rmi;

/**
 * Throw this exception when create Skeleton fail.
 *
 * @author siyuan
 * @date 2015-01-18 4:38 PM
 */
public class SkeletonException extends RuntimeException {

    public SkeletonException(String message) {
        super(message);
    }

    public SkeletonException(String message, Throwable cause) {
        super(message, cause);
    }
}
