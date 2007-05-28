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
package org.apache.qpid.nclient.impl;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.framing.Content;
import org.apache.qpid.framing.MessageAppendBody;
import org.apache.qpid.framing.MessageCheckpointBody;
import org.apache.qpid.framing.MessageCloseBody;
import org.apache.qpid.framing.MessageOpenBody;
import org.apache.qpid.framing.MessageRecoverBody;
import org.apache.qpid.framing.MessageResumeBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.nclient.amqp.AMQPMessage;
import org.apache.qpid.nclient.amqp.AMQPMessageCallBack;
import org.apache.qpid.nclient.api.QpidException;
import org.apache.qpid.nclient.api.QpidMessageConsumer;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.message.AMQPApplicationMessage;
import org.apache.qpid.nclient.message.MessageHeaders;
import org.apache.qpid.nclient.message.MessageStore;
import org.apache.qpid.nclient.message.TransientMessageStore;

public class QpidMessageConsumerImpl extends AbstractResource implements QpidMessageConsumer, AMQPMessageCallBack
{
	private MessageStore _msgStore = new TransientMessageStore();
	private final BlockingQueue<AMQPApplicationMessage> _queue = new LinkedBlockingQueue<AMQPApplicationMessage>();
	private QpidSessionImpl _session;
	private AMQPMessage _amqpMessage;
	
	protected QpidMessageConsumerImpl(QpidSessionImpl session)
	{
		super("Message Class");
		_session = session;		
	}
	
	/**
	 * -----------------------------------------------
	 * Methods from QpidMessageConsumer class
	 * -----------------------------------------------
	 */
	
	public AMQPApplicationMessage get() throws QpidException
	{
		// I want this to do a message.get
		return null;
	}

	public AMQPApplicationMessage receive()throws QpidException
	{
		return _queue.poll();
	}
	
	public AMQPApplicationMessage receive(long timeout, TimeUnit tu)throws QpidException
	{
		try
		{
			return _queue.poll(timeout, tu);
		}
		catch(Exception e)
		{
			throw new QpidException("Error retrieving message from queue",e);
		}		
	}

	/**
	 * -----------------------------------------------
	 * Abstract methods from AbstractResource class
	 * -----------------------------------------------
	 */
	protected void openResource() throws AMQPException
	{
		_amqpMessage = _session.getClassFactory().createMessageClass(_session.getChannel(),null);
	}
	
	protected void closeResource() throws AMQPException
	{
		_session.getClassFactory().destoryMessageClass(_session.getChannel(), _amqpMessage);
	}

	/**
	 * -----------------------------------------------
	 * Methods from AMQPMessageCallback class
	 * -----------------------------------------------
	 */
	public void append(MessageAppendBody messageAppendBody, long correlationId) throws AMQPException
	{ 
		String reference = new String(messageAppendBody.getReference());
		AMQPApplicationMessage msg = _msgStore.getMessage(reference);
		msg.addContent(messageAppendBody.getBytes());	
	}

	public void checkpoint(MessageCheckpointBody messageCheckpointBody, long correlationId) throws AMQPException
	{
		// TODO Auto-generated method stub
	}

	public void close(MessageCloseBody messageCloseBody, long correlationId) throws AMQPException
	{
		String reference = new String(messageCloseBody.getReference());
		AMQPApplicationMessage msg = _msgStore.getMessage(reference);
		enQueue(msg);
		_msgStore.removeMessage(reference);
	}

	public void open(MessageOpenBody messageOpenBody, long correlationId) throws AMQPException
	{
		String reference = new String(messageOpenBody.getReference());
		AMQPApplicationMessage msg = new AMQPApplicationMessage(_session.getChannel(), messageOpenBody.getReference());
		_msgStore.storeMessage(reference, msg);
	}

	public void recover(MessageRecoverBody messageRecoverBody, long correlationId) throws AMQPException
	{
		// TODO Auto-generated method stub

	}

	public void resume(MessageResumeBody messageResumeBody, long correlationId) throws AMQPException
	{
		// TODO Auto-generated method stub

	}

	public void transfer(MessageTransferBody messageTransferBody, long correlationId) throws AMQPException
	{
		MessageHeaders messageHeaders = new MessageHeaders();
        messageHeaders.setMessageId(messageTransferBody.getMessageId());
        messageHeaders.setAppId(messageTransferBody.getAppId());
        messageHeaders.setContentType(messageTransferBody.getContentType());
        messageHeaders.setEncoding(messageTransferBody.getContentEncoding());
        messageHeaders.setCorrelationId(messageTransferBody.getCorrelationId());
        messageHeaders.setDestination(messageTransferBody.getDestination());
        messageHeaders.setExchange(messageTransferBody.getExchange());
        messageHeaders.setExpiration(messageTransferBody.getExpiration());
        messageHeaders.setReplyTo(messageTransferBody.getReplyTo());
        messageHeaders.setRoutingKey(messageTransferBody.getRoutingKey());
        messageHeaders.setTransactionId(messageTransferBody.getTransactionId());
        messageHeaders.setUserId(messageTransferBody.getUserId());
        messageHeaders.setPriority(messageTransferBody.getPriority());
        messageHeaders.setDeliveryMode(messageTransferBody.getDeliveryMode());
        messageHeaders.setApplicationHeaders(messageTransferBody.getApplicationHeaders());
		
        
        
		if (messageTransferBody.getBody().getContentType() == Content.TypeEnum.INLINE_T)
		{
			AMQPApplicationMessage msg = new AMQPApplicationMessage(_session.getChannel(), 
																	correlationId,
                                                                    messageHeaders,
                                                                    messageTransferBody.getBody().getContentAsByteArray(), 
                                                                    messageTransferBody.getRedelivered());
			
			enQueue(msg);
		}
		else
		{
			byte[] referenceId = messageTransferBody.getBody().getContentAsByteArray();
			AMQPApplicationMessage msg = new AMQPApplicationMessage(_session.getChannel(),referenceId);
			msg.setMessageHeaders(messageHeaders);
			msg.setRedeliveredFlag(messageTransferBody.getRedelivered());
			
			_msgStore.storeMessage(new String(referenceId), msg);
		}
	}
	
	private void enQueue(AMQPApplicationMessage msg)throws AMQPException
	{
		try
		{	
			_queue.put(msg);
		}
		catch(Exception e)
		{
			throw new AMQPException("Error queueing the messsage",e);
		}
	}

}
