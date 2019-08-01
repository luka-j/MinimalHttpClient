/**
 * This package implements low-level communication with server.
 * More high-level (i.e. usable) stuff is located inside the client package.
 *
 * <br/>
 * <h3>Rationale</h3>
 * Using java.net.* could've been fine, BUT there is no (pretty) way to guarantee everything asked in the task.
 * HttpURLConnection, when created using {@link java.net.URL#openConnection()} doesn't provide interface to the lower level. Moreover,
 * docs for {@link java.net.HttpURLConnection} state it is meant to be used only <em>once</em>, so pooling and reusing them would
 * break its contract. It can reuse existing (underlying) connections, but that is very opaque and boils down to
 * implementation. Making absolutely sure task requests are followed would require designing my own SocketFactory
 * and tying it to the classloader before any connections are made, and figuring out how exactly HttpURLConnection
 * uses them so I can manipulate it. In short, lots of reflection, and no guarantees it won't break down on some
 * obscure JVM. Obviously, implement-everything-from-scratch approach has downsides as well. I'm pretty sure
 * HttpURLConnection better handles numerous edge cases and common-but-not-exactly-standard behaviour in the wild,
 * but considering  this is only homework, I opted for the more fun route. (On something that's meant to go into
 * production, I'd seriously think about appropriating something from the standard library.)
 *
 *
 * <br/>
 * <br/>
 * <h3>Overview</h3>
 * {@link rs.lukaj.httpclient.connections.HttpSocket} provides a way to send raw data to client. Doesn't actually implement any HTTP.
 * <br/>
 * {@link rs.lukaj.httpclient.connections.ConnectionPool} (implemented as
 * {@link rs.lukaj.httpclient.connections.ConfigurableConnectionPool}) pools HttpSockets. Implementation can be
 * configured to do everything the task requires and some more.
 * <br/>
 * {@link rs.lukaj.httpclient.connections.HttpRequest} / {@link rs.lukaj.httpclient.connections.HttpResponse}
 * use the HttpSocket to communicate with server using HTTP. They know the basic structure of each request and response.
 * <br/>
 * {@link rs.lukaj.httpclient.connections.HttpTransaction} makes the request, parses response headers and figures
 * out if anything else needs to be done. It uses HttpRequest/HttpResponse internally. This is (admittedly, a
 * rather poor) equivalent of HttpURLConnection.
 * <br/>
 * {@link rs.lukaj.httpclient.connections.HttpCache} can in theory be used to cache responses and serve it to
 * user when appropriate. In practice, it's used only when server returns 304. Wouldn't bet, well, anything on it.
 * <br/>
 * Bonus: chunked transfer. One could use this library for Transfer-Encoding: chunked responses (or requests!), but
 * it's quite fragile; see commented out example in Main class.
 * <br/>
 */
package rs.lukaj.httpclient.connections;