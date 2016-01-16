package com.intel.icecp.module.httpbridge.message;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.intel.icecp.core.Message;
import com.intel.icecp.module.httpbridge.HttpBridge_Module;
import com.intel.icecp.module.httpbridge.HttpBridge_Module.HttpPoolExecutor;
import com.intel.icecp.module.httpbridge.HttpConnectionTask;

/**
 * This message class is for the actual Http Requests. The setup command {@link HttpSetupMessage} returns the connectionId and the data command and command
 * return channels to use for this message.
 * <p>
 * <b>Send Message</b><p>
 * Create a new HttpDataMessage( connectionId ) passing in the connectionId. The httpRequest field is also a required field. Once all the fields are setup for
 * your Http request, then publish this message on the data command channel. If there is input data for the Http request (eg, form data) then publish that data
 * on the inputChannel. The results of this command will be returned on the data command return channel.
 * <p>
 * <b>Results</b><p>
 * The same HttpDataMessage is returned on the data command return channel with the following filled in.<p>
 * status - The HTTP_BRIDGE_STATUS of the call.<p>
 * responseHeaders - Response headers from the request.<p>
 * responseCode - The Http response code returned from the request.<p>
 * responseMessage - The Http response message returned from the request.<p>
 * Also, if there is any output from the Http request, it will be published to the output channel.
 * <p>
 * 
 *
 */
@SuppressWarnings("serial")
public class HttpDataMessage extends HttpBaseMessage implements OnDataCommandMessage {
    /**
     * (optional) The URI for the http input channel. Any input data required for the specified Http request is sent on this channel. The module will read the
     * data from this channel and send it to the Http output stream for the Http request. For example any form data would be published on this channel.
     */
    public URI inputHttpChannelURI;

    /**
     * (optional) Timeout value for the inputHttpChannelURI. This value specifies how many seconds the module will wait for data on the inputHttpChennelURI for
     * the specified Http request. If the timeout expires, the module will no longer wait for input data and continue with the request. Default value is 30
     * seconds.
     */
    public long inputTimeoutSeconds = 30; // default 30 seconds

    /**
     * (optional) The output channel URI where any output from the Http request is sent. If the Http request has any data on its input stream, it will be
     * published to this channel.
     */
    public URI outputHttpChannelURI;

    /**
     * (optional) Path information that is appended to the {@link HttpSetupMessage#connectionUrl} for this command. For example, if the connectionURL is
     * http://myserver.com, this urlPath could be "/api/getName?id=1" creating a complete URL of: http://myserver.com/api/getName?id=1.
     */
    public String urlPath;

    /**
     * (Required) The actual Http request. No restrictions on this, the string is sent directly to the Http server. Typical values are GET, PUT, POST, DELETE,
     * HEAD.
     */
    public String httpRequest;

    /**
     * (optional) Specify the Http request headers in the Map. The format is name value pairs. Example: "Content-Language", "en-US". These values are sent
     * directly to the Http request.
     */
    public Map<String, String> requestHeaders;

    /**
     * (required) Specify whether you want the Http request to use any cached values. True means it is ok to used cached values. False means do not use cached
     * data. Default is false;
     */
    public boolean useCache = false;

    /**
     * These headers are returned from the Http request. The number of headers and values depends on the Http request.
     */
    public Map<String, List<String>> responseHeaders;

    /**
     * The response code returned from the Http request.
     */
    public int responseCode;

    /**
     * The response message returned from the Http Request.
     */
    public String responseMessage;

    /**
     * Specify the connectionId returned from the setup command.
     * 
     * @param connectionId
     *            The id returned from the setup command.
     */
    public HttpDataMessage(long connectionId) {
        super.connectionId = connectionId;
    }

    /**
     * Default constructor, required for serialization. If this constructor is used, be sure to set the connectionId.
     */
    public HttpDataMessage() {
    }

    /**
     * Call the context to execute this command. This method is different than the onCommandMessage(HttpBridge_Module context) because for data commands a
     * connection task is created to handle the request, not the module.
     */
    @Override
    public void onCommandMessage(HttpConnectionTask context) {
        context.executeDataCommand(this);
    }

    /**
     * Validate the command. The HttpRequest must be specified. All other fields are optional. This method takes a connection task not a module context, since
     * the task is handling the data commands.
     */
    @Override
    public String onValidate(HttpConnectionTask context) {
        if (httpRequest == null) {
            status = HTTP_BRIDGE_STATUS.ERROR_ON_SYNTAX;
            return "DataCommand Missing httpRequest";
        }
        status = HTTP_BRIDGE_STATUS.OK;
        return null;
    }

    /**
     * Not used since the Http connction task is the context for data commands.
     */
    @Override
    public void onCommandMessage(HttpBridge_Module context) {
        ;// do nothing
    }

    /**
     * Not used since the http connction task is the context for data commands.
     */
    @Override
    public String onValidate(HttpBridge_Module context) {
        return null; // do nothing
    }

}
