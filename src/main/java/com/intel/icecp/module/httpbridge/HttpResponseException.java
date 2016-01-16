package com.intel.icecp.module.httpbridge;

/**
 * Specifies that the HTTP request or response failed
 *
 */
public class HttpResponseException extends Exception {

    /**
     * Creates a new instance of <code>HttpDataException</code> without detail message.
     */
    public HttpResponseException() {
    }

    /**
     * Constructs an instance of <code>HttpDataException</code> with the specified detail message.
     *
     * @param msg
     *            the detail message.
     */
    public HttpResponseException(String msg) {
        super(msg);
    }

    /**
     * Build an exception containing another exception
     *
     * @param ex Original exception
     */
    public HttpResponseException(java.lang.Exception ex) {
        super(ex);
    }
}
