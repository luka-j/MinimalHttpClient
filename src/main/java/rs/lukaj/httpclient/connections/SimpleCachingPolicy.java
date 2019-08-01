package rs.lukaj.httpclient.connections;

/**
 * The simplest non-trivial caching policy: cache each request that is potentially cacheable,
 * look in cache only if server returns 304.
 */
public class SimpleCachingPolicy implements CachingPolicy {
    @Override
    public boolean shouldStoreInCache(HttpRequest request, HttpResponse response) {
        return request.isCacheable();
    }

    @Override
    public boolean shouldLookInCache(HttpRequest request) {
        return false;
    }

    @Override
    public boolean shouldLookInCache(HttpRequest request, HttpResponse response) {
        if(response == null) return shouldLookInCache(request);
        return response.getStatus().getCode() == HttpResponse.Code.NOT_MODIFIED;
    }
}
