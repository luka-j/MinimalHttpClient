package rs.lukaj.httpclient.connections;

import java.util.HashMap;
import java.util.Map;


/**
 * Represents headers which are received from server or sent as a part of the request.
 * Header names are case-insensitive.
 */
public class Headers extends HashMap<String, String> { //blatant violation of Item 16
    //todo make this HashMap<String, List<String>> (split on comma)
    // or even HashMap<String, List<List<String>> (split on comma and semicolon)

    protected enum HeaderStatus {
        PERMANENT, //it's a permanent addition to the standard
        OBSOLETE, //it's used to be standard, now it isn't (rare)
        NONSTANDARD, //it's frequently used, but not part of the standard
        UNKNOWN //unrecognized header
    }

    @Override
    public String put(String key, String value) {
        return setHeader(key, value);
    }

    /**
     * Get value of the header identified by the name passed
     * @param header name of the header
     * @return value of the header, or null if it doesn't exist
     */
    public String getHeader(String header) {
        return get(header.toLowerCase());
    }

    /**
     * Put a new header, replacing the existing one if it exists. Header names are stored lowercase.
     * @param header name of the header
     * @param value value of the header
     * @return previous value of the header, or null if it didn't exist
     */
    public String setHeader(String header, String value) {
        return super.put(header.toLowerCase(), value);
    }

    /**
     * Put a new header, replacing one if it exists. Header names are stored lowercase.
     * @param header header line, where header name and value are separated by a semicolon
     * @return previous value of the header, or null if it didn't exist
     */
    public String setHeader(String header) {
        String[] tokens = header.split(":", 2);
        return setHeader(tokens[0].trim(), tokens[1].trim());
    }

    /**
     * Append header value if the header with the same name already exists, or put a new header
     * if it doesn't. Header values are separated by a comma.
     * @param header name of the header
     * @param value value of the header
     * @return previous value of the header, or null if it didn't exist
     */
    public String appendHeader(String header, String value) {
        if(containsKey(header)) {
            return super.put(header, get(header) + ", " + value);
        } else {
            return setHeader(header, value);
        }
    }

    /**
     * Append header value if the header with the same name already exists, or put a new header
     * if it doesn't. Header values are separated by a comma.
     * @param header header line, where header name and value are separated by a semicolon
     * @return previous value of the header, or null if it didn't exist
     */
    public String appendHeader(String header) {
        String[] tokens = header.split(":", 2);
        return appendHeader(tokens[0].trim(), tokens[1].trim());
    }

    /**
     * Remove a header if it exists.
     * @param header header name
     * @return previous value of the header, or null if it didn't exist
     */
    public String removeHeader(String header) {
        return remove(header.toLowerCase());
    }

    /**
     * Check whether header exists.
     * @param header header name
     * @return true if it exists, false otherwise
     */
    public boolean hasHeader(String header) {
        return containsKey(header.toLowerCase());
    }

    /**
     * Returns headers in format appropriate for sending, with trailing CRLF.
     * @return String representation of headers
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(size() * 32);
        for(Map.Entry<String, String> headers : entrySet()) {
            builder.append(headers.getKey()).append(": ").append(headers.getValue()).append("\r\n"); //Windows newline, ew
        }
        return builder.toString();
    }
}
