package rs.lukaj.httpclient.connections;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connection pool which can be configured using values in {@link Config}.
 * @inheritDoc
 */
public class ConfigurableConnectionPool implements ConnectionPool {

    /**
     * List of all connections for each Endpoint
     */
    private Map<Endpoint, List<HttpSocket>> connections = new ConcurrentHashMap<>();
    private AtomicInteger connectionCount = new AtomicInteger(0);
    private Config config;
    private final Object lock = new Object();

    public ConfigurableConnectionPool(Config config) {
        this.config = config;
    }
    public ConfigurableConnectionPool() {
        this.config = new Config();
    }
    public ConfigurableConnectionPool(int maxConnections, int maxConnectionsPerEndpoint, Duration aliveTime, Duration maxWait) {
        this.config = new Config(maxConnections, maxConnectionsPerEndpoint, aliveTime, maxWait);
    }

    /**
     * Returns config for this connection pool. This method can be used to change parameters for this pool. No
     * guarantees are given when changes will take place.
     * @return config for this connection pool
     */
    public Config getConfig() {
        return config;
    }

    /**
     * Sets new config by replacing current config object. No guarantees are given when changes will take place.
     * @param config new config
     */
    public void setConfig(Config config) {
        this.config = config;
    }

    /**
     * @return number of alive requests currently in pool
     * @throws IOException
     */
    public int getPoolSize() throws IOException {
        cleanupConnections();
        return connectionCount.get();
    }

    //in retrospect, some callback like 'hey pool, this connection is released' should've been better
    //though cleaning up dead connections would still need to be handled
    public HttpSocket getConnectionBlocking(Endpoint endpoint) throws IOException, TimeoutException {
        long start = System.currentTimeMillis(); //we need to measure how long the caller is waiting for connection
        List<HttpSocket> conns;

        do {
            synchronized (lock) { //if we synchronize over the whole method, we can't keep track for how long
                                  // the thread is blocked (which should, intuitively, be included in wait time
                conns = connections.get(endpoint);
                HttpSocket conn = tryAcquireConnection(endpoint, conns);
                if (conn != null) {
                    if (conns == null) {
                        List<HttpSocket> c = new ArrayList<>();
                        c.add(conn);
                        connections.put(endpoint, c);
                    }
                    return conn;
                }
            }
            try {
                Thread.sleep(config.waitTime.toMillis());
            } catch (InterruptedException interrupt) {
                throw new TimeoutException("Cannot obtain connection; try again later.");
            }
        } while(Duration.ofMillis(System.currentTimeMillis() - start).compareTo(config.maxWait) < 0 &&
                ((conns != null && conns.size() > config.maxConnections) || connectionCount.get() > config.maxConnections));
        throw new TimeoutException("Cannot obtain connection; try again later.");
    }

    public void getConnectionAsync(Endpoint endpoint, ConnectionPool.Callbacks callbacks) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            private long start = System.currentTimeMillis();
            private List<HttpSocket> conns = connections.get(endpoint);
            @Override
            public void run() {
                if(Duration.ofMillis(System.currentTimeMillis() - start).compareTo(config.maxWait) > 0) {
                    timer.cancel();
                    callbacks.onTimeout();
                    return;
                }
                try {
                    synchronized (lock) {
                        conns = connections.get(endpoint);
                        HttpSocket conn = tryAcquireConnection(endpoint, conns);
                        if (conn != null) {
                            if (conns == null) {
                                List<HttpSocket> c = new ArrayList<>();
                                c.add(conn);
                                connections.put(endpoint, c);
                            }
                            timer.cancel();
                            callbacks.onConnectionObtained(conn);
                        }
                    }
                } catch (IOException e) {
                    timer.cancel();
                    callbacks.onExceptionThrown(e);
                }
            }
        }, 0, config.waitTime.toMillis());
    }

    private HttpSocket tryAcquireConnection(Endpoint endpoint, List<HttpSocket> pool) throws IOException {
        cleanupConnections();
        if(pool != null) {
            for(HttpSocket conn : pool) {
                if(conn.acquireIfIdle()) return conn;
            }
            if(pool.size() < config.maxConnectionsPerEndpoint && connectionCount.get() < config.maxConnections) {
                return makeConnection(endpoint, pool);
            }
        } else {
            if(connectionCount.get() <= config.maxConnections) {
                return makeConnection(endpoint, pool);
            }
        }
        return null;
    }

    private HttpSocket makeConnection(Endpoint endpoint, List<HttpSocket> pool) throws IOException {
        connectionCount.incrementAndGet();
        HttpSocket conn = new HttpSocket(endpoint);
        conn.acquireIfIdle();
        if(pool != null) pool.add(conn);
        return conn;
    }

    private void cleanupConnections() throws IOException {
        int connCount = 0;
        for (List<HttpSocket> conns : connections.values()) {
            for (Iterator<HttpSocket> it = conns.iterator(); it.hasNext(); ) {
                HttpSocket conn = it.next();
                if (conn.isClosed()) {
                    it.remove();
                } else if (conn.getIdlingTime().compareTo(config.aliveTime) > 0
                        || conn.getAge().compareTo(config.maxAge) > 0) {
                    conn.close();
                    it.remove();
                } else {
                    connCount++;
                }
            }
        }
        connectionCount.set(connCount);
    }


    public static class Config {
        //setting config directly on connection pool is kinda out of place, but doing it to comply with the requirements
        //better option would be to pass timeout when making each request (similar to NetworkRequestBuilder)
        //and let it handle all the timing
        private int maxConnections = 32;
        private int maxConnectionsPerEndpoint = 8;
        private Duration aliveTime = Duration.ofSeconds(60);
        private Duration maxWait = Duration.ofSeconds(2);
        private Duration maxAge = Duration.ofHours(2);
        private Duration waitTime = Duration.ofMillis(100);

        public Config() {
        }

        public Config(int maxConnections, int maxConnectionsPerEndpoint, Duration aliveTime, Duration maxWait) {
            if(maxConnections < 1 || maxConnectionsPerEndpoint < 1 || aliveTime.isZero() || aliveTime.isNegative()
                    || maxWait.isZero() || maxWait.isNegative()) {
                throw new InvalidConfigException("Connection numbers and durations must be positive!");
            }
            this.maxConnections = maxConnections;
            this.maxConnectionsPerEndpoint = maxConnectionsPerEndpoint;
            this.aliveTime = aliveTime;
            this.maxWait = maxWait;
        }

        /**
         * Set maximum number of connections this pool can contain. If max is reached, client has to wait for one of the
         * connections to be freed so it can take it over, or destroy it and use a new one.
         * @param maxConnections max number of connections
         */
        public void setMaxConnections(int maxConnections) {
            if(maxConnections < 1) throw new InvalidConfigException("maxConnections must be positive!");
            this.maxConnections = maxConnections;
        }

        /**
         * Sets maximum number of connections which can be kept open to a single endpoint.
         * @param maxConnectionsPerEndpoint maximum connections per endpoint
         */
        public void setMaxConnectionsPerEndpoint(int maxConnectionsPerEndpoint) {
            if(maxConnectionsPerEndpoint < 1) throw new InvalidConfigException("maxConnectionsPerEndpoint must be positive!");
            this.maxConnectionsPerEndpoint = maxConnectionsPerEndpoint;
        }

        /**
         * Sets maximum time connection can be alive and idling without being closed. If connection is idling for more
         * than aliveTime, it will be closed on the next occasion and won't be used again.
         * @param aliveTime maximum idling time
         */
        public void setAliveTime(Duration aliveTime) {
            if(aliveTime.isNegative() || aliveTime.isZero()) throw new InvalidConfigException("aliveTime must be positive!");
            this.aliveTime = aliveTime;
        }

        /**
         * Sets maximum duration user can wait for connection. If user waits for connection for more than maxWait,
         * TimeoutException is thrown.
         * @param maxWait maximum wait time for connection
         */
        public void setMaxWait(Duration maxWait) {
            if(maxWait.isNegative() || maxWait.isZero()) throw new InvalidConfigException("maxWait must be positive!");
            this.maxWait = maxWait;
        }

        /**
         * Sets maximum connection age. Age is calculated as a period between the time socket was opened and now. If
         * connection is older than maxAge, it will be closed on the next occasion and won't be used again. Connections
         * which are in use won't be closed regardless of age.
         * @param maxAge maximum age connection can live for
         */
        public void setMaxAge(Duration maxAge) {
            if(maxAge.isNegative() || maxAge.isZero()) throw new InvalidConfigException("maxAge must be positive!");
            this.maxAge = maxAge;
        }

        /**
         * Sets duration which user spends sleeping while waiting for connection. When requesting connection, if none
         * are available, instead of entering a tight loop, user sleeps and periodically checks if any connection has
         * freed up. This allows multiple requests to wait for connection (connection access is synchronized, sleeping
         * isn't). This number shouldn't be too high, but should be noticeably larger than 0.
         * @param waitTime how long the caller sleeps before checking if connection is available
         */
        public void setWaitTime(Duration waitTime) {
            if(waitTime.isNegative() || waitTime.isZero()) throw new InvalidConfigException("waitTime must be positive!");
            this.waitTime = waitTime;
        }
    }

}
