package com.intel.icecp.module.httpbridge;

import java.util.concurrent.CountDownLatch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.intel.icecp.core.Channel;
import com.intel.icecp.core.Node;
import com.intel.icecp.core.metadata.Persistence;
import com.intel.icecp.core.misc.ChannelIOException;
import com.intel.icecp.core.misc.ChannelLifetimeException;
import com.intel.icecp.core.misc.OnPublish;
import com.intel.icecp.module.httpbridge.message.HttpSetupMessage;
import com.intel.icecp.module.httpbridge.message.HttpDataMessage;

/**
 * A {@link java.lang.Runnable} that executes a data command for a specified connection. When a connection is created through the {@link HttpSetupMessage}
 * command, a new HttpConnectionTask runnable is also created to execute the data commands {@link HttpDataMessage}. This task subscribes to the data command
 * channel and waits for {@link HttpDataMessage} commands to execute.
 * <p>
 * When the data command is complete, it returns the updated {@link HttpDataMessage} on the data command return channel
 * 
 *
 */
public class HttpConnectionTask implements Runnable {
    /**
     * The logger for debug messages.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The Node where this Http request is executed from.
     */
    private Node node = null;

    /**
     * The actual setup command {@link HttpSetupMessage}. This message must be a filled in message returned from the setup command. It should include the
     * connectionId and data channels.
     */
    private HttpSetupMessage setupMessage = null;

    /**
     * A CountDownLatch used to wait on until asked to stop execution. The run() methods sets everthing up and then waits on the latch. See {@link
     * HttpConnectionTask#tearDown()}
     */
    private CountDownLatch stopLatch = new CountDownLatch(1);

    /**
     * Constructor for setting up the task.
     * 
     * @param node
     *            The node that executes the Http request.
     * @param setupMessage
     *            The Setup message filled in with connection information.
     */
    public HttpConnectionTask(Node node, HttpSetupMessage setupMessage) {
        this.node = node;
        this.setupMessage = setupMessage;
    }

    /**
     * The run method for this task. Open the return channel to get it ready. Also, open the command channel and subscribe to it.
     */
    @Override
    public void run() {
        Thread.currentThread().setName(String.format("%d_%s", setupMessage.connectionId, setupMessage.connectionUrl));
        logger.info("Running");

        Channel<HttpDataMessage> dataCmdReturnChannel;
        try {
            // Open the data command return channel.
            dataCmdReturnChannel = node.openChannel(setupMessage.dataCmdReturnChannelURI, HttpDataMessage.class, Persistence.NEVER_PERSIST); // new
                                                                                                                                             // Persistence());
        } catch (ChannelLifetimeException e) {
            logger.error("Failed to open dataCmdReturnChannel[{}]", setupMessage.dataCmdReturnChannelURI, e);
            return;
        }

        Channel<HttpDataMessage> dataCmdChannel;
        try {
            dataCmdChannel = node.openChannel(setupMessage.dataCmdChannelURI, HttpDataMessage.class, new Persistence());
            DataCommandCallback dataCmdCallback = new DataCommandCallback();
            dataCmdCallback.dataCmdReturnChannel = dataCmdReturnChannel;
            dataCmdChannel.subscribe(dataCmdCallback);

        } catch (ChannelLifetimeException | ChannelIOException e) {
            logger.error("Failed to setup Data Command Channels", e);
            return;
        }

        waitForTearDown();

        // Clean up and close open channels
        try {
            dataCmdReturnChannel.close();
        } catch (ChannelLifetimeException e) {
            logger.error("Failed to clean up and close data command return channel", e);
        }
        try {
            dataCmdChannel.close();
        } catch (ChannelLifetimeException e) {
            logger.error("Failed to clean up and close data command channel", e);
        }

        logger.info("bye");

    }

    /**
     * Wait for stopLatch to turn to 0 which will indicate the application should terminate. See the tearDown() method.
     */
    private void waitForTearDown() {
        try {
            stopLatch.await();
        } catch (InterruptedException e1) {
            logger.info("Interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stop this runnable. The run() method is waiting on the latch.
     */
    public void tearDown() {
        logger.info("Connection Task asked to teardown");
        stopLatch.countDown();
    }

    /**
     * Callback for the data commands. When a command arrives, ask the data message to first validate and then run the command.
     *
     */
    public class DataCommandCallback implements OnPublish<HttpDataMessage> {

        public Channel<HttpDataMessage> dataCmdReturnChannel;

        @Override
        public void onPublish(HttpDataMessage message) {

            logger.info("Data Command received");
            // First validate the message. If error message,
            // then return status on return channel
            String errorMsg = message.onValidate(HttpConnectionTask.this);
            if (errorMsg != null) {
                logger.info(errorMsg);
                returnDataCmdMessage(message, dataCmdReturnChannel);
            }

            // Execute the command
            message.onCommandMessage(HttpConnectionTask.this);
            returnDataCmdMessage(message, dataCmdReturnChannel);
        }
    }

    /**
     * Execute the actual data command. Create a HttpCommandExecutor() object and ask it to execute the incoming httpRequest.
     * 
     * @param dataCmdMessage
     *            The data command to execute.
     */
    public void executeDataCommand(HttpDataMessage dataCmdMessage) {
        new HttpCommandExecutor(logger, node, setupMessage).executeCommand(dataCmdMessage);
    }

    /**
     * Send the return status on the data command return channel.
     * 
     * @param returnMessage
     *            The return message to send.
     * @param dataCmdReturnChannel
     *            The channel to send the message.
     */
    private void returnDataCmdMessage(HttpDataMessage returnMessage, Channel<HttpDataMessage> dataCmdReturnChannel) {

        try {
            dataCmdReturnChannel.publish(returnMessage);
            logger.info("Publish Return Msg ID[{}] status[{}] Cmd[{}] On[{}]",
                    returnMessage.connectionId, returnMessage.status, returnMessage.getCommand(), returnMessage.cmdReturnChannelURI);
        } catch (ChannelIOException e1) {
            logger.error("Failed to publish on return command channel", e1);
        }
    }
}
