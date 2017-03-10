/*
 * Copyright (c) 2017 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intel.icecp.module.httpbridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.Logger;

import com.intel.icecp.core.Channel;
import com.intel.icecp.core.Node;
import com.intel.icecp.core.messages.BytesMessage;
import com.intel.icecp.core.metadata.Persistence;
import com.intel.icecp.core.misc.ChannelIOException;
import com.intel.icecp.core.misc.ChannelLifetimeException;
import com.intel.icecp.module.httpbridge.message.HttpSetupMessage;
import com.intel.icecp.module.httpbridge.message.HttpDataMessage;
import com.intel.icecp.module.httpbridge.message.HttpBaseMessage.HTTP_BRIDGE_STATUS;

/**
 * This helper class executes the specified Http request.  In the constructor, the {@link HttpSetupMessage} is passed in, which contains all of the
 * connection information. Then the executeCommand() method is used to make the Http request.
 * 
 *
 */
public class HttpCommandExecutor {

    private final int READ_SIZE = 1024 * 16;

    /**
     * The logger for debug messages.
     */
    private static Logger logger = null;

    /**
     * The Node where this Http request is executed from.
     */
    private Node node = null;

    /**
     * The actual setup command {@link HttpSetupMessage}.  This message must be a filled in message returned from the setup command.  It should
     * include the connectionId and data channels.
     */
    private HttpSetupMessage setupCmdMessage = null;

    /**
     * Constructor - sets up the parameters for the request.
     * 
     * @param logger
     *            The logger for messages
     * @param node
     *            The node to use for the command
     * @param setupCmdMessage
     *            Contains the connection information for the request.
     */
    public HttpCommandExecutor(Logger logger, Node node, HttpSetupMessage setupCmdMessage) {
        this.logger = logger;
        this.node = node;
        this.setupCmdMessage = setupCmdMessage;
    }

    /**
     * Execute the specified {@link HttpDataMessage} for the connectionId.  This method builds the connection using the connection parameters in the
     * setupCmdMessage.  Then it executes the Http request using the parameters in the data command message.
     *
     * @param dataCmdMessage
     *            Contains the Http request and all its parameters. The status of this request is set in the status field of the incoming dataCmdMessage.
     */
    protected void executeCommand(HttpDataMessage dataCmdMessage) {
        dataCmdMessage.status = HTTP_BRIDGE_STATUS.OK;

        Channel<BytesMessage> outputChannel = null;
        HttpURLConnection connection = null;
        try {
            connection = createConnection(); // TODO: Should we support HttpsURLConnection?
            verifyConnection(connection);
            setConnectionProperties(connection, dataCmdMessage.httpRequest, dataCmdMessage.requestHeaders, dataCmdMessage.useCache);
            setupInputChannel(dataCmdMessage.inputHttpChannelURI, connection);
            outputChannel = setupOutputChannel(dataCmdMessage.outputHttpChannelURI, connection);

            connection.connect();
            getResponseCodes(dataCmdMessage, connection);
            if (connection.getDoOutput()) {
                getInputData(connection, dataCmdMessage.inputHttpChannelURI, dataCmdMessage.inputTimeoutSeconds);
            }
            if (outputChannel != null && connection.getDoInput()) {
                sendOutputData(connection, outputChannel);
            }
        } catch (HttpConnectionException e) {
            dataCmdMessage.status = HTTP_BRIDGE_STATUS.ERROR_ON_CONNECT;
        } catch (IOException e) {
            dataCmdMessage.status = HTTP_BRIDGE_STATUS.ERROR_ON_IO;
        } catch (HttpResponseException ex) {
            dataCmdMessage.status = HTTP_BRIDGE_STATUS.ERROR_ON_RESPONSE;
        } finally {
            cleanupConnection(connection, outputChannel);
        }
    }

    /**
     * Ensure the connection was created successfully.
     *
     * @param connection
     *            The connection to be verified.
     * @throws HttpConnectionException
     *            The passed in connection is null
     */
    protected void verifyConnection(HttpURLConnection connection) throws HttpConnectionException {
        if (connection == null) {
            throw new HttpConnectionException("No connection was created.");
        }
    }

    /**
     * Create the HttpURLConnection using the setup message passed into the constructor.  The setup message has the connection URL, and any other
     * parameters for the connect.
     *
     * @return The HttpURLConnection if successful.
     * @throws HttpConnectionException
     *             if not successful.
     */
    protected HttpURLConnection createConnection() throws HttpConnectionException {
        try {
            if (setupCmdMessage.proxyHost != null && !setupCmdMessage.proxyHost.isEmpty()) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(setupCmdMessage.proxyHost, setupCmdMessage.proxyPort));
                logger.info("openConnection [{}] using proxy[{}:{}]", setupCmdMessage.connectionUrl.toString(),
                        setupCmdMessage.proxyHost, setupCmdMessage.proxyPort);
                return (HttpURLConnection) setupCmdMessage.connectionUrl.openConnection(proxy);
            } else {
                logger.info("openConnection [{}], no Proxy", setupCmdMessage.connectionUrl.toString());
                return (HttpURLConnection) setupCmdMessage.connectionUrl.openConnection();
            }
        } catch (IOException e) {
            logger.error("Failed to openConnection", e);
            throw new HttpConnectionException(e);
        }
    }

    /**
     * Setup the properties for the connection.  This includes the httpRequest, the headers and other flags.
     *
     * @param connection
     *            The current connection.
     * @param httpRequest
     *            The httpRequest. One of (POST,PUT,GET,DELETE,HEAD,OPTIONS,TRACE)
     * @param requestHeaders
     *            The headers to be sent with this request.  A list of "name", "value" pairs.
     * @param useCache
     *            True=ok to use the cached data, false=do not use cached data
     * @throws HttpConnectionException
     *            Issue with the protocol or current state
     *
     */
    protected void setConnectionProperties(HttpURLConnection connection, String httpRequest, Map<String, String> requestHeaders, boolean useCache)
            throws HttpConnectionException {
        try {
            connection.setRequestMethod(httpRequest);

            for (Map.Entry<String, String> prop : requestHeaders.entrySet()) {
                logger.info("Set Property Name[{}] value[{}]", prop.getKey(), prop.getValue());

                // Do not allow null keys.
                if (prop.getKey() != null) {
                    connection.setRequestProperty(prop.getKey(), prop.getValue());
                }
            }
        } catch (ProtocolException | IllegalStateException e) {
            throw new HttpConnectionException(e);
        }

        connection.setUseCaches(useCache);
    }

    /**
     * If the inputChannel is specified then set the DoOutput flag in the connection.  Not all httpRequests require input (eg, HEAD, GET), so only setup
     * this channel if it is specified in the DataCommand.  See {@link HttpCommandExecutor#getInputData}
     * <p>
     * The bytes read from the inputChannel are sent to the http connections outputStream
     *
     * @param inputHttpChannelURI
     *            If specified, set the DoOutput flag
     * @param connection
     *            The current connection
     * @throws HttpConnectionException
     *            Issue with the connection
     */
    protected void setupInputChannel(URI inputHttpChannelURI, HttpURLConnection connection) throws HttpConnectionException {
        connection.setDoOutput(inputHttpChannelURI != null);
    }

    /**
     * If the outputChannel is specified then open it, and set the DoInput flag.  Not all httpRequests require input (eg, HEAD), so only setup this channel
     * if it is specified in the DataCommand.  See {@link HttpCommandExecutor#sendOutputData}
     * <p>
     * The bytes read from the http connections inputStream are published
     * to this outputChannel
     *
     * @param outputHttpChannelURI
     *            If specified, the output channel to open.
     * @param connection
     *            The current connection
     * @return Return a {@code Channel<BytesMessage>} if an outputHttpChannelURI is specified and successfully opened.  Null otherwise.
     *         Note: returning null may not be an error.
     * @throws HttpConnectionException
     *         Issue with the connection
     */
    protected Channel<BytesMessage> setupOutputChannel(URI outputHttpChannelURI, HttpURLConnection connection) throws HttpConnectionException {
        Channel<BytesMessage> outputChannel = null;
        connection.setDoInput(false);

        if (outputHttpChannelURI != null) {
            logger.info("Setup Output Channel from http input stream");
            try {
                outputChannel = node.openChannel(outputHttpChannelURI, BytesMessage.class, new Persistence());
                connection.setDoInput(true);
            } catch (ChannelLifetimeException e) {
                logger.error("Failed to open input channel", e);
                throw new HttpConnectionException(e);
            }
        }

        return outputChannel;
    }

    /**
     * Get the response from the connection.  This includes the responseCode, responseMessage and responseHeaders.
     *
     * @param dataCmdMessage
     *            The results are stored in this message and returned on the returnChannel.
     * @param connection
     *            The current connection.
     * @throws HttpResponseException
     *            The HTTP response resulted in an exception
     */
    protected void getResponseCodes(HttpDataMessage dataCmdMessage, HttpURLConnection connection) throws HttpResponseException {
        try {
            dataCmdMessage.responseCode = connection.getResponseCode();
            dataCmdMessage.responseMessage = connection.getResponseMessage();
            logger.info("ResponseMessage={}, ResponseCode={}", dataCmdMessage.responseMessage, dataCmdMessage.responseCode);
            // TODO: What errors should we handle here?
            if (dataCmdMessage.responseCode >= 400)
                throw new HttpResponseException(String.format("Http Error [%d] %s",
                        dataCmdMessage.responseCode, dataCmdMessage.responseMessage));
        } catch (IOException e) {
            logger.error("Failed to get response codes and messages", e);
            throw new HttpResponseException(e);
        }

        /**
         * Copy the response headers from the connection, into a new HashMap. This is done because sometimes a map entry has a null key and json does not map
         * null keys without some work.
         *
         * TODO: Is it ok to omit the null key response headers?
         */
        Map<String, List<String>> respHeaders = connection.getHeaderFields();
        dataCmdMessage.responseHeaders = new HashMap<>();

        for (Map.Entry<String, List<String>> prop : respHeaders.entrySet()) {

            logger.debug("ResponseHeader Name[{}] values[{}]", prop.getKey(), getResponseValues(prop.getValue()));
            if (prop.getKey() != null) {
                dataCmdMessage.responseHeaders.put(prop.getKey(), prop.getValue());
            }
        }
    }

    /**
     * Convert the incoming list of strings into a single string separated by a space. This is just a debugging method.
     *
     * @param hdrValues
     *            List of strings.
     * @return String containing all of the strings in the incoming list, separated by a space.
     */
    private String getResponseValues(List<String> hdrValues) {
        StringBuilder values = new StringBuilder();
        for (String val : hdrValues) {
            values.append(val).append(" ");
        }
        return values.toString();
    }

    /**
     * Open the inputChannel and read the bytes using the timeout value.  When the bytes arrive they are sent to the http connections output stream.
     * 
     * @param connection
     *            The existing HttpURLConnection
     * @param inputHttpChannelURI
     *            The URI for the input channel
     * @param inputTimeoutSeconds
     *            The timeout in seconds to wait for the input bytes.
     * @throws HttpResponseException
     *            The HTTP response resulted in an exception
     */
    protected void getInputData(HttpURLConnection connection, URI inputHttpChannelURI, long inputTimeoutSeconds) throws HttpResponseException {
        BytesMessage inputMessage;
        try (Channel<BytesMessage> inputChannel = node.openChannel(inputHttpChannelURI, BytesMessage.class, new Persistence())) {
            inputMessage = inputChannel.latest().get(inputTimeoutSeconds, TimeUnit.SECONDS);
        } catch (ChannelLifetimeException | ExecutionException | ChannelIOException | InterruptedException e) {
            logger.error("Failed to read from input channel", e);
            throw new HttpResponseException(e);
        } catch (TimeoutException e) {
            logger.error("Timed out waiting for input data", e);
            throw new HttpResponseException(e);
        }

        logger.info("Input bytes [{}] received", inputMessage.getBytes().length);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(inputMessage.getBytes());
        } catch (IOException e) {
            logger.error("Exception reading from output stream", e);
            throw new HttpResponseException(e);
        }
    }

    /**
     * Send the bytes from the http connection (inputStream) to the outputChannel.
     *
     * @param connection
     *            The current httpConnection
     * @param outputChannel
     *            The opened output channel to send the bytes
     * @throws HttpResponseException
     *            The HTTP response resulted in an exception
     */
    protected void sendOutputData(HttpURLConnection connection, Channel<BytesMessage> outputChannel) throws HttpResponseException {
        // Both InputStream and ByteArrayOutputStream implement Closeable which means
        // even though the InputStream gets closed twice, its supported.
        int nRead;
        byte[] data = new byte[READ_SIZE];
        try (InputStream is2 = connection.getInputStream()) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                while ((nRead = is2.read(data, 0, data.length)) != -1) {
                    bos.write(data, 0, nRead);
                }
                data = bos.toByteArray();
            }
        } catch (IOException ioe2) {
            logger.error("Exception reading from input stream", ioe2);
            throw new HttpResponseException(ioe2);
        }

        // Send the bytes to the output channel
        try {
            if (data.length > 0) {
                outputChannel.publish(new BytesMessage(data));
                logger.info("Published [{}] response bytes to channel: {}", data.length, outputChannel.getName());
            }
            else
                logger.info("Did not publish to output channel, byte length=0");
        } catch (ChannelIOException e) {
            logger.error("Failed to publish response bytes", e);
            throw new HttpResponseException(e);
        }
    }

    /**
     * Clean up and tear down the current connection.
     *
     * @param connection
     *            The connection to disconnect().
     * @param outputChannel
     *            The output channel to close
     */
    protected void cleanupConnection(HttpURLConnection connection, Channel<BytesMessage> outputChannel) {
        closeIOChannel(outputChannel);
        if (connection != null) {
            connection.disconnect();
        }
    }

    /**
     * Helper function for cleanup
     *
     * @param channel
     *            the IO Channel to close
     */
    protected void closeIOChannel(Channel<BytesMessage> channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (ChannelLifetimeException e2) {
                logger.info("Failed to close Channel: " + channel.getName());
            }
        }
    }
}
