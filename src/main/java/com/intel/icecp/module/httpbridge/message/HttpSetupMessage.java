package com.intel.icecp.module.httpbridge.message;

import java.net.URI;
import java.net.URL;

import com.intel.icecp.module.httpbridge.HttpBridge_Module;

/**
 * This message is the first message sent to the module, to setup your Http Request. The setup message creates a connection and returns to you the channels to
 * use for the actual Http request.
 * <p>
 * <b>Send Message</b><p>
 * Create a new HttpSetupMessage( returnCmdChannelURI ) passing in the required URI for the return channel.  The connectionURL is also required and it
 * represents where the Http request will be sent (eg, example: https://eap-tst.intel.com).  The optional proxy parameters can be specified too.  Once
 * all the parameters are filled in, publish this message on the {@link HttpBaseMessage#HTTP_CMD_CHANNEL_NAME} channel.  The results will be published on the
 * returnCmdChannelURI channel. <p>
 * <b>Results</b><p>
 * The same HttpSetupMessage is returned on the command return channel with the following filled in.<p>
 * status - The HTTP_BRIDGE_STATUS of the call.  If not HTTP_BRIDGE_STATUS.OK, connectionId is invalid.<p>
 * connectionId - This unique id is returned. Use this for all subsequent commands.<p>
 * dataCmdChannelURI - The URI to send {@link HttpDataMessage} commands.<p>
 * dataCmdReturnChannelURI - The URI to receive {@link HttpDataMessage} status messages.
 * <p>
 * 
 */
@SuppressWarnings("serial")
public class HttpSetupMessage extends HttpBaseMessage {
    /**
     * The URL for the Http request. For example: http://myServer.com. This field is required.
     */
    public URL connectionUrl;

    /**
     * (optional) The Proxy host if needed. If the proxy is specified, it is used with the proxyPort to setup a proxy for the Http request. If no proxy is
     * needed, leave this empty.
     */
    public String proxyHost;

    /**
     * (optional) The proxy port if the proxy host is specified.
     */
    public int proxyPort;

    /**
     * A data command channel URI is returned from the setup command. This channel name is unique for this connection. Use this channel to send your Data
     * messages to the module.
     */
    public URI dataCmdChannelURI;

    /**
     * A data command return channel URI is returned from the setup command. This channel receives status for any data messages.
     */
    public URI dataCmdReturnChannelURI;

    /**
     * Pass in the command return channel URI when creating this messages class. The module will return status on this channel.
     * 
     * @param returnCmdChannelURI
     *            A URI for sending the results of this Setup command.
     */
    public HttpSetupMessage(URI returnCmdChannelURI) {
        super.cmdReturnChannelURI = returnCmdChannelURI;
        super.connectionId = 0;
    }

    /**
     * Default constructor, required for serialization. If this constructor is used, be sure to set the cmdReturnChannelURI field.
     */
    public HttpSetupMessage() {
    }

    /**
     * Call the context to execute the setup command.
     */
    @Override
    public void onCommandMessage(HttpBridge_Module context) {
        context.setupCommand(this);
    }

    /**
     * Validate the incoming setup command.  Verify the required return channel URI and connectionURL. 
     * 
     * @return Status message. If an error found, the error message is returned.  If no error, return null.
     */
    @Override
    public String onValidate(HttpBridge_Module context) {
        status = HTTP_BRIDGE_STATUS.ERROR_ON_SYNTAX;
        if (cmdReturnChannelURI == null)
            return "SetupCommand: Missing Return Command Channel URI";
        if (connectionUrl == null)
            return "SetupCommand: Missing Connection URL";

        status = HTTP_BRIDGE_STATUS.OK;
        return null;
    }

}
