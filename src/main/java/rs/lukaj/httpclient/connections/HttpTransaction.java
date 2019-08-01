package rs.lukaj.httpclient.connections;

import rs.lukaj.httpclient.Utils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static rs.lukaj.httpclient.connections.HttpResponse.Code.*;

/**
 * This is used to represent a single transaction over the network, over a {@link HttpSocket} obtained from a
 * {@link ConnectionPool}. It can make multiple requests if needed (i.e. if server indicates a redirect).
 * It is illegal to use one HttpTransaction object for multiple requests.
 */
//similar role to HttpURLConnection in standard library, but more configurable
public class HttpTransaction implements Closeable {
    private ConnectionPool connectionPool;
    private HttpRequest request;
    private HttpCache cache = new FifoHttpCache();
    private HttpSocket socket;
    private HttpResponse response;
    private CachingPolicy cachingPolicy = new SimpleCachingPolicy();
    private RequestHeaders requestHeaders = RequestHeaders.createDefault();
    private Http.Version httpVersion = Http.Version.HTTP11;

    private long requestStartTime = -1;
    private volatile boolean used = false; //this really shouldn't be used from multiple threads
    private String bodyStr;
    private File bodyFile;
    private int maxRedirects = 8;
    private int maxRepeats = 3;
    private boolean throwIfMaxRepeats = false;
    private int currRedirects, currRepeats;
    private boolean disconnectOnClose = false;
    private boolean repeatOnNotModified = true;
    private boolean closed = false;

    /**
     * Create a new transaction which uses given connection pool to obtain a {@link HttpSocket}.
     * @param connectionPool connection pool used for obtaining sockets
     */
    public HttpTransaction(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    /**
     * Set headers to be used with this request. By default, transaction uses
     * {@link RequestHeaders.createDefault() default} headers.
     * @param headers request headers
     * @return this, to allow chaining
     */
    public HttpTransaction setHeaders(RequestHeaders headers) {
        this.requestHeaders = headers;
        return this;
    }

    public HttpTransaction useCache(HttpCache cache) {
        this.cache = cache;
        if(cache == null)
            this.cache = new HttpCache.Empty();
        return this;
    }

    public HttpTransaction useCachingPolicy(CachingPolicy policy) {
        this.cachingPolicy = policy;
        return this;
    }

    public HttpTransaction setHttpVersion(Http.Version version) {
        this.httpVersion = version;
        return this;
    }

    /**
     * Send string as request body. This <em>won't</em> open the connection nor write anything to server yet.
     * @param str string to send as body
     * @return this, to allow chaining
     */
    public HttpTransaction sendString(String str) {
        bodyStr = str;
        return this;
    }

    /**
     * Send data from file as request body. This <em>won't</em> open the connection nor write anything to server yet.
     * @param file file which is sent as body
     * @return this, to allow chaining
     */
    public HttpTransaction sendFile(File file) {
        bodyFile = file;
        return this;
    }

    /**
     * Set maximum number of redirects which are followed by this transaction. If server tries to redirect more than
     * maxRedirects times, exception is thrown.
     * @param maxRedirects maximum number of redirects followed
     * @return this, to allow chaining
     */
    public HttpTransaction setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    /**
     * Set maximum number of times the request is repeated. The request needs to be repeated if, e.g. server returns
     * 304, but there is no appropriate response in cache.
     * @param maxRepeats maximum number of repeats
     * @return this, to allow chaining
     */
    public HttpTransaction setMaxRepeats(int maxRepeats) {
        this.maxRepeats = maxRepeats;
        return this;
    }

    /**
     * After request has been repeated maximum times, set if exception should be thrown or the response returned.
     * @param throwIfMaxRepeats if exception should be thrown after max number of repeats has been reached
     * @return this, to allow chaining
     */
    public HttpTransaction setThrowIfMaxRepeats(boolean throwIfMaxRepeats) {
        this.throwIfMaxRepeats = throwIfMaxRepeats;
        return this;
    }

    /**
     * Should request be repeated if received HTTP 304 response from server and data is not in cache. If not,
     * the 304 response will be returned to the caller.
     * @param repeatOnNotModified should HTTP 304 not modified be repeated if necessary
     * @return this, to allow chaining
     */
    public HttpTransaction setRepeatOnNotModified(boolean repeatOnNotModified) {
        this.repeatOnNotModified = repeatOnNotModified;
        return this;
    }

    /**
     * Get headers sent with this request. Modifying the headers will have impact on the request.
     * @return headers used with this request
     */
    public RequestHeaders getHeaders() {
        return requestHeaders;
    }

    private void ensureOpen() {
        if(closed) throw new IllegalStateException("Cannot use closed transaction!");
        if(used) throw new IllegalStateException("Transaction has already been finished!");
    }

    //close the socket if Connection header is set to close; keep it alive otherwise (default for HTTP/1.1)
    private void setShouldDisconnect() {
        if(response.getHeaders().hasHeader("Connection"))
            disconnectOnClose = "close".equals(response.getHeaders().getHeader("Connection").toLowerCase());
    }

    /**
     * Make a new request on this thread. Waiting for a connection from the connection pool blocks the calling thread.
     * This method sends data over the network and returns the HttpResponse. Use the returned result to obtain
     * response body.
     * @param method http method
     * @param target url to which the request should be made
     * @return response from the server
     * @throws IOException if I/O exception occurs during transfer
     * @throws TimeoutException if waiting for connection from connection pool times out
     * @throws InvalidRequestException if exception or invalid state occurs while creating the request
     * @throws InvalidResponseException if response is something unexpected and/or breaks the HTTP spec
     */
    public HttpResponse makeRequest(Http.Verb method, String target) throws IOException, TimeoutException,
            InvalidRequestException, InvalidResponseException{
        ensureOpen();
        used = true;
        currRedirects = 0;
        currRepeats = 0;
        return makeRequest(method, target, null);
    }

    /**
     * Make a new request and send body in chunks, according to HTTP/1.1 spec. Use the returned {@link ChunkSender}
     * to begin transfer, send data and end transfer. All headers are sent before the data; sending trailers
     * is not supported.
     * @param method http method
     * @param target url to which the request should be made
     * @return interface to use for sending data
     */
    //this isn't supported in client.Network, and never will be; too cumbersome
    public ChunkSender sendChunks(Http.Verb method, String target) {
        ensureOpen();
        return new ChunkSender() {
            @Override
            public void begin() throws IOException, TimeoutException {
                verifyRequest();
                requestHeaders.setTransferEncoding("chunked");
                request = HttpRequest.create(method, target)
                        .setHeaders(requestHeaders)
                        .setHttpVersion(httpVersion);
                socket = request.connectNow(connectionPool);
            }

            @Override
            public void sendChunk(byte[] chunk) throws IOException {
                byte[] compressed = chunk.length == 0 ? chunk : Utils.compress(chunk, getHeaders().get("Content-Encoding"));
                socket.write((Integer.toHexString(compressed.length) + "\r\n").getBytes(UTF_8));
                socket.write(compressed);
                socket.write("\r\n".getBytes(UTF_8));
            }

            @Override
            public HttpResponse end() throws IOException {
                sendChunk(new byte[0]);
                HttpResponse response = HttpResponse.from(socket, request)
                        .setCache(cache)
                        .setCachingPolicy(cachingPolicy)
                        .parseResponse();
                //we're skipping the usual repeating-request-handholding and that stuff
                close();
                setShouldDisconnect();
                return response;
            }
        };
    }

    /**
     * Make a new request on the passed executor. Waiting for a connection from the connection pool is done on the
     * background thread. This method sends data over the network. It returns immediately after verifying the
     * request is in valid state. Use the callbacks to parse the response.
     * @param method http method
     * @param target url to which request is made
     * @param callbacks callback to call when getting a response
     * @param executor executor on which response reading and parsing is done
     */
    public void makeRequestLater(Http.Verb method, String target, Callbacks callbacks, Executor executor) {
        ensureOpen();
        used = true;
        try { verifyRequest(); } catch (Throwable t) { callbacks.onExceptionThrown(t); }
        try {
            byte[] body = makeBody(method, target);
            if(requestStartTime == -1) requestStartTime = System.currentTimeMillis();
            request.connectLater(connectionPool, new ConnectionPool.Callbacks() {
                @Override
                public void onConnectionObtained(HttpSocket connection) {
                    socket = connection;
                    if(body != null) {
                        try {
                            socket.write(body);

                            response = HttpResponse.from(socket, request).setCache(cache)
                                    .setCachingPolicy(cachingPolicy).parseResponse();
                            int responseCode = response.getStatus().responseCode;
                            if(responseCode == MOVED_PERMANENTLY.code || responseCode == FOUND.code || responseCode == SEE_OTHER.code
                                    || responseCode == TEMP_REDIRECT.code) {
                                String redirectUrl = getRedirectUrl();
                                response.getBodyString();
                                if(redirectUrl.startsWith("/")) { //fixme I don't yet know what, but something _is_ wrong here
                                    String[] proto = target.split("://", 2);
                                    redirectUrl = proto[0] + "://" + proto[1].split("/", 2)[0] + redirectUrl;
                                }
                                HttpRequest.create(method, redirectUrl).connectNow(connection);
                                //we're not waiting for ConnectionPool here, so not counting that time
                                onConnectionObtained(connection);
                            }
                            if(responseCode == NOT_MODIFIED.code) {
                                HttpResponse response = getCachedResponse();
                                if(response != null) callbacks.onResponse(response);
                                else if(repeatOnNotModified) {
                                    if((response = prepareRepeat()) != null) callbacks.onResponse(response);
                                    HttpSocket newConnection = HttpRequest.create(method, target).connectNow(connection);
                                    onConnectionObtained(newConnection);
                                }
                            }
                            setShouldDisconnect();
                            cache();
                            //some other cases which require special handling... ?
                            callbacks.onResponse(response);
                        } catch (Throwable e) {
                            callbacks.onExceptionThrown(e);
                        }
                    }
                }

                @Override
                public void onTimeout() {
                    callbacks.onTimeout();
                }

                @Override
                public void onExceptionThrown(IOException ex) {
                    callbacks.onExceptionThrown(ex);
                }
            }, executor);
        } catch (IOException e) {
            executor.execute(() -> callbacks.onExceptionThrown(e));
        }

    }

    //worst case of code duplication here afaik
    //ugh
    private HttpResponse makeRequest(Http.Verb method, String target, HttpSocket socket) throws IOException,
            TimeoutException, InvalidRequestException, InvalidResponseException {
        verifyRequest();
        byte[] body = makeBody(method, target);
        if(socket == null)
            socket = request.connectNow(connectionPool);
        else
            socket = request.connectNow(socket);
        this.socket = socket;
        if(body != null) socket.write(body);
        HttpResponse cached = null;
        if(cachingPolicy.shouldLookInCache(request)) cached = getCachedResponse();
        if(cached != null) {
            response = cached;
        } else {
            response = HttpResponse.from(socket, request).setCache(cache).setCachingPolicy(cachingPolicy).parseResponse();
        }

        int responseCode = response.getStatus().responseCode;
        if(responseCode == MOVED_PERMANENTLY.code || responseCode == FOUND.code || responseCode == SEE_OTHER.code
                || responseCode == TEMP_REDIRECT.code) {
            String redirectUrl = getRedirectUrl();
            response.getBodyString();
            if(redirectUrl.startsWith("/")) { //fixme I don't yet know what, but something _is_ wrong here
                String[] proto = target.split("://", 2);
                redirectUrl = proto[0] + "://" + proto[1].split("/", 2)[0] + redirectUrl;
                return makeRequest(method, redirectUrl, socket); //we're using currently opened socket to redirect
            }
            //we need a new socket for redirect; this may fail if waiting for a new socket takes too long
            //or if server refuses multiple connections from same client and redirects to itself using absolute address
            return makeRequest(method, redirectUrl, null);
        }
        if(cachingPolicy.shouldLookInCache(request, response) && responseCode == NOT_MODIFIED.code) {
            //this is kinda ugly because we're treating NOT_MODIFIED super-specially, repeating the request without
            //usual headers to get a fresh copy
            HttpResponse response = getCachedResponse();
            if(response != null) return response;
            else if(repeatOnNotModified) {
                if((response = prepareRepeat()) != null) return response;
                return makeRequest(method, target, socket);
                //we're repeating request on the same socket; this will fail if server decides to close the connection
            }
        }
        setShouldDisconnect();
        cache();
        //some other cases which require special handling... ?
        return response;
    }


    private void verifyRequest() {
        if(bodyStr != null && bodyFile != null) {
            throw new InvalidRequestException("Cannot send both String and File!");
        }
        if(bodyFile != null && !bodyFile.exists()) throw new InvalidRequestException("File doesn't exist!");
    }

    private byte[] makeBody(Http.Verb method, String target) throws IOException {
        byte[] body = null;
        if(bodyStr != null) body = Utils.compress(bodyStr.getBytes(UTF_8), getHeaders().getHeader("Content-Encoding"));
        if(bodyFile != null) body = Files.readAllBytes(bodyFile.toPath());
        if(body != null) requestHeaders.setContentLength(body.length);
        else requestHeaders.setContentLength(0);
        request = HttpRequest.create(method, target)
                .setHeaders(requestHeaders)
                .setHttpVersion(httpVersion);
        return body;
    }

    private void cache() {
        if(cachingPolicy.shouldStoreInCache(request, response)) {
            cache.putStatus(request, response.getStatus());
            cache.putHeaders(request, response.getHeaders());
        }
    }

    private HttpResponse getCachedResponse() {
        if(cache.exists(request)) {
            response = HttpResponse.wrap(cache.getStatus(request), cache.getHeaders(request), cache.getBody(request), cache.getType(request));
            return response;
        }
        return null;
    }

    private String getRedirectUrl() {
        currRedirects++;
        if (currRedirects >= maxRedirects) throw new InvalidResponseException("Too many redirects");
        return response.getHeaders().getLocation();
    }

    private HttpResponse prepareRepeat() {
        currRepeats++;
        if(currRepeats >= maxRepeats) {
            if(throwIfMaxRepeats) throw new InvalidResponseException("Too many repeated requests");
            else return response;
        }
        requestHeaders.removeHeader("If-Modified-Since");
        requestHeaders.removeHeader("If-None-Match");
        requestHeaders.removeHeader("If-Unmodified-Since");
        return null;
    }


    /**
     * Closes the transaction, signalling the transaction is over. This releases the underlying socket, but  does
     * not necessarily close it (unless server signalled to do so). After closing the transaction, the socket can
     * be used for other transactions.
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        closed = true;
        if(socket != null) {
            if (disconnectOnClose) socket.close();
            else socket.release();
        } else {
            System.err.println("Warning: closing transaction over nonexistent socket (socket == null)");
        }
    }

    /**
     * Callbacks which are used to manage asynchronous transactions.
     */
    public interface Callbacks {
        /**
         * Signals that request is finished and passes the response.
         * @param response response from the server
         */
        void onResponse(HttpResponse response);

        /**
         * Signals that timeout has occurred, possibly while waiting for connection from the connection pool.
         */
        void onTimeout();

        /**
         * Called when any exception is thrown. Request won't proceed.
         * @param t thrown exception
         */
        void onExceptionThrown(Throwable t);
    }
}
