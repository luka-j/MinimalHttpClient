package rs.lukaj.httpclient.connections;

/**
 * Thrown if configuration parameters are invalid (e.g. a negative value when expected only positive)
 */
public class InvalidConfigException extends RuntimeException {
    public InvalidConfigException(String message) {
        super(message);
    }
}
