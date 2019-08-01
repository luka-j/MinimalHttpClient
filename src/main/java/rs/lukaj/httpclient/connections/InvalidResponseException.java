package rs.lukaj.httpclient.connections;

/**
 * Thrown when Response is unexpected (e.g. it doesn't follow the spec, or redirects too many times)
 */
public class InvalidResponseException extends RuntimeException {
    public InvalidResponseException(String message) {
        super(message);
    }
}
