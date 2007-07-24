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

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.MessageCancelBody;
import org.apache.qpid.framing.MessageConsumeBody;
import org.apache.qpid.nclient.amqp.AMQPMessage;
import org.apache.qpid.nclient.api.QpidException;
import org.apache.qpid.nclient.api.QpidMessageConsumer;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.message.AMQPApplicationMessage;

public class QpidMessageConsumerImpl extends AbstractResource implements QpidMessageConsumer
{
	private final BlockingQueue<AMQPApplicationMessage> _queue = new LinkedBlockingQueue<AMQPApplicationMessage>();
	private QpidSessionImpl _session;
	private AMQPMessage _amqpMessage;
	private String _consumerTag;
	private String _queueName;
	private boolean _noLocal;
	private boolean _exclusive;
	
	protected QpidMessageConsumerImpl(QpidSessionImpl session,String consumerTag,String queueName,boolean noLocal,boolean exclusive) throws QpidException
	{
		super("Message Consuer");
		_session = session;		
		_amqpMessage = session.getMessageHelper().getMessageClass();
		_consumerTag = consumerTag;
		_queueName = queueName;
		_noLocal = noLocal;
		_exclusive = exclusive;
	}
	
	/**
	 * -----------------------------------------------
	 * Methods from QpidMessageConsumer class
	 * -----------------------------------------------
	 */
	
	public AMQPApplicationMessage get() throws QpidException
	{
		checkClosed();
		// I want this to do a message.get
		return null;
	}

	public AMQPApplicationMessage receive()throws QpidException
	{
		checkClosed();
		try
		{
			return _queue.take();
		}
		catch (InterruptedException e)
		{
		throw new QpidException("Error occurred while retrieving message",e);
		}
	}
	
	public AMQPApplicationMessage receive(long timeout, TimeUnit tu)throws QpidException
	{
		checkClosed();
		try
		{
			return _queue.poll(timeout, tu);
		}
		catch(Exception e)
		{
			throw new QpidException("Error retrieving message from queue",e);
		}		
	}
	
	public void messageArrived(AMQPApplicationMessage msg)throws QpidException
	{
		try
		{	
			_queue.put(msg);
		}
		catch(Exception e)
		{
			throw new QpidException("Error queueing the messsage",e);
		}
	}

	/**
	 * -----------------------------------------------
	 * Abstract methods from AbstractResource class
	 * -----------------------------------------------
	 */
	protected void openResource() throws AMQPException, QpidException
	{
		// Will wait till the dust settles on the message selectors
		
		final MessageConsumeBody messageConsumeBody = 
			MessageConsumeBody.createMethodBody(
			    _session.getMajor(),
			     _session.getMinor(),
			     new AMQShortString(_consumerTag),// destination/deliveryTag/consumerTag
				_exclusive, //exclusive
				null, //filter
				false, //noAck,
				_noLocal, //noLocal, 
				new AMQShortString(_queueName), //queue
				_session.getAccessTicket() //ticket
				);
		
		final AMQPCallbackHelper cb = new AMQPCallbackHelper();
		HelperTemplate template = new HelperTemplate(){
			
			public void amqpMethodCall() throws AMQPException
			{
				_amqpMessage.consume(messageConsumeBody, cb);
			}
		};
		
		template.invokeAMQPMethodCall("Message consume failed due to");
		
		template.evaulateResponse(cb);
	}
	
	protected void closeResource() throws AMQPException, QpidException
	{
		((QpidMessageHelperImpl)_session.getMessageHelper()).deregisterConsumer(_consumerTag);
		
		final MessageCancelBody messageCancelBody = 
			MessageCancelBody.createMethodBody(
			   			 _session.getMajor(),
					     _session.getMinor(), 
					     new AMQShortString(_queueName));
		
		final AMQPCallbackHelper cb = new AMQPCallbackHelper();
		HelperTemplate template = new HelperTemplate(){
			
			public void amqpMethodCall() throws AMQPException
			{
				_amqpMessage.cancel(messageCancelBody, cb);
			}
		};
		
		template.invokeAMQPMethodCall("Message cancel failed due to");
		
		template.evaulateResponse(cb);
	}

	/**
	 * ----------------------------------------------
	 * Getters for Message Consumer properties
	 * No setters are allowed. Once these properties
	 * are set in the constructor they are not allowed
	 * to be modifed.
	 * ----------------------------------------------
	 */
	public String getConsumerTag()
	{
		return _consumerTag;
	}

	public boolean isExclusive()
	{
		return _exclusive;
	}

	public boolean isNoLocal()
	{
		return _noLocal;
	}
	
	public String getQueueName()
	{
		return _queueName;
	}
}