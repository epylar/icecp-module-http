package com.intel.icecp.module.httpbridge.message;

import com.intel.icecp.module.httpbridge.HttpConnectionTask;

/**
 * This interface is used by the HttpDataMessage. These messages context is the HttpConnectionTask.
 * 
 *
 */
public interface OnDataCommandMessage<M extends HttpConnectionTask> {

    public void onCommandMessage(M context);

    public default String onValidate(M context) {
        return null;
    }
}
