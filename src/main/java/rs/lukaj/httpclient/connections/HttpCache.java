package rs.lukaj.httpclient.connections;

import java.io.File;
import java.time.Duration;

/**
 * Common interface for implementing cache used by {@link HttpTransaction}.
 */
public interface HttpCache {
    /**
     * Cleans up a cache and checks if given request exists in cache. If this function returns true,
     * it is guranteed that the request won't become stale for at least a short period afterwards.
     * @param request request to look for in cache
     * @return whether the requests exists in cache
     */
    boolean exists(HttpRequest request);

    /**
     * Removes the given request from cache, if it exists.
     * @param request request to be removed
     */
    void evict(HttpRequest request);

    /**
     * Puts response status in cache. If this request already exists in cache, this status will be
     * associated with it. If you're putting a new response, be sure to call both
     * {@link #putHeaders(HttpRequest, ResponseHeaders)} and {@link #putString(HttpRequest, String)} or
     * {@link #putFile(HttpRequest, File)} to fill in all the data or {@link #evict(HttpRequest)} the
     * request from cache.
     * @param request request to be associated with this response status
     * @param status response status from server
     */
    void putStatus(HttpRequest request, HttpResponse.Status status);

    /**
     * Puts response headers in cache. If this request already exists in cache, these headers will be
     * associated with it. If you're putting a new response, be sure to call both
     * {@link #putStatus(HttpRequest, HttpResponse.Status)} and {@link #putString(HttpRequest, String)} or
     * {@link #putFile(HttpRequest, File)} to fill in all the data or {@link #evict(HttpRequest)} the
     * request from cache.
     * @param request request to be associated with this response status
     * @param headers response headers from server
     */
    void putHeaders(HttpRequest request, ResponseHeaders headers);

    /**
     * Puts response body in cache. If this request already exists in cache, this body will be
     * associated with it. If you're putting a new response, be sure to call both
     * {@link #putStatus(HttpRequest, HttpResponse.Status)} and {@link #putHeaders(HttpRequest, ResponseHeaders)}
     * to fill in all the data or {@link #evict(HttpRequest)} the request from cache.
     * @param request request to be associated with this response status
     * @param body response body from server
     */
    void putString(HttpRequest request, String body);

    /**
     * Puts response a reference to file where response body of this request is stored in cache. If this
     * request already exists in cache, this body will be associated with it. If you're putting a new
     * response, be sure to call both {@link #putStatus(HttpRequest, HttpResponse.Status)} and
     * {@link #putHeaders(HttpRequest, ResponseHeaders)} to fill in all the data or {@link #evict(HttpRequest)}
     * the request from cache.
     * @param request request to be associated with this response status
     * @param body response body from server
     */
    void putFile(HttpRequest request, File body);

    /**
     * @param request request that may be in this cache
     * @return response status associated with this request or null if it doesn't exist
     */
    HttpResponse.Status getStatus(HttpRequest request);

    /**
     * @param request request that may be in this cache
     * @return response headers associated with this request or null if it doesn't exist
     */
    ResponseHeaders getHeaders(HttpRequest request);

    /**
     * @param request request that may be in this cache
     * @return body of the response or null if it doesn't exist. If you don't know the type,
     * use in conjunction with {@link #getType(HttpRequest)}
     */
    Object getBody(HttpRequest request);

    /**
     * @param request request that may be in this cache
     * @return type of response body or null if it doesn't exist
     */
    Class getType(HttpRequest request);

    /**
     * @param request request that may be in this cache
     * @return age of the response in cache, i.e. for how long the request has been in cache so far
     */
    Duration getAge(HttpRequest request);


    //if you're reading this, know that this interface is faulty in one significant way,
    //but since this is homework, I'm leaving it this way for now
    //(otoh there's nothing wrong with implementation afaik)
    //I mean, besides the fact status, headers and body are stored separately instead of
    //as a part of one object, that's just annoying

    /**
     * An empty cache. It doesn't store anything, returns null on all {@code get*} methods and
     * {@link #exists(HttpRequest)} always returns false.
     */
    class Empty implements HttpCache {

        @Override
        public boolean exists(HttpRequest request) {
            return false;
        }

        @Override
        public void evict(HttpRequest request) {
        }

        @Override
        public void putStatus(HttpRequest request, HttpResponse.Status status) {
        }

        @Override
        public void putHeaders(HttpRequest request, ResponseHeaders headers) {
        }

        @Override
        public void putString(HttpRequest request, String body) {
        }

        @Override
        public void putFile(HttpRequest request, File body) {
        }

        @Override
        public HttpResponse.Status getStatus(HttpRequest request) {
            return null;
        }

        @Override
        public ResponseHeaders getHeaders(HttpRequest request) {
            return null;
        }

        @Override
        public Object getBody(HttpRequest request) {
            return null;
        }

        @Override
        public Class getType(HttpRequest request) {
            return null;
        }

        @Override
        public Duration getAge(HttpRequest request) {
            return null;
        }
    }
}
