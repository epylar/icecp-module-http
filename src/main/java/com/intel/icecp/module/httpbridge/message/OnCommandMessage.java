package com.intel.icecp.module.httpbridge.message;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.intel.icecp.core.Module;

public interface OnCommandMessage<M extends Module> {
    /**
     * Default implementation of executing the onCommandMessage in a pool To run this synchronously in a pool, call {@code poolCommandMessage (...).get();}
     * 
     * @param executorServive Executor service
     * @param context Contect passed tothe executor service
     * @return Future to test for completion.
     */
    public default Future<?> poolCommandMessage(ExecutorService executorServive, M context) {
        // WrapperTask wrapperTask = new WrapperTask(new Runnable(), connectionId);
        return executorServive.submit(new Runnable() {
            @Override
            public void run() {
                onCommandMessage(context);
            }
        });
    }

    public void onCommandMessage(M context);

    public default String onValidate(M context) {
        return null;
    }
}
