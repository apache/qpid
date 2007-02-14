/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.client.handler;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.message.MessageHeaders;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.Content;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.protocol.AMQMethodEvent;

import org.apache.log4j.Logger;

public class MessageTransferMethodHandler implements StateAwareMethodListener
{
    private static final Logger _logger = Logger.getLogger(MessageTransferMethodHandler.class);

    private static MessageTransferMethodHandler _instance = new MessageTransferMethodHandler();
    
    public static MessageTransferMethodHandler getInstance()
    {
        return _instance;
    }

    private MessageTransferMethodHandler() {}

    public void methodReceived (AMQStateManager stateManager, AMQProtocolSession protocolSession, AMQMethodEvent evt) throws AMQException
    {
    	MessageTransferBody transferBody = (MessageTransferBody) evt.getMethod();
        
        _logger.debug("New JmsDeliver method received");
        
        MessageHeaders messageHeaders = new MessageHeaders();
        messageHeaders.setMessageId(transferBody.getMessageId());
        messageHeaders.setAppId(transferBody.getAppId());
        messageHeaders.setContentType(transferBody.getContentType());
        messageHeaders.setEncoding(transferBody.getContentEncoding());
        messageHeaders.setCorrelationId(transferBody.getCorrelationId());
        messageHeaders.setDestination(transferBody.getDestination());
        messageHeaders.setExchange(transferBody.getExchange());
        messageHeaders.setExpiration(transferBody.getExpiration());
        messageHeaders.setReplyTo(transferBody.getReplyTo());
        messageHeaders.setRoutingKey(transferBody.getRoutingKey());
        messageHeaders.setTransactionId(transferBody.getTransactionId());
        messageHeaders.setUserId(transferBody.getUserId());
        messageHeaders.setPriority(transferBody.getPriority());
        messageHeaders.setDeliveryMode(transferBody.getDeliveryMode());
        messageHeaders.setJMSHeaders(transferBody.getApplicationHeaders());
        
    	final UnprocessedMessage msg = new UnprocessedMessage(evt.getChannelId(), evt.getRequestId(), messageHeaders);
        
        if(transferBody.getBody().getContentType() == Content.TypeEnum.INLINE_T)
        {
        	msg.addContent(transferBody.getBody().getContentAsByteArray());
        	protocolSession.deliverMessageToAMQSession(evt.getChannelId(), msg);
        }
        else
        {
        	String referenceId = new String(transferBody.getBody().getContentAsByteArray());
        	protocolSession.deliverMessageToAMQSession(evt.getChannelId(),referenceId);
        }
        
    }
}

