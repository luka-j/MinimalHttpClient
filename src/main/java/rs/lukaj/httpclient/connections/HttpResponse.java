package rs.lukaj.httpclient.connections;

import rs.lukaj.httpclient.Utils;

import java.io.*;
import java.util.concurrent.Executor;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Represents a HTTP response. Parsing headers and body is done here.
 */
public class HttpResponse {
    /**
     * HTTP response codes and their attributes.
     */
    public enum Code {
        //ignore
        CONTINUE            (100, false),
        SWITCHING_PROTOCOLS (101, false),
        //ok
        OK                  (200),
        CREATED             (201),
        ACCEPTED            (202),
        NONAUTHORITATIVE    (203),
        NOCONTENT           (204, false),
        RESET_CONTENT       (205),
        //redirects (and NOT_MODIFIED) - HttpClient should perform new request to location given in Location header
        MULTIPLE_CHOICES    (300),
        MOVED_PERMANENTLY   (301),
        FOUND               (302),
        SEE_OTHER           (303),
        NOT_MODIFIED        (304, false),
        USE_PROXY           (305),
        TEMP_REDIRECT       (306),
        //client errors
        BAD_REQUEST         (400),
        UNAUTHORIZED        (401),
        FORBIDDEN           (403),
        NOT_FOUND           (404),
        METHOD_NOT_ALLOWED  (405), //use headers to figure out which method is allowed
        NOT_ACCEPTABLE      (406),
        PROXY_AUTH_REQUIRED (407),
        REQUEST_TIMEOUT     (408),
        CONFLICT            (409),
        GONE                (410),
        LENGTH_REQUIRED     (411), //content length header not present
        PRECONDITION_FAILED (412),
        ENTITY_TOO_LARGE    (413),
        URI_TOO_LONG        (414),
        UNSUPPORTED_MEDIA   (415),
        RANGE_NOTSATISFIABLE(416),
        EXPECTATION_FAILED  (417),
        TOO_MANY_REQUESTS   (429),
        //server errors
        SERVER_ERROR        (500),
        BAD_GATEWAY         (502),
        SERVER_DOWN         (503),
        GATEWAY_TIMEOUT     (504),
        UNSUPPORTED_VERSION (505),
        SERVER_UNREACHABLE  (521),

        UNKNOWN(1001);

        public int code;
        public int codeClass;
        public boolean expectBody;
        Code(int code) {
            this(code, true);
        }

        Code(int code, boolean expectBody) {
            this.expectBody = expectBody;
            this.code = code;
            this.codeClass = code - code%100;
        }

        /**
         * Determines whether a response with this code has body.
         * @return true if body may be present, false otherwise
         */
        public boolean hasBody() {
            return expectBody && codeClass != 100;
        }

        /**
         * Determines whether response with this code is an error. If it is, response body represents an error message.
         * Otherwise, response body represents contents. Error codes include, but are not limited to, client and
         * server errors.
         * @return true if it's error code, false otherwise
         */
        public boolean isError() {
            return codeClass >= 400; //we're treating unknown as error as well
        }

        /**
         * Determines whether response with this code signals client error. Client errors can be corrected by e.g.
         * changing request body or setting appropriate headers.
         * @return true if it's client error, false otherwise.
         */
        public boolean isClientError() {
            return codeClass == 400;
        }

        /**
         * Determines whether response with this code signals server error. Server errors usually cannot be corrected
         * by the client.
         * @return true if it's server error, false otherwise.
         */
        public boolean isServerError() {
            return codeClass == 500;
        }

        @Override
        public String toString() {
            return code + name();
        }
    }

    private HttpSocket socket;
    private HttpRequest request;
    private HttpCache cache = new HttpCache.Empty();
    private CachingPolicy cachingPolicy = new SimpleCachingPolicy();
    private Status status;
    private ResponseHeaders headers;
    private int maxAllowedInformativeResponses = 5;
    private boolean throwIfInformativeResponse = false;
    private boolean allowInvalidHttpVersion = true;
    private int fileBufferSize = 51_200;

    private boolean parsed = false;
    private Object body;
    private Class type;

    private HttpResponse() {
    }

    /**
     * Wrap a HTTP response and disable reading from server.
     * @param status status line data
     * @param headers response headers
     * @param body body of the request
     * @param type type of the body (usually String or File)
     * @return new response
     */
    protected static HttpResponse wrap(Status status, ResponseHeaders headers, Object body, Class type) {
        HttpResponse response = new HttpResponse();
        response.status = status;
        response.headers = headers;
        response.body = body;
        response.type = type;
        response.parsed = true;
        return response;
    }

    /**
     * Create a new HTTP response, reading from the given socket. This response should be the result of the
     * passed HttpRequest (i.e. request has been previously sent over the same socket).
     * @param socket socket used for reading the data
     * @param request request used for getting this response
     * @return new response
     */
    public static HttpResponse from(HttpSocket socket, HttpRequest request) {
        HttpResponse response = new HttpResponse();
        response.socket = socket;
        response.request = request;
        return response;
    }

    /**
     * Set how many informative responses will be ignored. Set to 0 if you want to receive all informative responses.
     * @param maxAllowedInformativeResponses maximum number of informative (100-class) responses which will be ignored
     * @return this, to allow chaining
     */
    public HttpResponse setMaxAllowedInformativeResponses(int maxAllowedInformativeResponses) {
        this.maxAllowedInformativeResponses = maxAllowedInformativeResponses;
        return this;
    }

    /**
     * Set if instead of returning an informative response (HTTP 1xx) you'd like {@link InvalidResponseException} to
     * be thrown. Used in conjunction with {@link #setMaxAllowedInformativeResponses(int)}.
     * @param throwIfInformativeResponse if exception should be thrown instead of returning informative response
     * @return this, to allow chaining
     */
    public HttpResponse setThrowIfInformativeResponse(boolean throwIfInformativeResponse) {
        this.throwIfInformativeResponse = throwIfInformativeResponse;
        return this;
    }

    /**
     * Should invalid HTTP version be ignored. If this is set and there's a mismatch between request HTTP version and
     * response HTTP version, {@link InvalidResponseException} is thrown. Otherwise, it's ignored
     * @param allowInvalidHttpVersion whether an exception should be thrown on HTTP version mismatch
     * @return this, to allow chaining
     */
    public HttpResponse allowInvalidHttpVersion(boolean allowInvalidHttpVersion) {
        this.allowInvalidHttpVersion = allowInvalidHttpVersion;
        return this;
    }

    /**
     * Set size for a buffer used when writing request body to file.
     * @param size size of the buffer
     * @return this, to allow chaining
     */
    public HttpResponse setResponseFileBuffer(int size) {
        this.fileBufferSize = size;
        return this;
    }

    /**
     * Set {@link HttpCache} which should be used with this response. This class does the caching of received responses.
     * @param cache cache
     * @return this, to allow chaining
     */
    public HttpResponse setCache(HttpCache cache) {
        this.cache = cache;
        return this;
    }

    /**
     * Set {@link CachingPolicy} which should be used with this response.
     * @param policy
     * @return
     */
    public HttpResponse setCachingPolicy(CachingPolicy policy) {
        this.cachingPolicy = policy;
        return this;
    }

    private void readHeaders() throws IOException {
        String line;
        while(!(line = socket.readLine()).isEmpty()) {
            headers.appendHeader(line);
        }
    }

    /**
     * Allows further parsing of status line and headers.
     */
    protected void readMore() {
        this.parsed = false;
    }
    /**
     * Parses status line and response headers, skipping informative headers (100-class). This method is idempotent —
     * first invocation parses content, but further calls have no effect. Calling {@link #readMore()} will allow further
     * reads (but previously read data won't be read again).
     * @return this response
     * @throws IOException
     */
    public HttpResponse parseResponse() throws IOException {
        if(parsed) return this;
        int infoResponses = 0;
        do {
            if(infoResponses > maxAllowedInformativeResponses) {
                if(throwIfInformativeResponse) throw new InvalidResponseException("Too many informative responses!");
                else break;
            }
            status = new Status(socket.readLine());
            if(!status.httpVersion.equals(request.getHttpVersion().toString())) {
                if(!allowInvalidHttpVersion) throw new InvalidResponseException("Invalid HTTP version: " + status.httpVersion);
                System.err.println("Warning: invalid HTTP version returned by server: " + status.httpVersion);
            }
            headers = new ResponseHeaders();
            readHeaders();
            infoResponses++;
        } while (status.responseCode/100 == 1); //informative status lines - ignored

        parsed = true;
        return this;
    }

    /**
     * Get data from Status-Line received in this response.
     * @return status line data
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Get response headers received.
     * @return received headers
     */
    public ResponseHeaders getHeaders() {
        return headers;
    }

    /**
     * Get length of body. If status code indicates there should be no body, 0 is returned. Otherwise, if Content-Length
     * header is present and it's a valid int, its value is returned. Otherwise, 0 is returned.
     * @return
     */
    public int getContentLength() {
        if(!status.getCode().hasBody()) return 0;
        String lenStr = headers.getContentLength();
        int len;
        //if there's no Content-Length header, we're treating response as having no body
        //this is not exactly per spec, because server can close the connection, signalling end
        //of response, but task guarantees server won't close the connection abruptly
        //if server sets Content-Length larger than actual response, it will stall the client
        if(lenStr == null || lenStr.isEmpty()) len = 0;
        else len = Integer.parseInt(lenStr);
        return len;
    }

    /**
     * Reads body and returns it as string. Parses Content-Encoding appropriately and does caching of the body.
     * Assumes UTF-8 encoding.
     * If this response is coming from cache and it should be a file, {@link InvalidResponseException} is thrown.
     * @return response body, parsed as string
     * @throws IOException
     */
    public String getBodyString() throws IOException {
        if(body != null) {
            if(type.equals(String.class)) return (String)body;
            else throw new InvalidResponseException("Expected String, but got " + type.getName());
        }
        if("chunked".equals(getHeaders().getTransferEncoding())) {
            System.err.println("Warning: Expecting a chunked response, but reading as String. " +
                    "Use HttpResponse#getChunks instead.");
        }
        int len = getContentLength();
        if(len == 0) {
            if(cachingPolicy.shouldStoreInCache(request, this)) cache.putString(request, "");
            return "";
        }
        else {
            byte[] data = new byte[len];
            int off = 0;
            while(len > 0) {
                int read = socket.read(data, off, len);
                len-=read;
                off+=read;
            }
            String body = new String(Utils.decompress(data, getHeaders().getContentEncoding()), UTF_8);
            if(cachingPolicy.shouldStoreInCache(request, this)) cache.putString(request, body);
            return body;
        }
    }


    /**
     * Initiates reading of chunked response. Uses {@link rs.lukaj.httpclient.connections.HttpSocket.ChunkCallbacks}
     * to inform caller when each chunk is received and if I/O exception occurs.
     * Assumes "chunked" <em>is not</em> passed as a part of Content-Encoding header (but rather in Transfer-Encoding
     * or some other header)
     * @param callbacks callbacks used to inform when chunks are read
     * @param executor executor on which callbacks are executed
     * @see HttpSocket#readChunks(HttpSocket.ChunkCallbacks, Executor)
     */
    //Spec (and reality) here is really awkward. This client uses the following interpretation:
    //For this method, we expect Transfer-Encoding to be chunked, and read Content-Encoding to figure out
    //are chunks compressed. If, for example, Content-Encoding is gzip, we un-gzip chunks _one by one_
    //and pass the uncompressed bytes to the callback
    //we're not supporting reading all the chunks at once because if they're gzipped/deflated we'd have no idea
    //where each one starts or ends (HttpSocket allows reading all at once though, so go use that)
    public void getChunks(HttpSocket.ChunkCallbacks callbacks, Executor executor) {
        //yeah, we're not doing caching here, sorry
        if(cache.exists(request)) cache.evict(request);
        socket.readChunks(new HttpSocket.ChunkCallbacks() {
            @Override
            public void onChunkReceived(byte[] chunk) {
                try {
                    callbacks.onChunkReceived(Utils.decompress(chunk, getHeaders().getContentEncoding()));
                } catch (IOException e) {
                    onExceptionThrown(e);
                }
            }

            @Override
            public void onEndTransfer() {
                try {
                    readHeaders();
                } catch (IOException e) {
                    onExceptionThrown(e);
                }
                callbacks.onEndTransfer();
            }

            @Override
            public void onExceptionThrown(IOException ex) {
                callbacks.onExceptionThrown(ex);

            }
        }, executor);
    }

    /**
     * Writes body content to file. This method ignores Content-Encoding header and writes the body as-is.
     * @param to to which file body should be written
     * @return file to which body was written
     * @throws IOException if I/O exception occurs
     */
    public File writeBodyToFile(File to) throws IOException {
        if(body != null) {
            if(type.equals(File.class)) return (File)body;
            else throw new InvalidResponseException("Expected File, but got " + type.getName());
        }
        if("chunked".equals(getHeaders().getTransferEncoding())) {
            System.err.println("Warning: Expecting a chunked response, but writing to file. " +
                    "Use HttpResponse#getChunks instead.");
        }
        int len = getContentLength();
        if(len == 0) return to;
        else {
            FileOutputStream fos = new FileOutputStream(to);
            byte[] buffer = new byte[fileBufferSize];
            //we're not supporting decompressing gzip-encoded or deflated files
            //it could be done by using ByteArrayInputStream chained to GzipInputStream/InflaterInputStream, which
            //is outputted to the FileOutputStream. But if server sends you a gzip-encoded file, I suppose you know
            //better what to do with it
            while(len > 0) {
                //bytes.reset();
                int sz = Math.min(len, fileBufferSize);
                int read = socket.read(buffer, 0, sz);
                fos.write(buffer, 0, read);
                len-=read;
            }
            if(request != null && cachingPolicy.shouldStoreInCache(request, this)) {
                cache.putFile(request, to);
            }
            return to;
        }
    }

    /**
     * Represents data contained in a Status-Line of the response. Contains HTTP version, response code and a
     * a response phrase.
     */
    public static class Status {
        public final String httpVersion;
        public final int responseCode;
        public final String responsePhrase;
        private Code response;

        public Status(String statusLine) {
            String[] tokens = statusLine.split(" ", 3);
            httpVersion = tokens[0];
            responseCode = Integer.parseInt(tokens[1]);
            if(tokens.length == 3){
                responsePhrase = tokens[2];
            }
            else {
                responsePhrase = "";
                System.err.println("Warning: HTTP status line missing response phrase");
            }
        }

        /**
         * Get {@link Code} representation of this status's {@link #responseCode}. Returned code uses one of
         * the standard phrases as an enum variable name, instead of this response's phrase.
         * @return
         */
        public Code getCode() {
            if(response == null) {
                for(Code c : Code.values()) { //this is kinda slow, but there're only a few dozen codes
                                              // (I'm counting on JVM to optimize this somehow)
                    if (c.code == responseCode) {
                        response = c;
                        break;
                    }
                }
                if(response == null) {
                    switch (responseCode/100) {
                        case 1: response = Code.CONTINUE; break;
                        case 2: response = Code.OK; break;
                        case 3: response = Code.MULTIPLE_CHOICES; break;
                        case 4: response = Code.BAD_REQUEST; break;
                        case 5: response = Code.SERVER_ERROR; break;
                        default: response = Code.UNKNOWN;
                    }
                }
            }
            return response;
        }

        /**
         * Does this code represent an error.
         * @return true if this is an error code, false otherwise.
         * @see Code#isError()
         */
        public boolean isError() {
            return getCode().isError();
        }

        @Override
        public String toString() {
            return httpVersion + " " + responseCode + " " + responsePhrase;
        }
    }
}
