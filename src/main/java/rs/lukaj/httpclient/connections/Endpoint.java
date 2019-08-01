package rs.lukaj.httpclient.connections;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * Endpoint to which connections are connected. Consists of host and port.
 */
public class Endpoint {
    private final InetAddress address;
    private final String host;
    private final short port;
    private final boolean https;

    /**
     * Create a new endpoint
     * @param host hostname of the server
     * @param port port on which to connect (e.g. 80 for HTTP, 443 for HTTPS)
     * @param isHttps should the connection be over TLS
     * @throws UnknownHostException
     */
    public Endpoint(String host, short port, boolean isHttps) throws UnknownHostException {
        if(host == null) throw new NullPointerException("Host can't be null!");
        this.address = InetAddress.getByName(host);
        this.port = port;
        this.host = host;
        this.https = isHttps;
    }

    /**
     * Create Endpoint from URL passed as string
     * @param urlAddress URL to which this endpoint should point
     * @return new Endpoint for the given address
     * @throws UnknownHostException if host cannot be resolved
     * @throws MalformedURLException if address is malformed
     */
    public static Endpoint fromUrl(String urlAddress) throws UnknownHostException, MalformedURLException {
        URL url = new URL(urlAddress);
        return fromUrl(url);
    }

    /**
     * Create Endpoint from URL passed. If not present, port will be inferred from protocol.
     * @param url URL to which this endpoint should point
     * @return new Endpoint for given address
     * @throws UnknownHostException if host cannot be resolved
     * @throws MalformedURLException if address is malformed (e.g. protocol is neither http or https and no host is provided)
     */
    public static Endpoint fromUrl(URL url) throws UnknownHostException, MalformedURLException {
        int port = url.getPort();
        if(port == -1) {
            if(url.getProtocol().equals("http")) port = 80;
            else if(url.getProtocol().equals("https")) port = 443;
            else throw new MalformedURLException("Unknown protocol: " + url.getProtocol());
        }
        return new Endpoint(url.getHost(), (short)port, url.getProtocol().equals("https"));
    }

    public InetAddress getAddress() {
        return address;
    }
    public short getPort() {
        return port;
    }
    public String getHost() {
        return host;
    }
    public boolean isHttps() {
        return https;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Endpoint)) return false;
        Endpoint other = (Endpoint)obj;
        return port == other.port && address.equals(other.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, port);
    }
}
