package rs.lukaj.httpclient.connections;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/**
 * Headers which are received from server. Provides helper functions for getting them.
 */
public class ResponseHeaders extends Headers {
    public ResponseHeaders() {
    }
    public ResponseHeaders(String headers) {
        String[] hs = headers.split("\n");
        for (String header : hs) {
            setHeader(header);
        }
    }


    //used when server returns 405
    public String[] getAllowedMethods() {
        return getHeader("Allowed-Methods").split("\\s*,\\s*");
    }
    public String getCacheControl() {
        return getHeader("Cache-Control");
    }
    public String getConnection() {
        return getHeader("Connection");
    }
    public String getContentEncoding() {
        return getHeader("Content-Encoding");
    }
    public String getTransferEncoding() {
        return getHeader("Transfer-Encoding");
    } //oh god these headers are a mess
    public String getContentLanguage() {
        return getHeader("Content-Language");
    }
    public String getContentLength() {
        return getHeader("Content-Length");
    }
    public String getContentType() {
        return getHeader("Content-Type");
    }
    public TemporalAccessor getDate() {
        return DateTimeFormatter.RFC_1123_DATE_TIME.parse("Date");
    }
    public String getETag() {
        return getHeader("ETag");
    }
    //used for redirection (among other things)
    public String getLocation() {
        return getHeader("Location");
    }
    public String getRetryAfter() {
        return getHeader("Retry-After");
    }
    public String getMIME() {
        String contentType = getContentType();
        return contentType.split(";", 2)[0];
    }
    public String getCharset() { //unused (assuming UTF8 everywhere), and kinda ugly
        String contentType = getContentType();
        String tokens[] = contentType.split("charset=", 2);
        if(tokens.length==1) return "utf-8"; //default
        return tokens[1].split(" ")[0];
    }
}
