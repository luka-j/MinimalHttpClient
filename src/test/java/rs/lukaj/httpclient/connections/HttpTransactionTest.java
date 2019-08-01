package rs.lukaj.httpclient.connections;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpTransactionTest {

    /**
     * Using HttpRequest and HttpResponse to make a connection. Higher-level than HttpSocket, but still not very useful
     */
    @Test
    public void manualTransaction() throws IOException, TimeoutException {
        ConnectionPool pool = new ConfigurableConnectionPool();
        HttpRequest req = HttpRequest.create(Http.Verb.GET, "http://httpbin.org/redirect/2");
        HttpSocket conn = req.connectNow(pool);
        HttpResponse resp = HttpResponse.from(conn, req);
        resp.parseResponse();
        assertEquals(302, resp.getStatus().responseCode);
        //using HttpRequest/HttpResponse this way doesn't handle anything automatically, including redirects
        //you can obtain body with resp.getBodyString(); or resp.writeBodyToFile(new File("/home/user/Downloads/test.gz"))
        conn.release();
    }

    /**
     * Using HttpTransaction to make a request. Works pretty fine if you're doing one-off requests on current thread.
     * Not-so-fine if you need to define timeouts and code is executed on UI thread (especially on Android)
     * For multiple requests sharing a connection pool and cache, use HttpClient.
     */
    @Test
    public void normalTransaction() throws IOException, TimeoutException {
        ConnectionPool pool = new ConfigurableConnectionPool();
        HttpTransaction transaction = new HttpTransaction(pool);
        HttpResponse response = transaction.makeRequest(Http.Verb.GET, "http://httpbin.org/get");
        assertEquals(200, response.getStatus().responseCode);
        assertEquals("gzip", response.getHeaders().getContentEncoding()); //default for Content-Encoding is gzip,deflate
        transaction.close();
    }

    /**
     * Setting different parameters for Connection header and seeing everything works fine.
     */
    @Test
    public void closingConnectionTransaction() throws IOException, TimeoutException {
        ConfigurableConnectionPool pool = new ConfigurableConnectionPool();
        HttpTransaction transaction = new HttpTransaction(pool);
        transaction.getHeaders().setConnection("Keep-Alive");
        HttpResponse response = transaction.makeRequest(Http.Verb.GET, "http://httpbin.org/get");
        assertEquals(200, response.getStatus().responseCode);
        transaction.close();
        assertEquals(1, pool.getPoolSize());

        transaction = new HttpTransaction(pool);
        transaction.getHeaders().setConnection("Close");
        response = transaction.makeRequest(Http.Verb.GET, "http://httpbin.org/get");
        assertEquals(200, response.getStatus().responseCode);
        assertEquals("close", response.getHeaders().getConnection().toLowerCase());
        transaction.close();
        assertEquals(0, pool.getPoolSize());
    }

    /**
     * Checking whether transaction handles redirects correctly.
     */
    @Test
    public void redirectingTransaction() throws IOException, TimeoutException {
        ConnectionPool pool = new ConfigurableConnectionPool();
        HttpTransaction transaction = new HttpTransaction(pool);
        HttpResponse response = transaction.makeRequest(Http.Verb.GET, "http://httpbin.org/redirect/2");
        assertEquals(200, response.getStatus().responseCode);
        transaction.close();
    }

    /**
     * Checking safeguards for infinite redirects.
     */
    @Test
    public void tooManyRedirects() throws IOException {
        ConnectionPool pool = new ConfigurableConnectionPool();
        HttpTransaction transaction = new HttpTransaction(pool);
        transaction.setMaxRedirects(1); //allowing only one redirect
        assertThrows(InvalidResponseException.class,
                () -> transaction.makeRequest(Http.Verb.GET, "http://httpbin.org/redirect/2"));
        transaction.close();
    }

    /**
     * Checking transaction handles 304 Not modified appropriately
     */
    @Test
    public void notModifiedTransaction() throws IOException, TimeoutException {
        ConnectionPool pool = new ConfigurableConnectionPool();
        RequestHeaders headers = RequestHeaders.createDefault();
        headers.setHeader("If-Modified-Since", "test");
        HttpTransaction transaction = new HttpTransaction(pool);
        HttpResponse response = transaction.setHeaders(headers).makeRequest(Http.Verb.GET, "http://httpbin.org/cache");
        assertEquals(200, response.getStatus().responseCode);
    }

    //I'd write some file-downloading tests here but don't know about the pretty way to do it and make it flexible
}
