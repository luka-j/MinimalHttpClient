package rs.lukaj.httpclient.client;

/**
 * Provides the interface for interacting with the tokens which identify user currently logged in.
 * Tokens are unique Strings, and any attempt to tamper them will result in server rejecting them.
 * Tokens are provided by the server upon successful login or registration, as well as on refresh.
 * Tokens have limited lifespan and need to be refreshed periodically, which is handled by Network
 * class. Class representing a User can be made to implement this interface, or it can be detached
 * from it. Persist the token somewhere, e.g. in SharedPreferences in order to avoid bothering the
 * user and minimize the need for unnecessary logins. I love how this doc is so perfectly aligned.
 *
 * Created by luka on 4.8.17.
 */
public interface AuthTokenManager {
    int TOKEN_EXPIRED = 0;
    int TOKEN_INVALID = 1;
    int TOKEN_UNKNOWN = 2;

    /**
     * Retrieves the token of the currently logged in user
     * @return token identifying the currently logged in user
     */
    String getToken();

    /**
     * Should examine given response, and it case token is expired refresh it, so next call of
     * getToken() results in valid token. Potential network errors should be handled by provided
     * NetworkExceptionHandler
     * @param response response which generated the error
     * @param handler in case exception handling is needed
     * @throws NotLoggedInException in case error can't be handled (e.g. invalid token) and user
     *      needs to be logged out.
     */
    void handleTokenError(Network.Response<?> response, NetworkExceptionHandler handler) throws NotLoggedInException;

    /**
     * This method gets called in case "Unauthorized" (401) response is received from server
     * and is used to figure out whether the error is due to authorization token. In case
     * {@link #TOKEN_EXPIRED} is returned, a refresh is attempted using
     * {@link #handleTokenError(Network.Response, NetworkExceptionHandler)} and retrying the
     * request. If {@link #TOKEN_INVALID} is returned, {@link NetworkExceptionHandler#handleUserNotLoggedIn()}
     * is called, or in case of {@link #TOKEN_UNKNOWN} {@link NetworkExceptionHandler#handleUnauthorized(String)}
     * @param response server response
     * @return {@link #TOKEN_EXPIRED}, {@link #TOKEN_INVALID} or {@link #TOKEN_UNKNOWN}
     */
    int getTokenStatus(Network.Response<?> response);

    /**
     * Removes currently present token.
     */
    void clearToken();
}
