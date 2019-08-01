package rs.lukaj.httpclient.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rs.lukaj.httpclient.client.HttpClient;
import rs.lukaj.httpclient.connections.ConfigurableConnectionPool;
import rs.lukaj.httpclient.connections.Http;
import rs.lukaj.httpclient.connections.HttpResponse;
import rs.lukaj.httpclient.connections.HttpTransaction;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpClientTest {

    /**
     * HttpClient is used mainly for creating HttpTransactions. It provides the same connection pool and cache to all
     * transactions it creates.
     */
    @Test
    public void normalUsage() throws IOException, TimeoutException {
        HttpClient client = HttpClient.create(); //you can use withPool (or getConnectionPool) to setup connection pool
        try(HttpTransaction transaction = client.newTransaction()) { //HttpTransaction implements closeable
            HttpResponse response = transaction.sendString("Hello").makeRequest(Http.Verb.PUT, "http://httpbin.org/anything");
            assertEquals(200, response.getStatus().responseCode);
            //httpbin returns json responses, and I'm not feeling like writing a JSON parser, so just testing a fragment
            assertTrue(response.getBodyString().contains("\"data\": \"Hello\""));
        }
        try(HttpTransaction transaction = client.newTransaction()) {
            HttpResponse response = transaction.sendString("Hello").makeRequest(Http.Verb.POST, "http://httpbin.org/anything");
            assertEquals(200, response.getStatus().responseCode);
            assertTrue(response.getBodyString().contains("\"data\": \"Hello\""));
            //we should be using one connection for both requests, because closing a transaction only closes underlying
            //socket if Connection:Close header is set (HttpURLConnection works in the similar way), which is not the case here
            Assertions.assertEquals(1, ((ConfigurableConnectionPool)client.getConnectionPool()).getPoolSize());
        }
    }
}
