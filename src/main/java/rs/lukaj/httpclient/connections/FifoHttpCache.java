package rs.lukaj.httpclient.connections;

import java.io.File;
import java.time.Duration;
import java.util.*;

/**
 * Simple FIFO cache for HTTP requests and responses. Supports a single maxAge parameter for all entries.
 */
public class FifoHttpCache implements HttpCache {

    private int size = 32;
    private long maxAge = Duration.ofMinutes(10).toMillis();

    public FifoHttpCache() {
    }
    public FifoHttpCache(int size) {
        this.size = size;
    }
    public FifoHttpCache(int size, Duration maxAge) {
        this.size = size;
        this.maxAge = maxAge.toMillis();
    }

    private Map<HttpRequest, ResponseData> cache = new HashMap<>();
    private Queue<HttpRequest> evictionQueue = new LinkedList<>();

    @Override
    public boolean exists(HttpRequest request) {
        cleanUp(50); //hacky
        return cache.containsKey(request);
    }

    @Override
    public void evict(HttpRequest request) {
        cache.remove(request);
    }

    private void put(HttpRequest request, ResponseData response) {
        cache.put(request, response);
        evictionQueue.add(request);
        while(cache.size() > size) {  //it is possible that cache doesn't contain a request from evictionQueue
                                        //(if it was previously evicted using #evict(HttpRequest))
            cache.remove(evictionQueue.poll());
        }
    }

    private void cleanUp(int offset) {
        if(evictionQueue.isEmpty()) return;
        while((System.currentTimeMillis() + offset - cache.get(evictionQueue.peek()).time >= maxAge))
            evictionQueue.poll();
    }

    private ResponseData get(HttpRequest request) {
        cleanUp(0);
        return cache.getOrDefault(request, new ResponseData());
    }

    @Override
    public void putStatus(HttpRequest request, HttpResponse.Status status) {
        if(exists(request)) cache.get(request).status = status;
        else put(request, new ResponseData(status));
    }

    @Override
    public void putHeaders(HttpRequest request, ResponseHeaders headers) {
        if(exists(request)) cache.get(request).headers = headers;
        else put(request, new ResponseData(headers));
    }

    @Override
    public void putString(HttpRequest request, String body) {
        if(exists(request)) {
            ResponseData resp = cache.get(request);
            resp.body = body;
            resp.type = body.getClass();
        }
        else put(request, new ResponseData<>(body, String.class));
    }

    @Override
    public void putFile(HttpRequest request, File body) {
        if(exists(request)) {
            ResponseData resp = cache.get(request);
            resp.body = body;
            resp.type = body.getClass();
        }
        else put(request, new ResponseData<>(body, File.class));
    }

    @Override
    public HttpResponse.Status getStatus(HttpRequest request) {
        return get(request).status;
    }
    @Override
    public ResponseHeaders getHeaders(HttpRequest request) {
        return get(request).headers;
    }
    @Override
    public Object getBody(HttpRequest request) {
        return get(request).body;
    }
    @Override
    public Class getType(HttpRequest request) {
        return get(request).type;
    }
    @Override
    public Duration getAge(HttpRequest request) {
        return get(request).getAge();
    }


    private static class ResponseData<T> {
        private HttpResponse.Status status;
        private ResponseHeaders headers;
        private T body;
        private Class<T> type;
        private long time;

        private ResponseData(HttpResponse.Status status, ResponseHeaders headers, T body, Class<T> type) {
            this();
            this.status = status;
            this.headers = headers;
            this.body = body;
            this.type = type;
        }
        private ResponseData(HttpResponse.Status status) {
            this();
            this.status = status;
        }
        private ResponseData(ResponseHeaders headers) {
            this();
            this.headers = headers;
        }
        private ResponseData(T body, Class<T> type) {
            this();
            this.body = body;
            this.type = type;
        }

        private ResponseData() {
            this.time = System.currentTimeMillis();
        }


        public HttpResponse.Status getStatus() {
            return status;
        }

        public ResponseHeaders getHeaders() {
            return headers;
        }

        public T getBody() {
            return body;
        }

        public Class<T> getType() {
            return type;
        }

        public Duration getAge() {
            return Duration.ofMillis(System.currentTimeMillis() - time);
        }
    }
}
