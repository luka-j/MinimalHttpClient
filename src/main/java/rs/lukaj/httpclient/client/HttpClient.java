package rs.lukaj.httpclient.client;

import rs.lukaj.httpclient.connections.*;

/**
 * Top-level class for making HTTP requests. Used to open {@link HttpTransaction}s which are used for communicating
 * with the server. There is one {@link ConnectionPool} and one {@link HttpCache} per client, which can be adjusted
 * on the fly.
 * <br/>
 * Example with default values:
 * <br/>
 * <pre>
 *     HttpTransaction transaction = HttpClient.create().newTransaction();
 * </pre>
 * <br/>
 * More customized example:
 * <br/>
 * <pre>
 *     ConnectionPool pool = new ConfigurableConnectionPool();
 *     pool.getConfig().setMaxConnections(4);
 *     HttpClient client = HttpClient.create()
 *                                   .withPool(pool)
 *                                   .withCache(newFifoHttpCache(64);
 *     HttpTransaction transaction = client.newTransaction();
 *     //do something with the transaction
 *     HttpTransaction newTransaction = client.newTransaction();
 * </pre>
 */
public class HttpClient {
    private ConnectionPool connectionPool = new ConfigurableConnectionPool();
    private HttpCache      cache          = new FifoHttpCache();
    private Http.Version   httpVersion    = Http.Version.HTTP11;
    private CachingPolicy cachingPolicy = new SimpleCachingPolicy();

    private HttpClient() {
    }

    /**
     * Create a {@link HttpClient} with default parameters.
     * @return a new {@link HttpClient} instance
     */
    public static HttpClient create() {
        return new HttpClient();
    }

    /**
     * Set {@link ConnectionPool} used for obtaining {@link HttpSocket}s. All new {@link HttpTransaction}s
     * will use sockets from that pool, but any ongoing ones will continue using the old pool. Take care to
     * clean up the old pool and close the outstanding sockets <em>before</em> calling this method.
     * @param connectionPool new pool to be used with this client
     * @return this instance, to allow chaining
     */
    public HttpClient withPool(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
        return this;
    }

    /**
     * Set {@link ConnectionPool} used for obtaining {@link HttpSocket}s. This will create a new pool to be
     * used for any new {@link HttpTransaction}s.
     * @param config configuration parameters for the new pool
     * @return this instance, to allow chaining
     * @see #withPool(ConnectionPool)
     */
    public HttpClient withPool(ConfigurableConnectionPool.Config config) {
        this.connectionPool = new ConfigurableConnectionPool(config);
        return this;
    }

    /**
     * Set {@link HttpCache} to be used with transactions using this client, according to the {@link CachingPolicy}.
     * @param cache cache to use for caching responses
     * @return this instance, to allow chaining
     */
    public HttpClient withCache(HttpCache cache) {
        this.cache = cache;
        return this;
    }

    /**
     * Set {@link CachingPolicy} to be used for transactions using this client. CachingPolicy describes which requests
     * should be cached and when to search cache for response.
     * @param policy caching policy to use for this client
     * @return this instance, to allow chaining
     */
    public HttpClient withCachingPolicy(CachingPolicy policy) {
        this.cachingPolicy = policy;
        return this;
    }

    /**
     * Get {@link ConnectionPool} used for obtaining {@link HttpSocket}s by this client. Changing parameters of the
     * returned pool will impact future and, possibly, ongoing, work of the client.
     * @return connection pool associated with this client
     */
    public ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    /**
     * Get {@link HttpCache} used by this client. Changing parameters of the returned cache will impact future
     * and, possibly, ongoing, transactions of the client.
     * @return
     */
    public HttpCache getCache() {
        return cache;
    }

    /**
     * Create a new transaction with parameters of this client. Returned transaction can be customized further to
     * suit specific needs. This step does not open the connection to server.
     * @return new {@link HttpTransaction} instance
     */
    public HttpTransaction newTransaction() {
        return new HttpTransaction(connectionPool).useCache(cache).useCachingPolicy(cachingPolicy).setHttpVersion(httpVersion);
    }
}
