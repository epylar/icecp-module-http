package com.intel.icecp.module.httpbridge;

/**
 * Specifies that the HTTP connection failed
 *
 */
public class HttpConnectionException extends Exception {

    /**
     * Creates a new instance of <code>HttpConnectionFailure</code> without detail message.
     */
    public HttpConnectionException() {
    }

    /**
     * Constructs an instance of <code>HttpConnectionFailure</code> with the specified detail message.
     *
     * @param msg
     *            the detail message.
     */
    public HttpConnectionException(String msg) {
        super(msg);
    }

    /**
     * Build an exception containing another exception
     *
     * @param ex Original exception
     */
    public HttpConnectionException(java.lang.Exception ex) {
        super(ex);
    }
}
