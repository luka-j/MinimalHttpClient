package rs.lukaj.httpclient.connections;

/**
 * Thrown when headers are in invalid state (e.g. flags can be set to throw this on unknown or obsolete headers)
 */
public class InvalidHeaderException extends RuntimeException {
    public InvalidHeaderException(String message) {
        super(message);
    }
}
