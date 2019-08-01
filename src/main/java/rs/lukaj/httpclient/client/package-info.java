/**
 * Classes meant to be used by the programmer to initiate network requests. Everything in this package
 * except HttpClient was originally written for purposes of Android apps I've developed and packaged it
 * into a library. <a href="https://github.com/luka-j/MinimalJavaNetworking/">Check it out</a>!
 * <em>Most</em> of the code is in tact, but some had to be modified to use the
 * {@link rs.lukaj.httpclient.connections.HttpTransaction} instead  of {@link java.net.HttpURLConnection}
 * and meanwhile I figured some things could be done better and simpler (I wrote some parts all the way
 * back in 2015/2016). Some docs may seem out of place.
 * <br/>
 * <h3>Overview</h3>
 * {@link rs.lukaj.httpclient.client.HttpClient} provides common connection pool and cache for
 * multiple HttpTransactions.
 * <br/>
 * {@link rs.lukaj.httpclient.client.Network} makes the request and sends data using HttpTransaction.
 * It has some very abstract notion of HTTP.
 * <br/>
 * {@link rs.lukaj.httpclient.client.NetworkRequestBuilder} doesn't know much nor cares about HTTP.
 * It takes data from client and passes it to Network. It cares about on which thread is what executed
 * and for how long, as well as whether one is sending a HashMap or a File. When used in conjunction
 * with NetworkExceptionHandler, it can handle virtually everything using callbacks.
 */
package rs.lukaj.httpclient.client;