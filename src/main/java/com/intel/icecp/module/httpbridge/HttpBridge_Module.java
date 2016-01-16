package com.intel.icecp.module.httpbridge;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.intel.icecp.core.Channel;
import com.intel.icecp.core.Module;
import com.intel.icecp.core.Node;
import com.intel.icecp.core.metadata.Persistence;
import com.intel.icecp.core.misc.ChannelIOException;
import com.intel.icecp.core.misc.ChannelLifetimeException;
import com.intel.icecp.core.misc.Configuration;
import com.intel.icecp.core.misc.OnPublish;
import com.intel.icecp.core.modules.ModuleProperty;
import com.intel.icecp.module.httpbridge.message.HttpBaseMessage;
import com.intel.icecp.module.httpbridge.message.HttpDataMessage;
import com.intel.icecp.module.httpbridge.message.HttpSetupMessage;
import com.intel.icecp.module.httpbridge.message.HttpBaseMessage.HTTP_BRIDGE_STATUS;
import com.intel.icecp.module.httpbridge.message.HttpTeardownMessage;

/**
 * Http Bridge module for ICECP<p>
 * This module is a bridge for http requests. It will execute a specified Http request, taking the http input from an input channel (eg, POST form data) and
 * sending the output of the request to an output channel (eg, response data).
 * <p>
 * This module listens for commands on its well known command<p>
 * channel: {@link HttpBaseMessage#HTTP_CMD_CHANNEL_NAME}.<p>
 * The steps to setup a connection, send the request and tear it down are:
 * <p>
 * <b>Load</b><p>
 * Load this module onto a ICECP node. Build the jar file using maven and the pom file. Then start up the ICECP Node with this command line argument:<p>
 * -c ../icecp-module-httpbridge/target/icecp-module-httpbridge-0.0.1.jar
 * <p>
 * <b>Setup</b><p>
 * Open the {@link HttpBaseMessage#HTTP_CMD_CHANNEL_NAME} channel.<p>
 * Setup and send an {@link HttpSetupMessage} message on the command channel.<p>
 * Receive the Setup message back on user specified return command channel, check status<p>
 * The return message contains a connectionId, data command channel and data command return channel
 * <p>
 * <b>Data</b><p>
 * Open the data command channel returned from the setup message<p>
 * Open the data command return channel returned from setup, and subscribe to it.<p>
 * Setup and send a {@link HttpDataMessage} on the data command channel<p>
 * (optionally) Send data on the input channel<p>
 * (optionally) Open and subscribe to the output channel<p>
 * Receive the Data message back on data command return channel, check status<p>
 * (optionally) Receive data back on the output channel<p>
 * (optionally) Repeat the Data command
 * <p>
 * <b>Teardown</b><p>
 * Setup and send an {@link HttpTeardownMessage} message on the command channel<p>
 * Receive the Teardown message back on user specified return command channel, check status
 * <p>
 * See each message class for more info<p>
 * 
 *
 */

@ModuleProperty(name = "HttpBridge_Module")
public class HttpBridge_Module implements Module {
    private static final Logger logger = LogManager.getLogger(HttpBridge_Module.class.getName());

    private Node node = null;
    private CountDownLatch stopLatch = new CountDownLatch(1);
    private final Random randomGenerator = new SecureRandom();
    private ConcurrentMap<Long, ConnectionDetail> connections = new ConcurrentHashMap<>();

    private final int MAX_HTTP_THREADS = 15;
    private HttpPoolExecutor httpPoolExecutor = new HttpPoolExecutor(0, MAX_HTTP_THREADS, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
    private ClassLoader moduleClassLoader = Thread.currentThread().getContextClassLoader();
    
    /**
     * Run() method of the Module interface. Everything starts here.
     * 
     * Open the well known {@link HttpSetupMessage#HTTP_CMD_CHANNEL_NAME} for this module and subscribe to it. When stopped, clean up and exit.
     * 
     */
    public void run(Node node, Configuration moduleConfiguration, Channel<State> moduleStateChannel, long moduleId) {
        String cmdChannelName = node.getDefaultUri().toString() + "/" + HttpSetupMessage.HTTP_CMD_CHANNEL_NAME;
        this.node = node;

        try {
            moduleStateChannel.publish(State.RUNNING);
        } catch (ChannelIOException e) {
            logger.error("Failed to publish RUNNING state {}", e);
            return;
        }
        logger.info("Running...");

        try (Channel<HttpBaseMessage> cmdChannel = node.openChannel(new URI(cmdChannelName), HttpBaseMessage.class, new Persistence())){
            cmdChannel.subscribe(new CommandCallback());
            logger.info("Cmd Channel {} open and subscribed to", cmdChannel.getName());
        } catch (ChannelLifetimeException | URISyntaxException e) {
            logger.error("Failed to open/setup the command channel", e);
            return;
        } catch (ChannelIOException e) {
            logger.error("Failed to subscribe to command channel", e);
            return;
        }

        waitForTearDown();
        httpPoolExecutor.shutdownNow();
    }

    /**
     * Call back for the {@link HttpBaseMessage#HTTP_CMD_CHANNEL_NAME}. All commands arrive here, and are carried out by the module.
     * 
     */
    public class CommandCallback implements OnPublish<HttpBaseMessage> {
        @Override
        public void onPublish(HttpBaseMessage message) {
            logger.info("Command [{}] received", message.getCommand());
            String errorMsg = message.onValidate(HttpBridge_Module.this);
            if (errorMsg != null) {
                logger.info(errorMsg);
                returnCommandMessage(message);
            }

            message.onCommandMessage(HttpBridge_Module.this);
        }
    }

    /**
     * Handle the incoming setup command by creating a connection. A connection consists of a connectionId, a {@link HttpConnectionTask}, a
     * {@link HttpWrapperTask}, and unique data command and data command return channel names. A ConnectionDetail object is created, filled in and stored in
     * connections. The {@link HttpConnectionTask} is started via the httpPoolExecutor and it will listen for data commands on this connectionId.
     * 
     * The connectionId and data channel names are returned for subsequent commands.
     * 
     * @param setupCommand
     *            The HttpSetupMessage containing parameters for the setup command.
     */
    public void setupCommand(HttpSetupMessage setupCommand) {
        try {
            ConnectionDetail connectionDetail = setupConnection(setupCommand);
            logger.info("SetupCommand id[{}]", connectionDetail.commandMsg.connectionId);
            connections.put(setupCommand.connectionId, connectionDetail);
            httpPoolExecutor.execute(connectionDetail.wrapperTask);
        } catch (URISyntaxException e) {
            logger.error("Error setting up connection", e);
            setupCommand.status = HTTP_BRIDGE_STATUS.ERROR_ON_SYNTAX;
        }
        logger.debug("SetupCommand - send return message");
        returnCommandMessage(setupCommand);
    }

    /**
     * Handle the incoming tear down command. The connectionId in the message is used to lookup the connection. If found, tell the {@link HttpConnectionTask} to
     * tear down, then clean up and remove the connection. If connection id is not found, just return.
     * 
     * @param tearDownCommand
     *            The {@link HttpTeardownMessage} containing the connectionId to tear down.
     */
    public void tearDownCommand(HttpTeardownMessage tearDownCommand) {
        logger.info("TearDownCommand id[{}]", tearDownCommand.connectionId);
        ConnectionDetail bridgeConnectionObject = connections.get(tearDownCommand.connectionId);
        if (bridgeConnectionObject != null) {
            if (bridgeConnectionObject.wrapperTask != null)
                bridgeConnectionObject.wrapperTask.tearDown();
            connections.remove(tearDownCommand.connectionId);
        }
        returnCommandMessage(tearDownCommand);
    }

    /**
     * Helper method to indicate if the specified connection is actually connected and setup.
     * 
     * @param connectionId
     *            The connectionId to look up.
     * @return True=connectionId is valid and setup, False=connectionId not found.
     */
    public boolean isConnected(long connectionId) {
        return (connections.get(connectionId) != null);
    }

    private ConnectionDetail setupConnection(HttpSetupMessage message) throws URISyntaxException {
        final String dataCmdChannel = "HTTPBridge-DATACMD";
        final String dataCmdReturnChannel = "HttpBridge-DATACMDRETURN";

        message.connectionId = createConnectionId();
        message.dataCmdChannelURI = new URI(String.format("%s/%s-%d", node.getDefaultUri(), dataCmdChannel, message.connectionId));
        message.dataCmdReturnChannelURI = new URI(String.format("%s/%s-%d", node.getDefaultUri(), dataCmdReturnChannel, message.connectionId));
        message.status = HTTP_BRIDGE_STATUS.OK;

        // Create and fill in the connection detail, then return it.
        ConnectionDetail conx = new ConnectionDetail(logger, node, message);
        conx.httpConnectionTask = new HttpConnectionTask(node, message);
        conx.wrapperTask = new HttpWrapperTask(conx.httpConnectionTask, message.connectionId);
        return conx;
    }

    private long createConnectionId() {
        long id;
        do {
            id = randomGenerator.nextLong();
        } while (connections.containsKey(id));
        return id;
    }

    /**
     * Helper method to return the status on the return channel. The message is setup and ready to go.
     * 
     * @param returnMessage
     *            - the message to send on the return channel.
     */
    private void returnCommandMessage(HttpBaseMessage returnMessage) {
        try (Channel<HttpBaseMessage> cmdReturnChannel = node.openChannel(returnMessage.cmdReturnChannelURI, HttpBaseMessage.class, Persistence.NEVER_PERSIST)) {
            cmdReturnChannel.publish(returnMessage);
            /**
             * Since the channel is opened with no persistence, sleep for a short time to make sure the subscriber gets the message before closing the channel.
             * If the # bytes to publish is small, its possible the subscriber can miss out.
             */
            final long sleepTimeInMs = 1000;
            Thread.sleep(sleepTimeInMs);
            logger.info("Publish Return Msg ID[{}] status[{}] Cmd[{}] On[{}]", returnMessage.connectionId, returnMessage.status,
                    returnMessage.getCommand(), returnMessage.cmdReturnChannelURI);
        } catch (ChannelLifetimeException | ChannelIOException e1) {
            logger.error("Failed to publish on return command channel", e1);
        } catch (InterruptedException ie) {
            logger.info("Interrupted.");
            Thread.currentThread().interrupt();
        }
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
     * The stop() method of the Module interface.
     */
    public void stop(StopReason reason) {
        logger.info("Stopped for this reason: " + reason);
        stopLatch.countDown();
    }

    /**
     * Connection object to store the information for a connection. This object is stored in the map, and used to control the connection.
     */
    public class ConnectionDetail {
        private HttpSetupMessage commandMsg;
        private HttpCommandExecutor commandExecutor;
        private HttpWrapperTask wrapperTask;
        private HttpConnectionTask httpConnectionTask;

        ConnectionDetail(Logger logger, Node node, HttpSetupMessage commandMsg) {
            this.commandMsg = commandMsg;
            this.commandExecutor = new HttpCommandExecutor(logger, node, this.commandMsg);
        }
    }

    /**
     * Extend the ThreadPoolExecutor that we use for starting the http connection threads. This allows us to override the beforeExecute() method and set the
     * threads context class loader to our module loader. This is required to support ServiceLoaders that use their default ExtensionLoaders. We set our threads
     * context loader to the ModuleClassLoader so that the ServiceLoader providers can be found.
     *
     */
    public class HttpPoolExecutor extends ThreadPoolExecutor {
        HttpPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            Thread.currentThread().setContextClassLoader(moduleClassLoader);
        }
    }
}
