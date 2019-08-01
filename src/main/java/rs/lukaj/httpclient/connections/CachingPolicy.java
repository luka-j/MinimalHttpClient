package rs.lukaj.httpclient.connections;

/**
 * Defines a caching policy for requests. Methods provided in this interface are used by transactions
 * to figure out whether something is 1) worth looking for in cache and 2) should be stored in cache.
 */
public interface CachingPolicy {
    /**
     * Should the request and response be stored in cache. If true, caller should make sure it actually
     * reaches the cache. If false, it should be commited to cache.
     * @param request HttpRequest
     * @param response HttpResponse got by sending the passed request
     * @return whether request/response should be in cache
     */
    boolean shouldStoreInCache(HttpRequest request, HttpResponse response);

    /**
     * Should caller check value in cache before making the request. If true, caller shouldn't make a
     * network request, and rather retrieve the response data from cache.
     * @param request HttpRequest which may be present in cache
     * @return whether request may be in cache
     */
    boolean shouldLookInCache(HttpRequest request);

    /**
     * Should caller replace an already obtained response with the one from cache. This is useful for
     * e.g. 304 Not Modified response, when it is expected for client to pull the data from its own
     * cache instead of expecting the server to send the resource.
     * @param request HttpRequest used for obtaining the response
     * @param response response which may be replaced by the one from cache
     * @return whether the response should be replaced by the one stored in cache
     */
    boolean shouldLookInCache(HttpRequest request, HttpResponse response);
}
