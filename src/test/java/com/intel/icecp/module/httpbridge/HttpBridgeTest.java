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

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.intel.icecp.module.httpbridge.message.HttpSetupMessage;
/**
 * Unit test for simple App.
 */
public class HttpBridgeTest 
{
    private static final Logger logger = LogManager.getLogger();
    private HttpSetupMessage cmdMessage = null;
    private HttpCommandExecutor cmdExecutor = null;
    
    @Before
    public void setup() {
        cmdMessage = new HttpSetupMessage();
        try {
            cmdMessage.connectionUrl = new URL("http://www.google.com");
            cmdMessage.cmdReturnChannelURI = new URI("ndn:/intel/node/cmd");
        } catch (MalformedURLException | URISyntaxException e) {
            fail("Failed during setup");
        }
        cmdExecutor = new HttpCommandExecutor(logger, null, cmdMessage);
    }
    
    @Test
    public void testVerifyConnection() {
        try {
            cmdExecutor.verifyConnection(null);
            fail("Verify Connection should have failed");
        } catch (HttpConnectionException e) {
            ;//Expected.
            logger.info("VerifyConnection passed");
        }
    }

    @Test 
    public void testCreateConnection() {
        
        try {
            HttpURLConnection connection = cmdExecutor.createConnection();
            cmdExecutor.verifyConnection(connection);
            logger.info("Create Connection passed");
        } catch (HttpConnectionException e) {
            fail("Failed to create connection");
        }
    }
    
    @Test 
    public void testCreateConnectionProxy() {
        
        try {
            cmdMessage.proxyHost = "proxy-us.intel.com";
            cmdMessage.proxyPort = 911;
            cmdExecutor.createConnection();
            logger.info("Create Connection passed");
        } catch (HttpConnectionException e) {
            fail("Failed to create connection");
        }
    }
    
    @Test
    public void testSetConnectionProperties() {
        HttpURLConnection connection = null;
        Map<String,String> propMap = null;
        try {
            connection = cmdExecutor.createConnection();
            propMap = new HashMap<>();
            cmdExecutor.setConnectionProperties(connection, "GET", propMap, true);
            logger.info("Created connection Properties passed");
        } catch (HttpConnectionException e) {
            fail("Failed to create connection properties");
        }
        
        try {
            cmdExecutor.setConnectionProperties(connection, "FOO", propMap, false);
            fail("Failed.  Created connection properties with bad http request");
        } catch (HttpConnectionException e) {
            logger.info("Created connection properties with bad http request passed");
        }

        try {
            propMap.put("nfl", "football");
            propMap.put(null, "bar");
            cmdExecutor.setConnectionProperties(connection, "POST", propMap, false);
            logger.info("Created connection properties with null key passed");
        } catch (HttpConnectionException e) {
            fail("Failed to create connection properties with null key");
        }

    }
    
}
