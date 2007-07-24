/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.qpid.server.exchange.synapse;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;

import javax.activation.DataHandler;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.axiom.attachments.ByteArrayDataSource;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMText;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.util.StAXUtils;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.impl.llom.soap11.SOAP11Factory;
import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.addressing.RelatesTo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.Content;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;

/**
 * The MessageContext needs to be set up and then is used by the SynapseMessageReceiver to inject messages.
 * This class is used by the SynapseMessageReceiver to find the environment. The env is stored in a Parameter to the Axis2 config
 */
public class MessageContextCreatorForQpid{

    private static Log log = LogFactory.getLog(MessageContextCreatorForQpid.class);

    private static SynapseConfiguration synCfg = null;
    private static SynapseEnvironment   synEnv = null;

    final static String ORIGINAL_MESSAGE = "ORIGINAL_MESSAGE";
    final static String AMQP_CONTENT_TYPE = "AMQP_CONTENT_TYPE";  
    final static String DEFAULT_CHAR_SET_ENCODING = "UTF-8";
    
    enum ContentType 
    {
    	TEXT_PLAIN ("text/plain"),
    	TEXT_XML ("text/xml"),
    	APPLICATION_OCTECT ("application/octet-stream");
    	
    	private final String _value;
    	
    	private ContentType (String value)
    	{
    		_value = value;
    	}
    	
    	public String value()
    	{
    		return _value;
    	}
    }
    
    private static String createURL(String exchangeName,String routingKey)
    {
    	StringBuffer buf = new StringBuffer();
    	buf.append("amqp://");
    	buf.append(exchangeName);
    	buf.append("?");
    	buf.append("routingKey=");
    	buf.append(routingKey);
    	
    	return buf.toString();
    }
    
    public static MessageContext getSynapseMessageContext(AMQMessage amqMsg) throws SynapseException {

        if (synCfg == null || synEnv == null) {
            String msg = "Synapse environment has not initialized properly..";
            log.fatal(msg);
            throw new SynapseException(msg);
        }
        
        org.apache.axis2.context.MessageContext axis2MC = new org.apache.axis2.context.MessageContext();        
        Axis2MessageContext synCtx = new Axis2MessageContext(axis2MC, synCfg, synEnv);
        synCtx.setMessageID(amqMsg.getTransferBody().getMessageId().asString());
        if(amqMsg.getTransferBody().getCorrelationId() != null)
        {
        	synCtx.setRelatesTo(new RelatesTo[]{new RelatesTo(amqMsg.getTransferBody().getCorrelationId().asString())});
        }
        synCtx.setTo(new EndpointReference(createURL(amqMsg.getTransferBody().getExchange().asString(),amqMsg.getTransferBody().getRoutingKey().asString())));
        
        if(amqMsg.getTransferBody().getReplyTo() != null)
        {
        	synCtx.setReplyTo(new EndpointReference(createURL(amqMsg.getTransferBody().getExchange().asString(),amqMsg.getTransferBody().getReplyTo().asString())));       
    	}
        synCtx.setDoingPOX(true);
        synCtx.setProperty(ORIGINAL_MESSAGE, amqMsg);
        
        //Creating a fictitious SOAP envelope to support the synapse model
        
        SOAPFactory soapFactory = new SOAP11Factory();
        SOAPEnvelope envelope = soapFactory.getDefaultEnvelope();
        
        String contentType = amqMsg.getTransferBody().getContentType().asString();
        if(ContentType.TEXT_PLAIN.value().equals(contentType))
        {
        	OMElement wrapper = soapFactory.createOMElement(new QName("payload"), null);
        	OMText textData = soapFactory.createOMText(amqMsg.getTransferBody().getBody().getContentAsString());
            wrapper.addChild(textData);
            envelope.getBody().addChild(wrapper);
        }
        else if (ContentType.TEXT_XML.value().equals(contentType))
        {
        	XMLStreamReader parser;
			try
			{
				parser = StAXUtils.createXMLStreamReader(
						 new ByteArrayInputStream(amqMsg.getTransferBody().getBody().getContentAsByteArray()),
						 DEFAULT_CHAR_SET_ENCODING);
			}
			catch (XMLStreamException e)
			{
				throw new SynapseException("Error reading the XML message",e);				
			}
        	
        	StAXOMBuilder builder = new StAXOMBuilder(parser);
        	//builder.setOMBuilderFactory(soapFactory);
        	
        	Object obj = builder.getDocumentElement();
        	envelope.getBody().addChild(builder.getDocumentElement());
        }
        else if (ContentType.APPLICATION_OCTECT.value().equals(contentType))
        {
        	// treat binary data as an attachment
        	DataHandler dataHandler = new DataHandler(
                    new ByteArrayDataSource(amqMsg.getTransferBody().getBody().getContentAsByteArray()));
                OMText textData = soapFactory.createOMText(dataHandler, true);
                OMElement wrapper = soapFactory.createOMElement(new QName("payload"), null);
                wrapper.addChild(textData);
                synCtx.setDoingMTOM(true);
                
                envelope.getBody().addChild(wrapper);
        }
        else
        {
        	throw new SynapseException("Unsupported Content Type : " + contentType);
        }
        
        synCtx.setProperty(AMQP_CONTENT_TYPE, contentType);
                
        try
        {
        	synCtx.setEnvelope(envelope);
        }
        catch(AxisFault e)
        {        
        	throw new SynapseException(e);
        }
        	
        return synCtx;
    }    
    
    public static AMQMessage getAMQMessage(MessageContext mc)
    {
    	AMQMessage origMsg = (AMQMessage)mc.getProperty(ORIGINAL_MESSAGE);
    	OMElement payload = mc.getEnvelope().getBody().getFirstElement();
    	
    	String amqContentType = (String)mc.getProperty(AMQP_CONTENT_TYPE);
    	byte[] content = new byte[0];
    	
    	if(ContentType.TEXT_PLAIN.value().equals(amqContentType))
    	{
    		// For plain text there was a wrapper element
    		content = payload.getText().getBytes();
    	}
    	else if (ContentType.TEXT_XML.value().equals(amqContentType))
    	{
    		content = payload.getText().getBytes();
    	}
    	else if (ContentType.APPLICATION_OCTECT.value().equals(amqContentType) && mc.isDoingMTOM())
    	{
    		
    	}
    	
    	String url = mc.getTo().getAddress();;
		// very crude
		// should have utility class to do this, but do it when amqp
		// officialy converge on an addressing scheme
		String exchangeName = url.substring(7,url.indexOf('?'));
		String routingKey = url.substring(url.indexOf('=')+1,url.length());
		
    	
    	MessageTransferBody origTransferBody = origMsg.getTransferBody();
    	MessageTransferBody transferBody = MessageTransferBody.createMethodBody(
    			origTransferBody.getMajor(), 
    			origTransferBody.getMinor(),
    			origTransferBody.getAppId(), //appId
    			origTransferBody.getApplicationHeaders(), //applicationHeaders
				new Content(Content.TypeEnum.INLINE_T, content), //body
				origTransferBody.getContentType(), //contentEncoding, 
				origTransferBody.getContentType(), //contentType
				origTransferBody.getCorrelationId(), //correlationId
				origTransferBody.getDeliveryMode(), //deliveryMode non persistant
				new AMQShortString(exchangeName),// destination
				new AMQShortString(exchangeName),// exchange
				origTransferBody.getExpiration(), //expiration
				origTransferBody.getImmediate(), //immediate
				origTransferBody.getMandatory(), //mandatory
				origTransferBody.getMessageId(), //messageId
				origTransferBody.getPriority(), //priority
				origTransferBody.getRedelivered(), //redelivered
				origTransferBody.getReplyTo(), //replyTo
				new AMQShortString(routingKey), //routingKey, 
				"abc".getBytes(), //securityToken
				origTransferBody.ticket, //ticket
				System.currentTimeMillis(), //timestamp
				origTransferBody.getTransactionId(), //transactionId
				origTransferBody.getTtl(), //ttl, 
				origTransferBody.getUserId() //userId
				);
    	AMQMessage newMsg = new AMQMessage(origMsg.getMessageStore(),transferBody,origMsg.getTransactionContext());
    	
    	return newMsg;
    }
    
    public static void setSynConfig(SynapseConfiguration synCfg) {
        MessageContextCreatorForQpid.synCfg = synCfg;
    }

    public static void setSynEnv(SynapseEnvironment synEnv) {
        MessageContextCreatorForQpid.synEnv = synEnv;
    }
}
