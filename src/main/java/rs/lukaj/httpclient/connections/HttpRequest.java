package rs.lukaj.httpclient.connections;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/**
 * Represents a HTTP request. Knows what to do with addresses, request methods and headers.
 * It is up to the user to write request body, if desirable.
 */
public class HttpRequest {

    private Http.Version httpVersion = Http.Version.HTTP11;
    private Http.Verb httpVerb = null;
    private RequestHeaders headers = RequestHeaders.createDefault();
    private URL target = null;
    private boolean targetAny = false; //used for request where path isn't important, sends asterisk instead of path
    private boolean setHostHeader = true;

    private HttpRequest() {
    }

    /**
     * Create a new HTTP request using a given request method.
     * @param method request method ("http verb")
     * @param target address to where should the request go (URL)
     * @return new HTTP request
     * @throws MalformedURLException if target is invalid
     */
    public static HttpRequest create(Http.Verb method, String target) throws MalformedURLException {
        HttpRequest request = new HttpRequest();
        request.httpVerb = method;
        request.target = new URL(target);
        request.headers.setHost(request.target.getHost());
        return request;
    }

    /**
     * Unset if path should be read from target URL. Set if path should be '*', denoting "Any" (used for, e.g.
     * OPTIONS request)
     * @param targetAny whether request path should be any
     * @return this, to allow chaining
     */
    public HttpRequest setTargetAny(boolean targetAny) {
        this.targetAny = targetAny;
        return this;
    }

    /**
     * Set HTTP version to be used.
     * @param version protocol version over which this request is made
     * @return this, to allow chaining
     */
    public HttpRequest setHttpVersion(Http.Version version) {
        this.httpVersion = version;
        return this;
    }

    /**
     * Set request headers. If null, sets empty headers (unless {@link #setHostHeader} is also set and
     * {@link #targetAny} is unset, in which case Host header is set).
     * @param headers headers to be used with this request or null for empty headers
     * @return this, to allow chaining
     */
    public HttpRequest setHeaders(RequestHeaders headers) {
        this.headers = headers;
        if(headers == null) headers = RequestHeaders.createEmpty();
        if(setHostHeader && !targetAny) headers.setHost(target.getHost());
        return this;
    }

    /**
     * Set if this request should insert host header when making the request.
     * @param setHostHeader whether appropriate host header should be inserted
     * @return this, to allow chaining
     */
    public HttpRequest setHostHeader(boolean setHostHeader) {
        this.setHostHeader = setHostHeader;
        return this;
    }

    /**
     * @return HTTP version used by this request
     */
    public Http.Version getHttpVersion() {
        return httpVersion;
    }

    /**
     * @return headers used for this request
     */
    public RequestHeaders getHeaders() {
        return headers;
    }

    /**
     * @return Whether this request can be cached
     */
    public boolean isCacheable() { //this is quite primitive, but I'm only using cache if I get http 304
        return httpVerb.isResponseCacheable();
    }

    private void verifyRequest() {
        if(setHostHeader && !headers.hasHeader("Host")) headers.setHost(target.getHost());
        if(httpVerb.mustProvideRequestBody()
                && (!headers.hasHeader("Content-Length") || !headers.hasHeader("Content-Type"))) {
            throw new InvalidRequestException("Must provide body, but content length or type not set!");
        }
        if(!httpVerb.canProvideRequestBody()
                && (headers.hasHeader("Content-Length") || headers.hasHeader("Content-Type"))) {
            throw new InvalidRequestException("Can't provide body, but has set content length or content type!");
        }
        if(!httpVerb.isSupported()) {
            System.err.println("Warning: using non-supported http method (might fail unpredictably)");
        }
        if(!httpVersion.isSupported()) {
            System.err.println("Warning: using non-supported http version (might fail unpredictably)");
        }
    }

    private void setupConnection(HttpSocket conn) throws IOException {
        conn.print(httpVerb + " " + (targetAny ? "*" : target.getFile()) + " " + httpVersion + "\r\n");
        conn.print(headers.toString());
        conn.print("\r\n");
        conn.flush();
    }

    /**
     * Establishes connection to server in a blocking fashion, on this thread. Timeout is defined by
     * {@link ConfigurableConnectionPool.Config} as per the requirements. It might fail with TimeoutException if timeout is reached.
     * You can assume headers are sent, and use the returned connection to send request body and read the response.
     * @param connections connection pool used for obtaining a connection
     * @return connection used to communicate with the server
     * @throws IOException if URL is bad (e.g. using neither http:// and port number), host is unknown, or other I/O
     *                      operation fails
     * @throws TimeoutException if timeout occurs due to no available connections
     */
    public HttpSocket connectNow(ConnectionPool connections) throws IOException, TimeoutException {
        verifyRequest();
        Endpoint endpoint = Endpoint.fromUrl(target);
        HttpSocket conn = connections.getConnectionBlocking(endpoint);
        setupConnection(conn);
        return conn;
    }

    /**
     * Establishes connection to server in a blocking fashion, on this thread. It uses given socket for the request.
     * You can assume headers are sent, and use the returned connection to send request body and read the response.
     * @param socket socket over which data is passed
     * @return connection used to communicate with the server
     */
    public HttpSocket connectNow(HttpSocket socket) throws IOException {
        verifyRequest();
        setupConnection(socket);
        return socket;
    }

    /**
     * Establishes connection to server in a async fashion, on given executor. Timeout is defined by
     * {@link ConfigurableConnectionPool.Config} as per the requirements. In
     * {@link ConnectionPool.Callbacks#onConnectionObtained(HttpSocket)}, you can assume headers
     * are sent and use passed {@link HttpSocket} to write method body and read the response.
     * @param connections connection pool used for obtaining a connection
     * @param callbacks callbacks which are called when request is complete
     * @param executor executor on which callbacks are executed
     * @throws MalformedURLException if URL is bad (e.g. using neither http:// and port number)
     * @throws UnknownHostException if there's an error resolving hostname
     */
    public void connectLater(ConnectionPool connections, ConnectionPool.Callbacks callbacks, Executor executor)
            throws MalformedURLException, UnknownHostException {
        verifyRequest();
        Endpoint endpoint = Endpoint.fromUrl(target);
        connections.getConnectionAsync(endpoint, new ConnectionPool.Callbacks() {
            @Override
            public void onConnectionObtained(HttpSocket connection) {
                executor.execute(() -> {
                    try {
                        setupConnection(connection);
                    } catch (IOException e) {
                        onExceptionThrown(e);
                    }
                    callbacks.onConnectionObtained(connection);
                });
            }

            @Override
            public void onTimeout() {
                executor.execute(callbacks::onTimeout);
            }

            @Override
            public void onExceptionThrown(IOException ex) {
                executor.execute(() -> callbacks.onExceptionThrown(ex));
            }
        });
    }


    /**
     * Requests are equal if they use same HTTP version, same request method, same headers and same target.
     * @inheritDoc
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HttpRequest request = (HttpRequest) o;
        boolean maybeEqual = httpVersion == request.httpVersion &&
                httpVerb == request.httpVerb &&
                Objects.equals(headers, request.headers);
        if(!maybeEqual) return false;
        if(!targetAny) {
            return target.equals(request.target);
        } else { //if we're using targetAny, we don't care about the file portion of URL (but do care about the rest)
            URL oth = request.target;
            return oth.getProtocol().equals(target.getProtocol())
                    && oth.getHost().equals(target.getHost())
                    && oth.getPort() == target.getPort();
        }
    }

    @Override
    public int hashCode() {
        if(!targetAny)
            return Objects.hash(httpVersion, httpVerb, headers, target);
        else
            return Objects.hash(httpVersion, httpVerb, headers); //this is quite bad hash
    }
}
