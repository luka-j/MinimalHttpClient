package rs.lukaj.httpclient.connections;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Used for creating requests with Transfer-Encoding: chunked. Caller sends data using methods provided
 * here, which callee encodes and sends to the server.
 */
public interface ChunkSender {
    /**
     * Signals the beginning of the transfer. Establishes a connection and sends headers.
     * @throws IOException
     * @throws TimeoutException
     */
    void begin() throws IOException, TimeoutException;

    /**
     * Used to send actual chunks. Only raw data should be here, which will be compressed according to
     * Content-Encoding header. No additional length information shall be passed to this function. No
     * zero-length array shall be passed to this function.
     * @param chunk data to be sent
     */
    void sendChunk(byte[] chunk) throws IOException;

    /**
     * Signals the end of the transfer. Sends 0-length chunk and retrieves response.
     * @return response from server
     * @throws IOException
     */
    HttpResponse end() throws IOException;
}
