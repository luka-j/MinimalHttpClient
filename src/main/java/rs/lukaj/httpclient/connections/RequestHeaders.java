package rs.lukaj.httpclient.connections;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;

import static rs.lukaj.httpclient.connections.Headers.HeaderStatus.*;

/**
 * Headers which are sent with the request. Provides helper functions for setting them.
 * If empty or null is passed to helper functions, header is removed.
 */
public class RequestHeaders extends Headers {

    private static final Map<String, HeaderStatus> knownHeaders = new HashMap<>() {{
        put("a-im", PERMANENT);
        put("accept", PERMANENT);
        put("accept-charset", PERMANENT);
        put("accept-datetime", PERMANENT);
        put("accept-encoding", PERMANENT);
        put("accept-language", PERMANENT);
        put("access-control-request-method", PERMANENT);
        put("access-control-request-headers", PERMANENT);
        put("authorization", PERMANENT);
        put("cache-control", PERMANENT);
        put("connection", PERMANENT);
        put("content-length", PERMANENT);
        put("content-md5", OBSOLETE);
        put("content-type", PERMANENT);
        put("cookie", PERMANENT);
        put("date", PERMANENT);
        put("expect", PERMANENT);
        put("forwarded", PERMANENT);
        put("from", PERMANENT);
        put("host", PERMANENT);
        put("http2-settings", PERMANENT); //not-really-supported
        put("if-match", PERMANENT);
        put("if-modified-since", PERMANENT);
        put("if-none-match", PERMANENT);
        put("if-range", PERMANENT);
        put("if-unmodified-since", PERMANENT);
        put("max-forwards", PERMANENT);
        put("origin", PERMANENT);
        put("pragma", PERMANENT);
        put("proxy-authorization", PERMANENT);
        put("range", PERMANENT);
        put("referer", PERMANENT);
        put("te", PERMANENT);
        put("user-agent", PERMANENT);
        put("upgrade", PERMANENT); //not-really-supported
        put("via", PERMANENT);
        put("warning", PERMANENT);
        put("upgrade-insecure-requests", NONSTANDARD);
        put("x-requested-with", NONSTANDARD);
        put("dnt", NONSTANDARD);
        put("x-forwarded-for", NONSTANDARD);
        put("x-forwarded-host", NONSTANDARD);
        put("x-forwarded-proto", NONSTANDARD);
        put("front-end-ttps", NONSTANDARD);
        put("x-http-method-override", NONSTANDARD);
        put("x-att-deviceid", NONSTANDARD);
        put("x-wap-profile", NONSTANDARD);
        put("proxy-connection", NONSTANDARD);
        put("x-uidh", NONSTANDARD);
        put("x-csrf-token", NONSTANDARD);
        put("x-request-id", NONSTANDARD);
        put("x-correlation-id", NONSTANDARD);
        put("save-data", NONSTANDARD);
        //these are actually response headers according to Wikipedia, but work in the wild
        put("transfer-encoding", NONSTANDARD);
        put("content-encoding", NONSTANDARD);
    }};

    public static class Config {
        public boolean allowUnknownHeaders = true;
        public boolean warnUnknownHeaders = true;
        public boolean allowNonstandardHeaders = true;
        public boolean warnNonstandardHeaders = false;
        public boolean allowObsoleteHeaders = true;
        public boolean warnObsoleteHeaders = true;
        public Config() {
        }
        public Config(boolean allowUnknownHeaders, boolean warnUnknownHeaders, boolean allowNonstandardHeaders,
                              boolean warnNonstandardHeaders, boolean allowObsoleteHeaders, boolean warnObsoleteHeaders) {
            this.allowUnknownHeaders = allowUnknownHeaders;
            this.warnUnknownHeaders = warnUnknownHeaders;
            this.allowNonstandardHeaders = allowNonstandardHeaders;
            this.warnNonstandardHeaders = warnNonstandardHeaders;
            this.allowObsoleteHeaders = allowObsoleteHeaders;
            this.warnObsoleteHeaders = warnObsoleteHeaders;
        }
    }


    private Config config = new Config();
    private RequestHeaders() {
    }

    //there's no great reason to use static factory methods here, except disambiguating 'default' and 'empty' headers
    //and honestly, that's enough for me
    public static RequestHeaders createEmpty() {
        RequestHeaders headers = new RequestHeaders();
        headers.clear();
        return headers;
    }

    public static RequestHeaders createDefault() {
        RequestHeaders headers = new RequestHeaders();
        headers.setCharset(StandardCharsets.UTF_8);
        headers.setAcceptEncoding("gzip,deflate");
        headers.setDate();
        try {
            headers.setUserAgent("Java " + System.getProperty("java.version"));
        } catch (SecurityException ignored) {}
        return headers;
    }

    public RequestHeaders setConfig(Config config) {
        this.config = config;
        return this;
    }


    public void setAuthorization(String auth) {
        if(auth == null || auth.isEmpty()) removeHeader("Authorization");
        else put("Authorization", auth);
    }
    public void setCharset(Charset charset) {
        if(charset == null) removeHeader("Accept-Charset");
        else put("Accept-Charset", charset.name());
    }
    public void addCharset(Charset charset) {
        if(charset == null) return;
        else put("Accept-Charset", getOrDefault("Accept-Charset", "") + ", " + charset);
    }
    public void setConnection(String connection) {
        if(connection == null || connection.isEmpty()) removeHeader("Connection");
        put("Connection", connection);
    }
    public void setCharset(String charset) {
        if(charset == null || charset.isEmpty()) removeHeader("Accept-Charset");
        else put("Accept-Charset", charset);
    }
    public void setContentLength(long contentLength) {
        if(contentLength < 0) System.err.println("Warning: setting Content-Length to " + contentLength);
        else put("Content-Length", String.valueOf(contentLength));
    }
    public void setContentType(String type) {
        if(type == null || type.isEmpty()) removeHeader("Content-Type");
        else put("Content-Type", type);
    }
    public void setAcceptEncoding(String encoding) {
        if(encoding == null || encoding.isEmpty()) removeHeader("Accept-Encoding");
        else put("Accept-Encoding", encoding);
    }
    public void setTransferEncoding(String encoding) { //this is nonstandard
        if(encoding == null || encoding.isEmpty()) removeHeader("Transfer-Encoding");
        else put("Transfer-Encoding", encoding);
    }

    /**
     * Sets the encoding using which data is sent, if you're sending a string. You don't need to
     * compress it — HttpTransaction will take care of it.
     * @param encoding "gzip" or "deflate"
     */
    public void setContentEncoding(String encoding) {
        if(encoding == null || encoding.isEmpty()) removeHeader("Content-Encoding");
        else put("Content-Encoding", encoding);
    }
    public void setTE(String encoding) {
        if(encoding == null || encoding.isEmpty()) removeHeader("TE");
        else put("TE", encoding);
    }
    public void setDate() {
        setDate(ZonedDateTime.now(ZoneOffset.UTC));
    }
    public void setDate(TemporalAccessor date) {
        if(date == null) removeHeader("Date");
        else put("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(date));
    }
    public void setFrom(String from) {
        if(from == null || from.isEmpty()) removeHeader("From");
        else put("From", from);
    }
    public void setHost(String host) {
        if(host == null || host.isEmpty()) removeHeader("Host");
        else put("Host", host);
    }
    public void setReferer(String referer) {
        if(referer == null || referer.isEmpty()) removeHeader("Referer");
        else put("Referer", referer);
    }
    public void setUserAgent(String userAgent) {
        if(userAgent == null || userAgent.isEmpty()) removeHeader("User-Agent");
        else put("User-Agent", userAgent);
    }
    public void setAccept(String types) {
        if(types == null || types.isEmpty()) removeHeader("Accept");
        else put("Accept", types);
    }

    public String setHeader(String header, String value) {
        checkHeader(header);
        return super.setHeader(header, value);
    }

    public void checkHeaders() {
        for(String header : this.keySet()) {
            checkHeader(header);
        }
    }

    private void checkHeader(String header) {
        header = header.toLowerCase();
        HeaderStatus status = knownHeaders.get(header);
        if((status == null || status == HeaderStatus.UNKNOWN)) {
            if(!config.allowUnknownHeaders) throw new InvalidHeaderException("Unknown header " + header);
            if(config.warnUnknownHeaders) System.err.println("Warning: using unknown header " + header);
        } else if(status == NONSTANDARD) {
            if(!config.allowNonstandardHeaders) throw new InvalidHeaderException("Nonstandard header " + header);
            if(config.warnNonstandardHeaders) System.err.println("Warning: using nonstandard header " + header);
        } else if(status == OBSOLETE) {
            if(!config.allowObsoleteHeaders) throw new InvalidHeaderException("Obsolete header " + header);
            if(config.warnObsoleteHeaders) System.err.println("Warning: using obsolete header " + header);
        }
    }
}
