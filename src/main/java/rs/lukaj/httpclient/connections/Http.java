package rs.lukaj.httpclient.connections;

/**
 * Wrapper class for HTTP properties.
 */
public class Http {

    /**
     * Denotes a request method (i.e. "http verb")
     */
    public enum Verb {
        GET("GET", true, P.RESP_BODY & P.SAFE & P.IDEMPOTENT & P.CACHEABLE),
        POST("POST", true, P.REQ_BODY_MUST & P.RESP_BODY & P.CACHEABLE),
        PUT("PUT", true, P.REQ_BODY_MUST & P.RESP_BODY & P.IDEMPOTENT),
        DELETE("DELETE", true, P.RESP_BODY & P.IDEMPOTENT),

        //extras: might work, might not, but warning is printed when used nonetheless
        HEAD("HEAD", false, P.SAFE & P.IDEMPOTENT & P.CACHEABLE),
        CONNECT("CONNECT", false, P.RESP_BODY),
        OPTIONS("OPTIONS", false, P.RESP_BODY & P.IDEMPOTENT & P.SAFE),
        TRACE("TRACE", false, P.REQ_BODY_MUSTNT & P.RESP_BODY & P.SAFE & P.IDEMPOTENT),
        PATCH("PATCH", false, P.REQ_BODY_MUST & P.RESP_BODY);

        private static class P { //hack around illegal forward reference
            private static final long REQ_BODY_MUST = 1; //does this request must contain request body
            private static final long REQ_BODY_MUSTNT = 1 << 1; //can this request contain request body (set if can't)
            private static final long RESP_BODY = 1 << 2; //expecting a response with body?
            private static final long SAFE = 1 << 3;
            private static final long IDEMPOTENT = 1 << 4;
            private static final long CACHEABLE = 1 << 5;
        }

        private String text;
        private boolean supported;
        private long properties;

        Verb(String text, boolean supported, long properties) {
            this.text = text;
            this.supported = supported;
            this.properties = properties;
        }

        /**
         * Returns true if method is officially supported. If this method returns false, request may still work, but
         * no guarantees are given (it's up to the caller to ensure everything is in its place, e.g. appropriate
         * headers).
         * @return whether this method is supported
         */
        public boolean isSupported() {
            return supported;
        }

        /**
         * Denotes whether request body is mandatory. If it is, then body must be provided, along with appropriate
         * headers, even if empty.
         * @return whether request body is mandatory
         */
        public boolean mustProvideRequestBody() {
            return (properties & P.REQ_BODY_MUST) != 0;
        }

        /**
         * Denotes whether request body is allowed. If it isn't, then request cannot contain body.
         * @return whether request body is allowed
         */
        public boolean canProvideRequestBody() {
            return (properties & P.REQ_BODY_MUSTNT) == 0;
        }

        /**
         *
         * @return whether response has body
         */
        public boolean responseHasBody() {
            return (properties & P.RESP_BODY) != 0;
        }

        /**
         * If method is safe, then requests shouldn't change resource representation.
         * @return whether method is safe
         */
        public boolean isMethodSafe() {
            return (properties & P.SAFE) != 0;
        }

        /**
         * If method is idempotent, request can be made multiple times with the same outcome.
         * @return whether method is idempotent
         */
        public boolean isMethodIdempotent() {
            return (properties & P.IDEMPOTENT) != 0;
        }

        /**
         * Denotes whether response can be cached. This is further explained by response headers.
         * @return whether response is cacheable
         */
        public boolean isResponseCacheable() {
            return (properties & P.CACHEABLE) != 0;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * HTTP version used for requests. This client is modelled for HTTP 1.1, but some other versions
     * might work with careful header tuning.
     */
    public enum Version {
        HTTP10("HTTP/1.0", false),
        HTTP11("HTTP/1.1", true),
        HTTP20("HTTP/2.0", false);
        //HTTP3 works over UDP (for now), so listing it here is futile

        private String text;
        private boolean supported;

        Version(String text, boolean supported) {
            this.text = text;
            this.supported = supported;
        }

        public boolean isSupported() {
            return supported;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
