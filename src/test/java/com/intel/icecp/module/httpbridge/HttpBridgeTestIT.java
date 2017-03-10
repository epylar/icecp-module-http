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

import static org.junit.Assert.fail;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;

import com.intel.icecp.core.Channel;
import com.intel.icecp.core.Message;
import com.intel.icecp.core.Node;
import com.intel.icecp.core.messages.BytesMessage;
import com.intel.icecp.core.metadata.Persistence;
import com.intel.icecp.core.misc.ChannelIOException;
import com.intel.icecp.core.misc.ChannelLifetimeException;
import com.intel.icecp.core.misc.OnPublish;
import com.intel.icecp.main.ModuleParameter;
import com.intel.icecp.module.httpbridge.message.HttpBaseMessage;
import com.intel.icecp.module.httpbridge.message.HttpDataMessage;
import com.intel.icecp.module.httpbridge.message.HttpSetupMessage;
import com.intel.icecp.module.httpbridge.message.HttpTeardownMessage;
import com.intel.icecp.node.NodeFactory;
import com.intel.icecp.node.utils.NetworkUtils;

public class HttpBridgeTestIT {
    private static final Logger logger = LogManager.getLogger("HttpBridgeTestIT");
    private String nodeName = "/intel/node/";
    private Node node = null;
    private String HttpBridgeModuleJar = "target/icecp-module-httpbridge-0.0.1.jar";
    private final Object cmdLock = new Object();
    private Object dataLock = new Object();

    private HttpSetupMessage returnSetupMessage = null;
    private String website = "http://www.google.com";
    private String cmdChannelName = "/" + HttpBaseMessage.HTTP_CMD_CHANNEL_NAME;
    private String cmdRetChannelName = "/CMD-RETURN";
    private String outputChannelName = "/output";

    private Channel<HttpBaseMessage> cmdChannel = null;
    private Channel<HttpBaseMessage> cmdRetChannel = null;
    private Channel<BytesMessage> outputChannel = null;
    private Channel<HttpDataMessage> dataChannel = null;
    private Channel<HttpDataMessage> dataRetChannel = null;
    
    @Ignore
    @Test
    public void testBridge() throws InterruptedException {

        logger.info( "Test channel using subscribe" );
        begin();
        openCmdChannels(true);
        sendSetup();
        synchronized (cmdLock) {
            cmdLock.wait();
            logger.info("Lock Wait complete");
        }
        Thread.sleep(1000);

        sendData();
        synchronized (dataLock) {
            dataLock.wait();
            logger.info("Lock Wait complete");
        }
        Thread.sleep(1000);            
        
        sendTeardown();
        synchronized (cmdLock) {
            cmdLock.wait();
            logger.info("Lock Wait complete");
        }
        
        cleanUp();
        logger.info("Finished");
    }
    
    @SuppressWarnings("unused")
	public void begin() {
        logger.info("Startup the node: " + nodeName);
        try {
            nodeName += NetworkUtils.getHostName();
            node = NodeFactory.buildTestNode(nodeName);
            node.start();
            
            System.setProperty("icecp.sandbox", "disabled");
            
            ModuleParameter option = ModuleParameter.build(HttpBridgeModuleJar);
            
            URI jarUri = option.modulePath;
            URI configurationUri = option.configurationPath;

            CompletableFuture<Collection<Long>> future = node.loadAndStartModules(jarUri, configurationUri);
            Collection<Long> moduleIds;
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                fail("Failed to load HttpBridge-Module - be sure the module is built");
            }
            
            cmdChannelName = node.getDefaultUri().toString() + cmdChannelName;
            cmdRetChannelName = node.getDefaultUri().toString() + cmdRetChannelName;
            outputChannelName = node.getDefaultUri().toString() + outputChannelName;
        } catch (Exception e) {
            fail("Failed to setup node: " + e.getMessage());
        } 
    }

    public <T extends Message> Channel<T> openChannel(String channelName, Class<T> messageClass, Persistence persistence) {
        Channel<T> channel = null;
        try {
            URI uri = new URI(channelName);
            channel = openChannel(uri, messageClass, persistence);
            logger.info("Open Channel: " + channel.getName());
        } catch (URISyntaxException e) {
            cleanUp();
            fail(String.format("Failed to open channel[%s] Error[%s]", channelName, e.getMessage()));
        }
        return channel;
    }
    
    private <T extends Message> Channel<T> openChannel(URI channelURI, Class<T> messageClass, Persistence persistence) {
        Channel<T> channel = null;
        try {
            channel = node.openChannel(channelURI, messageClass, persistence);
        } catch (ChannelLifetimeException e) {
            cleanUp();
            fail(String.format("Failed to open channel[%s] Error[%s]", channelURI, e.getMessage()));
        }
        return channel;
    
    }
    
    private void openCmdChannels(boolean usingSubscribe) {
        cmdChannel = openChannel(cmdChannelName, HttpBaseMessage.class, Persistence.NEVER_PERSIST);
        if (usingSubscribe) {
            cmdRetChannel = openChannel(cmdRetChannelName, HttpBaseMessage.class, Persistence.NEVER_PERSIST);
            CommandReturnCallback returnCallback = new CommandReturnCallback();
            try {
                cmdRetChannel.subscribe(returnCallback);
            } catch (ChannelIOException e) {
                cleanUp();
                fail("Failed to open command channels: " + e.getMessage());
            }
        }
    }
    
    public class CommandReturnCallback implements OnPublish<HttpBaseMessage> {
        public void onPublish(HttpBaseMessage message) {
            logger.info(String.format("ReturnMessage Arrived id[%d] status[%s], cmd[%s]", 
                message.connectionId, message.status, message.getCommand()));
            synchronized (cmdLock) {
                logger.info("ReturnCallback lock.notify");
                if (message.getCommand().contains("Setup")) {
                    returnSetupMessage = (HttpSetupMessage)message;
                    setupDataChannels(returnSetupMessage);
                }
                else if (message.getCommand().contains("Teardown")) {
                    returnSetupMessage = null;
                }

                cmdLock.notify();
            }
        }
    }
    
    private void sendSetup() {
        logger.info("Send Setup Command");
        try {
            HttpSetupMessage setupMsg = new HttpSetupMessage(new URI(cmdRetChannelName));
            setupMsg.connectionUrl = new URL(website);
            setupMsg.proxyHost = "proxy-us.intel.com";
            setupMsg.proxyPort = 911;
            cmdChannel.publish(setupMsg);   //keep channel open
           
        } catch (URISyntaxException | MalformedURLException | ChannelIOException e) {
            cleanUp();
            fail("Failed to sendSetup: " + e.getMessage());
        } 
    }
    
    private void setupDataChannels(HttpSetupMessage message) {
        URI dataCmdChannelURI = message.dataCmdChannelURI;
        logger.info(String.format("DataCmdChannel[%s]", dataCmdChannelURI.toString()));
        URI dataCmdReturnChannelURI = message.dataCmdReturnChannelURI;
        logger.info(String.format("DataCmdReturnChannel[%s]", dataCmdReturnChannelURI.toString()));
        
        dataChannel = openChannel(dataCmdChannelURI, HttpDataMessage.class, Persistence.NEVER_PERSIST);
        dataRetChannel = openChannel(dataCmdReturnChannelURI, HttpDataMessage.class, Persistence.NEVER_PERSIST);
        outputChannel = openChannel(outputChannelName, BytesMessage.class, new Persistence());
        
        DataCommandReturnCallback dataReturnCallback = new DataCommandReturnCallback();
        OutputChannelCallback outputCallback = new OutputChannelCallback();
        logger.info("Setup Data Cmd Channel: " + dataChannel.getName());
        logger.info("Setup Data Cmd Return Channel, and subscribe: " + dataRetChannel.getName());
        logger.info("Create output channel, and subscribe: " + outputChannel.getName());
        
        try {
            dataRetChannel.subscribe(dataReturnCallback);
            outputChannel.subscribe(outputCallback);
        } catch (ChannelIOException e) {
            cleanUp();
            fail("Failed to setup data channels: " + e.getMessage());
        }
    }

    private void sendData() {
        logger.info("Send data command");
        HttpDataMessage dataMsg;
        try {
            dataMsg = new HttpDataMessage(returnSetupMessage.connectionId);
            dataMsg.httpRequest = "GET";
            dataMsg.requestHeaders = new HashMap();
            dataMsg.requestHeaders.put("Content-Language", "en-US");
            dataMsg.useCache = false;
            dataMsg.outputHttpChannelURI = new URI(outputChannelName);
            logger.info(String.format("Publish data command [%s] on channel [%s]", dataMsg.httpRequest, dataChannel.getName()));
            dataChannel.publish(dataMsg);
        } catch (ChannelIOException | URISyntaxException e) {
            cleanUp();
            fail("Failed to send data: " + e.getMessage());
        }
    }

    private class DataCommandReturnCallback implements OnPublish<HttpDataMessage> {
        @Override
        public void onPublish(HttpDataMessage message) {
            logger.info(String.format("DataReturnMessage Arrived id[%d] status[%s], cmd[%s]", 
                    message.connectionId, message.status, message.getCommand()));
            synchronized (dataLock) {
                logger.info("DataCommandReturn - dataLock.notify()");
                dataLock.notify();
            }
        }
    }
    
    private class OutputChannelCallback implements OnPublish<BytesMessage> {
        @Override
        public void onPublish(BytesMessage message) {
            logger.info(String.format("Received [%d] bytes on output channel", message.getBytes().length));
        }
    }

    private void sendTeardown() {
        try {
            logger.info("Send Teardown Command");
            HttpTeardownMessage tearDownMsg = new HttpTeardownMessage(returnSetupMessage.connectionId, new URI(cmdRetChannelName));
            cmdChannel.publish(tearDownMsg);   //keep channel open
            
        } catch (URISyntaxException | ChannelIOException e) {
            cleanUp();
            fail("Failed to send Teardown msg: " + e.getMessage());
        }
    }
    
    private void cleanUp() {
        closeChannel(cmdChannel);
        closeChannel(cmdRetChannel);
        closeChannel(outputChannel);
        closeChannel(dataChannel);
        closeChannel(dataRetChannel);
    }
    
    private <T extends Message> void closeChannel(Channel<T> channel ) {
        try {
            if (channel != null)
                channel.close();
        } catch (ChannelLifetimeException e) {
            //do nothing
        }
    }
}
