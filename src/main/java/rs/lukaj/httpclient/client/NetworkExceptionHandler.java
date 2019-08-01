package rs.lukaj.httpclient.client;

import rs.lukaj.httpclient.connections.HttpResponse;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.TimeoutException;


/**
 * Handles possible network errors. Implementers shouldn't assume code is run on any particular thread.
 * Created by luka on 3.1.16.
 */
public interface NetworkExceptionHandler {

    //if error mentions invalid http version, check whether spaces are properly encoded

    /**
     * User isn't logged in. Token should be cleared and user should be redirected to the login screen.
     */
    void handleUserNotLoggedIn();

    /**
     * User doesn't have appropriate permissions to access something on server. When UI is properly
     * implemented, this can only happen because (more likely) dev screwed up, or (less likely) user
     * has been tampering with this app's data using a tool such as root explorer.
     */
    void handleInsufficientPermissions(String message);

    /**
     * Unspecified server error has occured. Display a polite error message.
     */
    void handleServerError(String message);

    /**
     * Requested resource isn't found. Most of the times it's the dev's fault (user shouldn't
     * be able to make impossible requests).
     */
    void handleNotFound();

    /**
     * Requested resource was there, but isn't anymore and probably won't be ever again.
     */
    void handleGone();

    /**
     * There is resource conflict on server. Most often,
     * user is trying to create duplicate something that shouldn't have duplicates.
     * Sometimes this is possible (i.e. upon registration, when registering with email
     * already in use, in which case user should be prompted to pick some other), other
     * times it's dev's error or result of illegal tampering (every other case AFAIR).
     */
    void handleConflict();

    /**
     * Invalid request. Bad types, bad ranges, missing values, superfluous values,... god knows.
     * Consult message for details.
     * @param message possible details for the error
     */
    void handleBadRequest(String message);

    /**
     * JSON parsing exceptions. This really should never happen, and either means malformed
     * json coming from server, or broken parsing code on client. Both should be caught early.
     */
    void handleJsonException();

    /**
     * Server is currently offline due to maintenance, and will be back soon (well, let's hope so).
     * @param until optional, denotes time when maintenance will end
     */
    void handleMaintenance(String until);

    /**
     * Server is unreachable. Check your internet connection (and whether you're connecting to
     * localhost on mobile data). If you're connected and it's still unreachable someone blew
     * up our servers.
     */
    void handleUnreachable();

    /**
     * IO Exception occurred. Could be due to network, or something to do with files. Dunno.
     * @param ex details of the exception
     */
    void handleIOException(IOException ex);

    /**
     * User has an invalid token, or is trying to access something that is off-the-limits.
     * Not much to do about it, show a message and hope it's nothing serious.
     * @param errorMessage "Invalid" if token is invalid, god-knows-what if everything else
     */
    void handleUnauthorized(String errorMessage);

    /**
     * User is making excessive requests from this device, and server is telling to cool
     * down.
     * @param retryAfter optional, time at which additional requests can be made
     */
    void handleRateLimited(String retryAfter);

    /**
     * Bad gateway. One of those errors I never truly understood, but basically something
     * happened on the server. Display a message to try again, maybe it'll fix itself.
     */
    void handleBadGateway();

    /**
     * See {@link #handleBadGateway()}, but with more hope it will fix itself.
     * Can also occur if server is under massive load.
     */
    void handleGatewayTimeout();

    /**
     * This usually means someone is trying to be clever and send a too large request.
     * Ask him to try something shorter/smaller, because server has its limits.
     */
    void handleEntityTooLarge();

    /**
     * Something has gone really wrong and this time we have no idea what. This shouldn't
     * be called for normal codes (i.e. &lt;=400), so good luck figuring out what it is.
     * @param code response code for which there is no appropriate handle
     * @param message possible details on the error
     */
    void handleUnknownHttpCode(HttpResponse.Code code, String message);

    /**
     * Optional, can be called upon request completion, depending on the implementation.
     */
    default void finished() {}

    void handleTimeoutException(TimeoutException e);


    /**
     * Reference implementation, methods can be overriden as necessary.
     */
    class DefaultHandler implements NetworkExceptionHandler {

        private boolean hasErrors = false;

        public DefaultHandler() {
        }

        private void displayError(final String title, final String message) {
            System.err.println(title + ": " + message);
        }

        @Override
        public void handleUserNotLoggedIn() {
            displayError("Unauthorized", "User isn't logged in. Please log in and try again.");
            hasErrors = true;
        }

        @Override
        public void handleInsufficientPermissions(String message) {
            displayError("Insufficient permissions", "You don't have sufficient permissions to access " +
                    "requested resource: " + message);
            hasErrors = true;
        }

        @Override
        public void handleServerError(String message) {
            displayError("Server error", "Something has gone wrong on the server. It says: " + message);
            hasErrors = true;
        }

        @Override
        public void handleNotFound() {
            displayError("404 Not found", "Server can't find what you're looking for. Check if you're " +
                    "looking for the right thing.");
            hasErrors = true;
        }

        @Override
        public void handleGone() {
            displayError("Gone", "Something used to be here, but not anymore. Sorry.");
            hasErrors = true;
        }

        @Override
        public void handleConflict() {
            displayError("Resource conflict", "There is a resource conflict on the server. Maybe you're" +
                    "trying to create a duplicate where one isn't expected?");
            hasErrors = true;
        }

        @Override
        public void handleBadRequest(String message) {
            displayError("Bad request", "It seems that you've sent a bad request. Server says: " + message);
            hasErrors = true;
        }

        @Override
        public void handleJsonException() {
            displayError("JSON exception", "Parsing the response didn't go as expected. Maybe it's malformed.");
            hasErrors = true;
            finishedUnsuccessfully();
        }

        @Override
        public void handleMaintenance(String until) {
            displayError("Maintenance", "The server is under maintenance. Try again after " + until);
            hasErrors = true;
        }

        @Override
        public void handleUnreachable() {
            displayError("Unreachable", "Server seems to be unreachable. If you believe it should be " +
                    "there, try again later.");
            hasErrors = true;
        }

        @Override
        public void finished() {
            if (!hasErrors)
                finishedSuccessfully();
            else
                finishedUnsuccessfully();
            hasErrors = false;
        }

        @Override
        public void handleTimeoutException(TimeoutException e) {
            displayError("Timeout", "Too much time spent waiting for connection to finish.");
            e.printStackTrace();
            hasErrors = true;
        }

        @Override
        public void handleUnauthorized(String errorMessage) {
            displayError("Unauthorized", "You are not authorized to access that resource. Authorize " +
                    "and try again.");
            hasErrors = true;
        }

        @Override
        public void handleRateLimited(String retryAfter) {
            displayError("Rate limited", "You've been making too many requests. Try again after " + retryAfter);
            hasErrors = true;
        }

        @Override
        public void handleBadGateway() {
            displayError("Bad gateway", "Your request seems to have taken a wrong turn. Try again later.");
            hasErrors = true;
        }

        @Override
        public void handleGatewayTimeout() {
            displayError("Gateway timeout", "Server is unresponsive at the moment. Try again later.");
            hasErrors = true;
        }

        @Override
        public void handleEntityTooLarge() {
            displayError("Too large", "You seem to be sending something that is too large for the server." +
                    "Try with something smaller");
            hasErrors = true;
        }

        @Override
        public void handleUnknownHttpCode(HttpResponse.Code code, String message) {
            displayError("Something's wrong", "Unknown error. Code " + code + ", message " + message);
            hasErrors = code.isError();
        }

        public void finishedSuccessfully() {
            ;
        }

        public void finishedUnsuccessfully() {
            ;
        }

        @Override
        public void handleIOException(final IOException ex) {
            if (ex instanceof SocketException) {
                handleSocketException((SocketException) ex);
            } else {
                handleUnknownIOException(ex);
            }
            hasErrors = true;
            finishedUnsuccessfully();
        }

        public void handleSocketException(SocketException ex) {
            ex.printStackTrace();
            hasErrors = true;
        }

        public void handleUnknownIOException(IOException ex) {
            ex.printStackTrace();
            finishedUnsuccessfully();
        }
    }
}
