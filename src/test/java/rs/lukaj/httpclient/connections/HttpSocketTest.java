package rs.lukaj.httpclient.connections;


import org.junit.jupiter.api.Test;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpSocketTest {

    /**
     * Making a request at the lowest possible level, speaking directly to server over the socket.
     */
    @Test
    public void normalRequest() throws IOException {
        Endpoint endpoint = Endpoint.fromUrl("http://httpbin.org/");
        HttpSocket socket = new HttpSocket(endpoint);
        assertTrue(socket.acquireIfIdle());
        socket.write("POST /post HTTP/1.1\r\n".getBytes(UTF_8));
        socket.write("Host: httpbin.org\r\n".getBytes(UTF_8));
        socket.write("Content-Length: 0\r\n".getBytes(UTF_8));
        socket.write("\r\n".getBytes(UTF_8));
        String statusLine = socket.readLine();
        assertEquals(statusLine, "HTTP/1.1 200 OK");
        //you can use socket#readLine and socket#read to read headers, response body, etc., but it'd be too cumbersome this way
        socket.close();
    }
}
