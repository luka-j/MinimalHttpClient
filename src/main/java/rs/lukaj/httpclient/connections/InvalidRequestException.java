package rs.lukaj.httpclient.connections;

/**
 * Thrown when request is in invalid state (e.g. trying to send both String and File)
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
