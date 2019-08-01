package rs.lukaj.httpclient.connections;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurableConnectionPoolTest {

    /**
     * Obtaining, releasing and closing connections demo.
     */
    @Test
    public void normalObtainConnection() throws IOException, TimeoutException {
        Endpoint endpoint = Endpoint.fromUrl("http://httpbin.org");
        ConfigurableConnectionPool pool = new ConfigurableConnectionPool(8, 2, Duration.ofMinutes(2), Duration.ofSeconds(2));
        HttpSocket connection = pool.getConnectionBlocking(endpoint);
        assertFalse(connection.acquireIfIdle()); //pool should return active (non-idle) connections
        connection.release(); //releasing a connection does not remove it from the pool...
        assertEquals(1, pool.getPoolSize());
        connection.close(); //...but closing does
        assertEquals(0, pool.getPoolSize());
    }

    /**
     * 1-connection pool starved by 2 connections.
     */
    @Test
    public void starvePool() throws IOException, TimeoutException {
        Endpoint endpoint = Endpoint.fromUrl("http://httpbin.org");
        ConfigurableConnectionPool pool = new ConfigurableConnectionPool(1, 1, Duration.ofMinutes(2), Duration.ofMillis(500));
        HttpSocket connection = pool.getConnectionBlocking(endpoint);
        assertThrows(TimeoutException.class, () -> pool.getConnectionBlocking(endpoint));
        connection.close();
    }

    /**
     * What happens when there are enough connections, but you're limited to one connection per endpoint
     */
    @Test
    public void starveEndpoint() throws IOException, TimeoutException {
        Endpoint httpbin = Endpoint.fromUrl("http://httpbin.org");
        Endpoint example = Endpoint.fromUrl("http://example.org");
        ConfigurableConnectionPool pool = new ConfigurableConnectionPool(4, 1, Duration.ofMinutes(2), Duration.ofMillis(500));
        HttpSocket bin1 = pool.getConnectionBlocking(httpbin);
        HttpSocket example1 = pool.getConnectionBlocking(example);
        assertThrows(TimeoutException.class, () -> pool.getConnectionBlocking(httpbin));
        assertThrows(TimeoutException.class, () -> pool.getConnectionBlocking(example));
        bin1.close();
        example1.close();
    }

    /**
     * Obtaining connection without blocking the current thread (but still respecting the timeout).
     */
    @Test
    public void asyncObtainConnection() throws MalformedURLException, UnknownHostException {
        Endpoint endpoint = Endpoint.fromUrl("http://httpbin.org");
        ConfigurableConnectionPool pool = new ConfigurableConnectionPool(8, 2, Duration.ofMinutes(2), Duration.ofSeconds(1));
        pool.getConnectionAsync(endpoint, new ConnectionPool.Callbacks() {
            @Override
            public void onConnectionObtained(HttpSocket connection) {
                //by default, this executes on Timer thread, but I have no idea how to show that through JUnit
                assertFalse(connection.acquireIfIdle());
            }

            @Override
            public void onTimeout() {
                fail("Waiting for connection too long! Timed out.");
            }

            @Override
            public void onExceptionThrown(IOException ex) {
                fail(ex);
            }
        });
    }
}
