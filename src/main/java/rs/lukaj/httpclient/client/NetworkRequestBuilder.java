package rs.lukaj.httpclient.client;

import rs.lukaj.httpclient.connections.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

//
// === Note: I wrote this a while ago for one of my Android projects, later repurposed it into a library, ===
// === and now adapted it to use classes from connections package. Some docs might not make sense.        ===
//
/**
 * Builds a network request. Requests are always executed on the background thread - otherwise, an exception
 * by the Android would've been thrown. It is usually a good idea to set custom id and auth, though this
 * interface doesn't require it (server, on the other hand, might refuse to serve a request without proper
 * Authorization).
 * <br/>
 * This provides a way to do both asynchronous (in which case callbacks are needed) and blocking requests.
 * Avoid making blocking requests on UI thread at all costs, as such could cause UI freezing.
 * <br/>
 * All methods allow chaining.
 * Created by luka on 4.8.17.
 */
public class NetworkRequestBuilder<Send, Receive> {
    private static final ExecutorService    executor    = Executors.newCachedThreadPool(); //executor for background execution of requests

    public static final Map<String, String> emptyMap    = Collections.emptyMap();
    public static final int                 DEFAULT_ID  = -1;


    private String url;
    private Http.Verb verb;
    private AuthTokenManager tokens;

    private int requestId = DEFAULT_ID;
    private ExecutorService executeOn = executor;
    private Map<String, String> form;
    private String sendString;
    private File sendFile, receiveFile;
    private HttpSocket.ChunkCallbacks chunkCallbacks;
    private HttpClient client;
    private RequestHeaders headers = RequestHeaders.createDefault();
    private NetworkExceptionHandler exceptionHandler;

    public NetworkRequestBuilder(HttpClient client, Http.Verb verb, String url) {
        this.client = client;
        this.url = url;
        this.verb = verb;
    }

    public NetworkRequestBuilder<Send, Receive> sendForm(Map<String, String> data) {
        this.form = data;
        return this;
    }

    public NetworkRequestBuilder<Send, Receive> sendFile(File file) {
        this.sendFile = file;
        return this;
    }

    public NetworkRequestBuilder<Send, Receive> sendString(String str) {
        this.sendString = str;
        return this;
    }

    public NetworkRequestBuilder<Send, Receive> receiveFile(File file) {
        this.receiveFile = file;
        return this;
    }

    public NetworkRequestBuilder<Send, Receive> receiveChunks(HttpSocket.ChunkCallbacks callbacks) {
        this.chunkCallbacks = callbacks;
        return this;
    }

    /**
     * Sets id for this request, which will be passed to {@link rs.lukaj.httpclient.client.Network.NetworkCallbacks}.
     * Should be positive.
     */
    public NetworkRequestBuilder<Send, Receive> setId(int id) {
        this.requestId = id;
        return this;
    }

    /**
     * Attaches {@link NetworkExceptionHandler} to the (future) request. If handler is
     * present after making the request, and response isn't normal (i.e.
     * {@link Network.Response#isError()} returns true, handler is called to resolve
     * the error. This applies both to async and blocking requests.
     * @param exceptionHandler handler used to handle possible errors
     * @return this
     */
    public NetworkRequestBuilder<Send, Receive> setHandler(NetworkExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    /**
     * Sets source for retrieving and potential changing of auth token.
     * If none is specified, no Authorization header is sent.
     */
    public NetworkRequestBuilder<Send, Receive> setAuth(AuthTokenManager tokens) {
        this.tokens = tokens;
        return this;
    }

    /**
     * Provides specific executor on which this request should be made. Use if you already have an executor,
     * and/or wish to make requests in succession with some single-threaded executor. By default, this method
     * has a static cached thread pool executor on which requests are run.
     * @param executor custom executor on which to execute this request
     * @return this
     */
    public NetworkRequestBuilder<Send, Receive> setExecutor(ExecutorService executor) {
        this.executeOn = executor;
        return this;
    }

    /**
     * Set {@link RequestHeaders} to use with this request. Overwrites previously set headers.
     * @param headers headers
     */
    public NetworkRequestBuilder<Send, Receive> setHeaders(RequestHeaders headers) {
        this.headers = headers;
        return this;
    }

    /**
     * Adds a header to send to the host. Depending on {@link RequestHeaders} options, using nonstandard or
     * unknown headers may produce warning messages or {@link rs.lukaj.httpclient.connections.InvalidHeaderException}.
     * @param header header name
     * @param value header value
     * @return this
     */
    public NetworkRequestBuilder<Send, Receive> addHeader(String header, String value) {
        headers.setHeader(header, value);
        return this;
    }

    private void verifyRequest() {
        if(form == null && sendFile == null && sendString == null) sendString = "";
        int requestTypes = 0;
        if(form != null)  requestTypes++;
        if(sendFile != null) requestTypes++;
        if(sendString != null) requestTypes++;
        if(chunkCallbacks != null) requestTypes++;
        if(requestTypes != 1) throw new InvalidRequestException("Too many request options set! Set either form, string or file to send");
    }

    @SuppressWarnings("unchecked") //stupid generics
    private Future<Network.Response<Receive>> makeRequestTask(Network.NetworkCallbacks<Receive> callbacks) {
        return executeOn.submit(new Network.Request<>(requestId, client, headers, url, tokens, verb, null, callbacks) {
            @Override
            protected Receive getData(HttpResponse response) throws IOException {
                if(receiveFile != null) return (Receive)getFile(receiveFile, response);
                else if(chunkCallbacks != null) {getChunks(chunkCallbacks, executor, response); return null;}
                else return (Receive)getString(response);
            }

            @Override
            protected void uploadData(HttpTransaction connection) throws IOException {
                if(form != null) sendForm(form, connection);
                else if(sendString != null) sendString(sendString, connection);
                else if(sendFile != null) sendFile(sendFile, connection);
            }
        });
    }

    /**
     * Executes this request asynchronously and notifies callbacks of the outcome
     * @param callbacks callbacks which should be notified
     */
    @SuppressWarnings("unchecked")
    public void async(Network.NetworkCallbacks<Receive> callbacks, Duration timeout) {
        verifyRequest();
        if(exceptionHandler != null) callbacks = extendCallbacksWithHandler(callbacks);
        final Network.NetworkCallbacks finalCallbacks = callbacks;
        Future<Network.Response<Receive>> task = makeRequestTask(callbacks);
        if(timeout != null) {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if(!task.isDone()) {
                        task.cancel(true);
                        finalCallbacks.onRequestTimedOut(requestId);
                    }
                }
            }, timeout.toMillis());
        }
    }

    /**
     * Executes this request in a blocking fashion, but on a separate thread. Nonetheless,
     * current thread will be blocked for the duration. If request isn't finished before
     * the timeout is reached, TimeoutException is thrown.
     * In case NetworkExceptionHandler is supplied, worst-case time this method can take
     * is timeout*2, in case first request almost reaches timeout, and second times out.
     * @param timeout timeout
     * @return
     * @throws ExecutionException something unexpected has occurred
     * @throws TimeoutException timeout has been reached, and request hasn't completed
     * @throws IOException some IO exception occurred. Could be network issue, could be
     *      something with files, could be something else.
     */
    @SuppressWarnings("unchecked")
    public Network.Response<Receive> blocking(Duration timeout)
            throws ExecutionException, TimeoutException, IOException {
        verifyRequest();

        Network.Response<Receive> response = getTaskResult(makeRequestTask(null), timeout);

        if(exceptionHandler != null && response.isError()) {
            response = getTaskResult(getExceptionHandlerTask(response), timeout);
        }
        return response;
    }

    private Network.NetworkCallbacks<Receive> extendCallbacksWithHandler(final Network.NetworkCallbacks<Receive> callbacks) {
        return new Network.NetworkCallbacks<>() {
            @Override
            public void onRequestCompleted(int id, Network.Response<Receive> response) {
                if(response.isError()) {
                    response = response.handleErrorCode(exceptionHandler);
                }
                callbacks.onRequestCompleted(id, response);
            }

            @Override
            public void onExceptionThrown(int id, Throwable ex) {
                callbacks.onExceptionThrown(id, ex);
            }

            @Override
            public void onRequestTimedOut(int id) {
                callbacks.onRequestTimedOut(id);
            }
        };
    }

    private Future<Network.Response<Receive>> getExceptionHandlerTask(final Network.Response<Receive> response) {
        return executeOn.submit(() -> response.handleErrorCode(exceptionHandler));
    }

    private static <T> Network.Response<T> getTaskResult(Future<Network.Response<T>> task, Duration timeout)
            throws TimeoutException, IOException, ExecutionException {
        try {
            return task.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException ex) {
            throw new InvalidResponseException("Interrupted while sending request or parsing response");
        } catch (ExecutionException ex) {
            if(ex.getCause() instanceof FileNotFoundException)
                throw (FileNotFoundException)ex.getCause();
            else if(ex.getCause() instanceof IOException)
                throw (IOException)ex.getCause();
            else
                throw ex;
        }
    }
}
