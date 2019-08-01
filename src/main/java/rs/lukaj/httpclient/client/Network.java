package rs.lukaj.httpclient.client;

import rs.lukaj.httpclient.connections.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;


/**
 * Created by luka on 1.1.16.
 */
//I've started this _too_ long ago. It is rather generic-heavy code which I've adapted to fit in with HttpClient,
//so some generics probably look more useless than usual.
public class Network {
    /**
     * Network callbacks used for async calls.
     */
    public interface NetworkCallbacks<T> {
        /**
         * Request has completed. Check whether response is okay and handle possible error
         * codes appropriately.
         * @param id request id
         * @param response response
         */
        void onRequestCompleted(int id, Response<T> response);

        /**
         * Exception has been thrown during request execution. Some RuntimeExceptions might
         * not get caught and reported in this fashion, which will make them silently shut
         * down the background thread (keep in mind while debugging).
         * @param id request id
         * @param ex exception thrown
         */
        void onExceptionThrown(int id, Throwable ex);

        /**
         * Request has timed out. This method is called only when executing the request
         * asynchronously and request doesn't complete in specified time.
         * @param id request id
         */
        void onRequestTimedOut(int id);
    }

    /**
     * Holds a response from server. Consists of response code, an (optional) content and (optional) error message.
     * There cannot be both an error message and content present. Consult response codes in order to figure out what
     * to look for, or use {@link #isError()}
     */
    public static class Response<Receive> {

        private final HttpResponse response;

        private final AuthTokenManager tokens;
        private final Request<?, Receive> request;
        private Receive responseData;

        private Response(Request<?, Receive> request, HttpResponse response, AuthTokenManager tokens) {
            this.response = response;
            this.tokens = tokens;
            this.request = request;
        }

        public boolean isError() {
            return response.getStatus().isError();
        }

        private void setResponseData(Receive data) {
            this.responseData = data;
        }

        public HttpResponse getHttpResponse() {
            return response;
        }
        public Receive getResponseData() {
            return responseData;
        }

        public HttpResponse.Status getStatus() {
            return response.getStatus();
        }
        public ResponseHeaders getHeaders() {
            return response.getHeaders();
        }
        /**
         * Uses passed NetworkExceptionHandler to handle possible errors and returns new response.
         * It is not guaranteed same Response object (this) will be returned, or that no other requests will be made.
         * Should be called on the background thread.
         */
        public Response<Receive> handleErrorCode(NetworkExceptionHandler handler) {
            if(!isError()) {
                return this;
            }
            String errorMessage;
            try {
                //like any sane person, we're expecting a string if there's an error
                //server doesn't really _have_ to oblige, but usually this will work
                //worst case, we'll end up with bunch of garbage as errorMessage
                errorMessage = response.getBodyString();
            } catch (IOException e) {
                handler.handleIOException(e);
                return this;
            }
            switch (response.getStatus().getCode()) {
                case UNAUTHORIZED:
                    if(tokens != null && tokens.getTokenStatus(this) == AuthTokenManager.TOKEN_EXPIRED) {
                        try {
                            tokens.handleTokenError(this, handler); //this should make actual request to refresh token
                            Response<Receive> handled = request.call(); //redoing the current request
                            handled.handleErrorCode(handler);
                            return handled;
                        } catch (NotLoggedInException ex) {
                            handler.handleUserNotLoggedIn();
                        } catch (IOException e) {
                            handler.handleIOException(e);
                        } catch (TimeoutException e) {
                            handler.handleTimeoutException(e);
                        }
                    } else if(tokens != null && tokens.getTokenStatus(this) == AuthTokenManager.TOKEN_INVALID) {
                        handler.handleUserNotLoggedIn();
                    } else {
                        handler.handleUnauthorized(errorMessage);
                    }
                    break;
                case FORBIDDEN:
                    handler.handleInsufficientPermissions(errorMessage);
                    return this;
                case SERVER_ERROR:
                    handler.handleServerError(errorMessage);
                    return this;
                case NOT_FOUND:
                    handler.handleNotFound();
                    return this;
                case GONE:
                    handler.handleGone();
                    return this;
                case ENTITY_TOO_LARGE:
                    handler.handleEntityTooLarge();
                    return this;
                case CONFLICT:
                    handler.handleConflict();
                    return this;
                case BAD_REQUEST:
                    handler.handleBadRequest(errorMessage);
                    return this;
                case TOO_MANY_REQUESTS:
                    handler.handleRateLimited(response.getHeaders().getRetryAfter());
                    return this;
                case SERVER_UNREACHABLE:
                    handler.handleUnreachable();
                    return this;
                case BAD_GATEWAY:
                    handler.handleBadGateway();
                    return this;
                case SERVER_DOWN:
                    if("Maintenance".equals(errorMessage)) handler.handleMaintenance(response.getHeaders().getRetryAfter());
                    else handler.handleUnreachable();
                    return this;
                case GATEWAY_TIMEOUT:
                    handler.handleGatewayTimeout();
                    return this;
                default:
                    handler.handleUnknownHttpCode(response.getStatus().getCode(), errorMessage);
                    return this;
            }
            return this;
        }
    }


    /**
     * Represents one network request, consisting of url, authorization token, and http method.
     * In case of async calls (on executor) it also holds requestId
     * and appropriate callback. T is type of request, i.e. what's sent and what's received.
     * Currently, it's not possible to have StringRequest which has file as a request (though
     * it's of course possible to have empty request, i.e. get, which is usually more appropriate).
     */
    protected static abstract class Request<Send, Receive> implements Callable<Response<Receive>> {
        private int                 requestId;
        private HttpClient          client;
        private RequestHeaders      headers;
        private String              url;
        private AuthTokenManager    tokens;
        private Http.Verb              httpVerb;
        private NetworkCallbacks<Receive> callback;
        protected Send data;

        public Request(int requestId, HttpClient client, RequestHeaders headers, String url, AuthTokenManager tokens,
                        Http.Verb httpVerb, Send data, NetworkCallbacks<Receive> callback) {
            this.requestId = requestId;
            this.client = client;
            this.headers = headers;
            this.url = url;
            this.tokens = tokens;
            this.httpVerb = httpVerb;
            this.data = data;
            this.callback = callback; //in case this isn't null, it also notifies callback when request is done (apart from 
                                      //returning value in call() method)
        }

        @Override
        public Response<Receive> call() throws IOException, TimeoutException {
            try (HttpTransaction transaction = client.newTransaction()) {
                if (headers != null) transaction.setHeaders(headers);
                if (tokens != null && tokens.getToken() != null) {
                    RequestHeaders headers = transaction.getHeaders();
                    headers.setAuthorization("Bearer " + tokens.getToken());
                }
                uploadData(transaction);
                HttpResponse rawResponse = transaction.makeRequest(httpVerb, url);

                final Response<Receive> response = new Response<>(this, rawResponse, tokens);
                if (!response.isError())
                    response.setResponseData(getData(rawResponse));
                if (callback != null) {
                    callback.onRequestCompleted(requestId, response);
                }
                return response;
            } catch (final Throwable ex) {
                if (callback != null) {
                    callback.onExceptionThrown(requestId, ex);
                    return null;
                } else {
                    throw ex;
                }
            }
        }

        protected void sendForm(Map<String, String> params, HttpTransaction transaction) {
            if (params.size() > 0) {
                if(httpVerb != Http.Verb.GET) {
                    transaction.getHeaders().setContentType("application/x-www-form-urlencoded");
                    transaction.sendString(dataToString(params).toString());
                } else {
                    url = appendDataToUrl(url, params);
                }
            }
        }

        protected void sendString(String str, HttpTransaction transaction) {
            transaction.sendString(str);
        }

        protected void sendFile(File data, HttpTransaction transaction) {
            transaction.sendFile(data);
        }

        protected String getString(HttpResponse response) throws IOException {
            return response.getBodyString();
        }

        protected void getChunks(HttpSocket.ChunkCallbacks callbacks, Executor executor, HttpResponse response) {
            response.getChunks(callbacks, executor);
        }

        protected File getFile(File saveTo, HttpResponse response) throws IOException {
            if(saveTo != null) {
                if (!saveTo.exists() && !saveTo.createNewFile()) throw new IOException("Cannot create new file");
                response.writeBodyToFile(saveTo);
            }
            return saveTo;
        }

        /**
         * Only called if request is successfully completed (indicated by status code)
         * @param response response from server
         * @return request body
         * @throws IOException if I/O Exception occurs during transfer
         */
        protected abstract Receive getData(HttpResponse response) throws IOException;
        protected abstract void uploadData(HttpTransaction connection) throws IOException;
    }



    private static String appendDataToUrl(String url, Map<String, String> data) {
        StringBuilder fullUrl = new StringBuilder(url);

        if(!url.contains("?")) fullUrl.append("?");
        else fullUrl.append("&");
        fullUrl.append(dataToString(data));
        return fullUrl.toString();
    }

    private static StringBuilder dataToString(Map<String, String> data) {
        if(data.isEmpty()) return new StringBuilder();

        StringBuilder urlParams = new StringBuilder(data.size() * 16);
        for (Map.Entry<String, String> param : data.entrySet()) {
            urlParams.append(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8)).append('&');
        }
        urlParams.deleteCharAt(urlParams.length() - 1); //trailing &
        return urlParams;
    }
}
