package rs.lukaj.httpclient.client;

/**
 * Signalizes user isn't logged in. Stack trace isn't filled for performance reasons.
 * Created by luka on 4.8.17.
 */

public class NotLoggedInException extends Exception {
    public NotLoggedInException() {super();}
    public NotLoggedInException(Class origin, String message) {super("Origin: " + origin.getCanonicalName()
                                                                     +"\nMessage: " + message);}

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
