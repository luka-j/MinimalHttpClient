package rs.lukaj.httpclient.connections;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Provides connections to the client. Connections == {@link HttpSocket}s
 */
public interface ConnectionPool {
    /**
     * Get connection to a given endpoint and block the thread while waiting. If timeout is reached, exception is thrown.
     * Obtaining connections <em>does not</em> have to be on first-come first-serve basis.
     * @param endpoint endpoint to which connection should go
     * @return HttpSocket to the endpoint
     * @throws IOException
     * @throws TimeoutException if no free connections are available after timeout duration has passed
     */
    HttpSocket getConnectionBlocking(Endpoint endpoint) throws IOException, TimeoutException;

    /**
     * Get connection to a given endpoint and wait on background thread if none are available.
     * Obtaining connections <em>does not</em> have to be on first-come first-serve basis.
     * @param endpoint endpoint to which connection should go
     * @param callbacks callbacks used for notifying the caller when connection is obtained or timeout is reached
     */
    void getConnectionAsync(Endpoint endpoint, ConnectionPool.Callbacks callbacks);

    /**
     * Callbacks used when obtaining connections asynchronously
     */
    interface Callbacks {
        /**
         * Connection successfully obtained. Use it to send your data, and close it when you're over.
         * @param connection newly obtained connection
         */
        void onConnectionObtained(HttpSocket connection);

        /**
         * Timeout reached without obtaining connection. Try raising timeout time and/or trying again.
         */
        void onTimeout();

        /**
         * Exception thrown while waiting for connection
         * @param ex exception thrown by something
         */
        void onExceptionThrown(IOException ex);
    }
}
