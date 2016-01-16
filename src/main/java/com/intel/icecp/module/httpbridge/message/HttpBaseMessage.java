package com.intel.icecp.module.httpbridge.message;

import java.net.URI;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.intel.icecp.core.Message;
import com.intel.icecp.module.httpbridge.HttpBridge_Module;

/**
 * Base message for http_bridge module messages. This base class contains the static constants used by the extended classes, and the common fields used by all
 * commands.
 * <p>
 * returnCmdChannelURI = The user specified channel where the command status is returned.<p>
 * connectionId = the unique id for the connection. Returned from the Setup command.<p>
 * status = the HTTP_BRIDGE_STATUS of the command.
 * <p>
 * The JsonTypeInfo annotation allows the deserialization to work for the correct extended classes. If you remove this, the deserialization will not work.
 * 
 *
 */
@SuppressWarnings("serial")
// this annotation puts the following in the json stream ".@c":".HttpBaseMessage"
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, include = JsonTypeInfo.As.PROPERTY, property = ".@c")
@JsonInclude(value = Include.NON_NULL)
public abstract class HttpBaseMessage implements Message, OnCommandMessage<HttpBridge_Module> {

    /**
     * The well known command channel for this module. The module is subscribed to this channel name and receives the commands. Publish all commands to this
     * channel.
     */
    @JsonIgnore
    public final static String HTTP_CMD_CHANNEL_NAME = "HTTPBridge-CMD";

    /**
     * The HTTP_BRIDGE_STATUS values. These values are returned from the module on the return channel to indicate the status of the command. The status values
     * are high level errors to direct you to any problems. See the logger output for more details on any given error.
     * 
     */
    // @JsonIgnore
    public static enum HTTP_BRIDGE_STATUS {
        OK,
        ERROR_ON_SYNTAX,
        ERROR_ON_CONNECT,
        ERROR_ON_RESPONSE,
        ERROR_ON_IO
    }

    /**
     * The return channel for all commmands. Specify the channel name that you want return status to be sent. This channel should be unique for your usage.
     */
    public URI cmdReturnChannelURI; // The command return channel URI

    /**
     * When a setup command is sent to the module, it will create a connectionId and return it on the return channel. This connectionId needs to be specified
     * for all subsequent commands (eg, data messages {@link HttpDataMessage} and teardown messages {@link HttpTeardownMessage}
     */
    public long connectionId; // returned from a connect, specified for all other commands

    /**
     * The status of the command. This value is set and returned for each command on the return channel. See HTTP_BRIDGE_STATUS values for more info.
     */
    public HTTP_BRIDGE_STATUS status = HTTP_BRIDGE_STATUS.OK; // returned from all commands

    /**
     * This method is used to get the name of the current command. The class name indicates the command (eg, setup, data, teardown).
     * 
     * @return The name of the class to indicate the command name.
     */
    @JsonIgnore
    public final String getCommand() {
        return getClass().getSimpleName();
    }

    /**
     * Each subclass will implement this method to perform the given command. The module passes in context for the call.
     */
    @Override
    public abstract void onCommandMessage(HttpBridge_Module context);

    /**
     * Each subclass will implement this method to validate the incoming command. The module passes in the context for the call.
     */
    @Override
    public abstract String onValidate(HttpBridge_Module context);

}
