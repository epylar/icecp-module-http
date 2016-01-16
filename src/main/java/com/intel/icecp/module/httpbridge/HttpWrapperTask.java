package com.intel.icecp.module.httpbridge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class to wrap the HttpConnectionTask class when passing to the ThreadPoolExecutor. This wrapper will "catch" any exceptions thrown and log the error.
 * Without this wrapper, these exceptions are caught by the Executor and not reported.
 * 
 *
 */
public class HttpWrapperTask implements Runnable {
    /**
     * The logger for debug messages.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The HttpConnectionTask to wrap.
     */
    private HttpConnectionTask httpTask;

    /**
     * The connectionId for the task.
     */
    private long id;

    /**
     * Store the incoming parameters.
     * 
     * @param httpTask Task to wrap
     * @param id Connection ID for the task
     */
    public HttpWrapperTask(HttpConnectionTask httpTask, long id) {
        this.httpTask = httpTask;
        this.id = id;
    }

    /**
     * This call method is called by the Executor.  It simply calls the HttpConnectionTask run method and catches any exceptions.
     */
    @Override
    public void run() {
        logger.info("Running");
        try {
            httpTask.run();
        } catch (Exception ex) {
            logger.error("Exception for HttpTask {}", id, ex);
        } catch (Throwable th) {
            logger.error("Throwable for HttpTask {}", id, th);
        }
    }

    /**
     * Call this method to stop the task.
     */
    public void tearDown() {
        httpTask.tearDown();
    }
}
