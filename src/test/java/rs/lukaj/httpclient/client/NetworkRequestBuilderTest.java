package rs.lukaj.httpclient.client;

import org.junit.jupiter.api.Test;
import rs.lukaj.httpclient.client.HttpClient;
import rs.lukaj.httpclient.client.Network;
import rs.lukaj.httpclient.client.NetworkExceptionHandler;
import rs.lukaj.httpclient.client.NetworkRequestBuilder;
import rs.lukaj.httpclient.connections.Http;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

public class NetworkRequestBuilderTest {

    /**
     * Basic example of NetworkRequestBuilder usage.
     */
    @Test
    public void sendStringReceiveString() throws ExecutionException, TimeoutException, IOException {
        HttpClient client = HttpClient.create(); //used for config
        Network.Response<String> response =
                new NetworkRequestBuilder<String, String>(client, Http.Verb.PUT, "http://httpbin.org/put")
                        .sendString("Hello")
                        .blocking(Duration.ofSeconds(5)); //note how there's no closing anything here
        assertEquals(200, response.getStatus().responseCode);
        assertTrue(response.getResponseData().contains("\"data\": \"Hello\""));
    }

    /**
     * Send a urlencoded form to the server, specified by a Map. When sending forms using GET,
     * NetworkRequestBuilder embeds the form into the URL.
     */
    @Test
    public void sendForm() throws ExecutionException, TimeoutException, IOException {
        HttpClient client = HttpClient.create();
        Map<String, String> params = new HashMap<>() {{
            put("key1", "value1");
            put("key2", "value2");
        }};
        Network.Response<String> response =
                new NetworkRequestBuilder<String, String>(client, Http.Verb.PUT, "http://httpbin.org/anything")
                        .sendForm(params)
                        .blocking(Duration.ofSeconds(5));
        assertEquals(200, response.getStatus().responseCode);
        String ret = response.getResponseData();
        assertTrue(ret.contains("\"Content-Type\": \"application/x-www-form-urlencoded\"")); //don't assert this if using GET
        assertTrue(ret.contains("\"key1\": \"value1\"") && ret.contains("\"key2\": \"value2\""));
        //this is inside the "form" object in JSON
    }

    /**
     * Using NetworkRequestBuilder so that requests are done on the background thread, using the same
     * callbacks for two different requests.
     */
    @Test
    public void asyncUsage() {
        HttpClient client = HttpClient.create();
        final int helloId = 0, goodbyeId = 1;
        //we can use same NetworkCallbacks for multiple requests, e.g.
        Network.NetworkCallbacks<String> callbacks = new Network.NetworkCallbacks<>() {
            @Override
            public void onRequestCompleted(int id, Network.Response<String> response) {
                assertEquals(200, response.getStatus().responseCode);
                switch (id) {
                    case helloId: assertTrue(response.getResponseData().contains("\"data\": \"Hello\"")); break;
                    case goodbyeId: assertTrue(response.getResponseData().contains("\"data\": \"Goodbye\"")); break;
                    default: fail("Invalid id");
                }
            }

            @Override
            public void onExceptionThrown(int id, Throwable ex) {
                fail(ex);
            }

            @Override
            public void onRequestTimedOut(int id) {
                fail("Request " + id + " timed out");
            }
        };
        new NetworkRequestBuilder<String, String>(client, Http.Verb.PUT, "http://httpbin.org/put")
                .setId(helloId)
                .sendString("Hello")
                //.setExecutor(Executors.newCachedThreadPool()) //e.g. you could set a custom executor (but don't have to)
                .async(callbacks, Duration.ofSeconds(5));
        new NetworkRequestBuilder<String, String>(client, Http.Verb.POST, "http://httpbin.org/post")
                .setId(goodbyeId)
                .sendString("Goodbye")
                .async(callbacks, Duration.ofSeconds(5));
    }

    private volatile boolean errorOccurred = false;
    /**
     * Demonstrates how NetworkExceptionHandler can be used. Instead of parsing the response code,
     * use methods in handler to handle error responses.
     */
    @Test
    public void handleErrorResponse() throws ExecutionException, TimeoutException, IOException {
        errorOccurred = false;
        HttpClient client = HttpClient.create();
        new NetworkRequestBuilder<String, String>(client, Http.Verb.GET, "http://httpbin.org/status/500")
                .setHandler(new NetworkExceptionHandler.DefaultHandler() {
                    @Override
                    public void handleServerError(String message) {
                        errorOccurred = true;
                    }
                    @Override
                    public void finishedSuccessfully() {
                        //this is called after response is parsed without errors, and in case async request is used,
                        //before calling callbacks
                        errorOccurred = false;
                    }
                })
                .blocking(Duration.ofSeconds(5));
        assertTrue(errorOccurred);
    }

    //not testing auth here, as it depends heavily on the server. Ask me and I'll show you how it works on my server.
}
