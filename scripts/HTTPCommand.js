/**
 *	This script sends a command to the HTTPBridge module.
 *	The command sets up an input channel and output channel to a URL.
 *  Once the command is received, the input and output channels can be used to send data.
 *  
 *  To run it:
 *  First build icecp.
 *  Second build the HTTPBridge module: mvn clean install
 *  
 *  Next run the icecp node mainDaemon.
 *  Arguments are: -c ../icecp-module-httpbridge/target/icecp-module-httpbridge-0.0.1.jar
 *  VM Arguments are: -Dicecp.sandbox=disabled 
 *  This will load the module and it will wait for commands.
 *  
 *  Then finally, run this script.
 *  From the icecp node folder type:
 *  jjs -cp "..\icecp-module-httpbridge\target\icecp-module-httpbridge-0.0.1-jar-with-dependencies.jar" ..\icecp-module-httpbridge\scripts\HTTPCommand.js
 *  
 *  
 */

//The full channel names are built in the connectToDevice() method below.
var httpURL = "http://www.google.com";
var cmdChannelName = "/HTTPBridge-CMD";
var returnCmdChannelName = "/HTTPBridge-CMD-Return";
var inputChannelName = "/HTTPBridge-Input";
var outputChannelName = "/HTTPBridge-Output";

var nodeName = "/intel/node/" + com.intel.icecp.node.utils.NetworkUtils.getHostName();
var myNode;
var cmdChannel;
//var cmdReturnChannel;
var outputChannel;

//Create and start a node, and create the channelNames
function connectToDevice() {
	//Create a ICECP Node and Start it
	print("configure device: " + nodeName);
	myNode = com.intel.icecp.node.utils.StartupUtils.configureDefaultDevice(nodeName);
	cmdChannelName = myNode.getDefaultUri().toString() + cmdChannelName;
	returnCmdChannelName = myNode.getDefaultUri().toString() + returnCmdChannelName;
	inputChannelName = myNode.getDefaultUri().toString() + inputChannelName;
	outputChannelName = myNode.getDefaultUri().toString() + outputChannelName;
	print("start");
	myNode.start();
}

//returns an open channel
function openChannel(channelName, channelMessageClassName, persistence) {
	
	var uri = new java.net.URI(channelName);
	var retChannel = myNode.openChannel(uri, channelMessageClassName, persistence);
	return retChannel;
}

function openCmdChannels() {
	print("open command channel");
	var perst = new com.intel.icecp.core.metadata.Persistence(0);
	cmdChannel = openChannel(cmdChannelName, com.intel.icecp.module.httpbridge.message.HttpBaseMessage.class, perst);
//	print("open command RETURN channel");
//	cmdReturnChannel = openChannel(returnCmdChannelName, com.intel.icecp.module.httpbridge.message.HttpBaseMessage.class);
}

function closeCmdChannels() {
	cmdChannel.close();
	//cmdReturnChannel.close();
}

function openDataChannels() {
	print("open data output channel");
	var perst = new com.intel.icecp.core.metadata.Persistence(3000);
	outputChannel = openChannel(outputChannelName, com.intel.icecp.core.messages.BytesMessage.class, perst);
}

function closeDataChannels() {
	outputChannel.close();
}

//Assumes command channel is created and open
function sendSetupCmd() {
	//Configure setup command
	var returnUri = new java.net.URI(returnCmdChannelName);
	var cmdInfo = new com.intel.icecp.module.httpbridge.message.HttpSetupMessage(returnUri);
	cmdInfo.connectionUrl = new java.net.URL(httpURL);
	cmdInfo.proxyHost = "proxy-us.intel.com";
	cmdInfo.proxyPort = 911;
	//Publish the configure message to the ICECP Module
	print("publish connect command");
	cmdChannel.publish(cmdInfo);
	java.lang.Thread.sleep(1000);
}

//Open the command return channel and get the latest.
//return the message
function getCmdResponse() {
	var perst = new com.intel.icecp.core.metadata.Persistence(0);
	var cmdReturnChannel = openChannel(returnCmdChannelName, com.intel.icecp.module.httpbridge.message.HttpBaseMessage.class, perst);
	var returnMessage = cmdReturnChannel.latest().get();
	print("ConnId=" + returnMessage.connectionId + ", message status = " + returnMessage.status + ", Cmd:" + returnMessage.getCommand());
	java.lang.Thread.sleep(1000);
	cmdReturnChannel.close();
	return returnMessage;
}

//assumes cmd channel created and open
function sendDataCmd(connectionId, httpRequest) {
	print("SendDataCmd: " + httpRequest + " to ID " + connectionId);
	var uri = new java.net.URI(returnCmdChannelName);
	var dataMsg = new com.intel.icecp.module.httpbridge.message.HttpDataMessage(connectionId, uri);
	dataMsg.httpRequest = httpRequest; 
	var HashMap = Java.type("java.util.HashMap");
	dataMsg.requestHeaders = new HashMap();
	dataMsg.requestHeaders.put("Content-Language", "en-US");
	dataMsg.useCache = false;
	//dataMsg.inputHttpChannelURI = new java.net.URI(inputChannelName);
	dataMsg.outputHttpChannelURI = new java.net.URI(outputChannelName);
	print("Publish Data Cmd, HTTPRequest=" + httpRequest);
	cmdChannel.publish(dataMsg);
	java.lang.Thread.sleep(1000);
}

function getOutputBytes() {
	var returnMessage;
	var returnBytes;
	try {
		returnMessage = outputChannel.latest().get();	
		print("Received Bytes");
//		returnBytes = returnMessage.getBytes();
		returnBytes = Java.from(returnMessage.getBytes());
		print("Bytes are: " + String.fromCharCode.apply(null, returnBytes));
		//print("Bytes are: " + returnBytes);
	}
	catch (err) {
		if (err.message.search("TimeoutException") >= 0)
			print("TIMEOUT - Did not receive the bytes");
		else
			print("Error: " + err.message);
		returnBytes = "Error";
	}
	java.lang.Thread.sleep(1000);
	return returnBytes;
}

//assumes command channel is open
function sendCloseCmd(connectionId) {
	print("Send Teardown to ID " + connectionId);
	var uri = new java.net.URI(returnCmdChannelName);
	var cmdInfo = new com.intel.icecp.module.httpbridge.message.HttpTeardownMessage(connectionId, uri);
	print("publish teardown command");
	cmdChannel.publish(cmdInfo);
	java.lang.Thread.sleep(1000);
}

print("STEP 1: Connect To Device");
connectToDevice();
print("STEP 2: Open channels");
openCmdChannels();
openDataChannels();
print("STEP 3: Send Setup Command");
sendSetupCmd();
print("STEP 4: Get Setup Response");
var cmdResponse = getCmdResponse();
if (cmdResponse.status != com.intel.icecp.module.httpbridge.message.HttpBaseMessage.HTTP_BRIDGE_STATUS.OK) {
	exit();
}
print("STEP 5: Send DataCommand");
sendDataCmd(cmdResponse.connectionId, "GET");
print("ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ");
java.lang.Thread.sleep(2000);
print("STEP 6: Get Data Response");
cmdResponse = getCmdResponse();
if (cmdResponse.status != com.intel.icecp.module.httpbridge.message.HttpBaseMessage.HTTP_BRIDGE_STATUS.OK) {
	exit();
}
//getDataCmdReturn();
//java.lang.Thread.sleep(2000);
print("STEP 7: Get Data Bytes");
var theBytes = getOutputBytes();

print("STEP 8: Send Close Command");
sendCloseCmd(cmdResponse.connectionId);
print("STEP 9: Get Close Response");
getCmdResponse();
print("STEP 10: Clean up");
closeCmdChannels();
closeDataChannels();









