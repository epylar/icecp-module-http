package com.intel.icecp.module.httpbridge.message;

import java.net.URI;
import java.net.URL;

import com.intel.icecp.core.Message;
import com.intel.icecp.module.httpbridge.HttpBridge_Module;
import com.intel.icecp.module.httpbridge.HttpBridge_Module.HttpPoolExecutor;

/**
 * This message class is for tearing down a connection.
 * <p>
 * <b>Send Message</b><p>
 * Create a new HttpTeardownMessage( connectionId, URI returnCmdChannelURI ) and specify the required connectionId and return channel.  Publish this
 * message on the {@link HttpBaseMessage#HTTP_CMD_CHANNEL_NAME} channel.
 * <p>
 * <b>Results</b><p>
 * The same HttpTeardownMessage is returned on the return channel with the following filled in.<p>
 * status - The HTTP_BRIDGE_STATUS of the call.
 *
 */
@SuppressWarnings("serial")
public class HttpTeardownMessage extends HttpBaseMessage {
    /**
     * Pass in the connectionId of the connection you want to tear down.  Also pass in the return channel.
     * 
     * @param connectionId
     *            The connection to tear down.
     * @param cmdReturnChannelURI
     *            The channel to receive the status of the command.
     */
    public HttpTeardownMessage(long connectionId, URI cmdReturnChannelURI) {
        super.cmdReturnChannelURI = cmdReturnChannelURI;
        super.connectionId = connectionId;
    }

    /**
     * Default constructor, required for serialization. If this constructor is used, be sure to set the connectionId and cmdReturnChannelURI fields.
     */
    public HttpTeardownMessage() {
    }

    /**
     * Call the context to execute the setup command.
     */
    @Override
    public void onCommandMessage(HttpBridge_Module context) {
        context.tearDownCommand(this);
    }

    /**
     * Validate the incoming teardown command.  Make sure the connectionId id valid.
     * 
     * @return Status message. If an error found, the error message is returned.  If no error, return null.
     */
    @Override
    public String onValidate(HttpBridge_Module context) {
        if (!context.isConnected(connectionId)) {
            status = HTTP_BRIDGE_STATUS.ERROR_ON_SYNTAX;
            return ("ConnectionId not found");
        }

        status = HTTP_BRIDGE_STATUS.OK;
        return null;
    }

}
