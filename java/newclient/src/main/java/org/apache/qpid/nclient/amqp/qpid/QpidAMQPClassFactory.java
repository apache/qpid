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
package org.apache.qpid.nclient.amqp.qpid;

import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ChannelCloseOkBody;
import org.apache.qpid.framing.ChannelFlowBody;
import org.apache.qpid.framing.ChannelFlowOkBody;
import org.apache.qpid.framing.ChannelOkBody;
import org.apache.qpid.framing.ChannelOpenOkBody;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionCloseOkBody;
import org.apache.qpid.framing.ConnectionOpenOkBody;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionStartBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.framing.ExchangeDeclareOkBody;
import org.apache.qpid.framing.ExchangeDeleteOkBody;
import org.apache.qpid.framing.MessageAppendBody;
import org.apache.qpid.framing.MessageCancelBody;
import org.apache.qpid.framing.MessageCheckpointBody;
import org.apache.qpid.framing.MessageCloseBody;
import org.apache.qpid.framing.MessageGetBody;
import org.apache.qpid.framing.MessageOffsetBody;
import org.apache.qpid.framing.MessageOkBody;
import org.apache.qpid.framing.MessageOpenBody;
import org.apache.qpid.framing.MessageQosBody;
import org.apache.qpid.framing.MessageRecoverBody;
import org.apache.qpid.framing.MessageRejectBody;
import org.apache.qpid.framing.MessageResumeBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.framing.QueueBindOkBody;
import org.apache.qpid.framing.QueueDeclareOkBody;
import org.apache.qpid.framing.QueueDeleteOkBody;
import org.apache.qpid.framing.QueuePurgeOkBody;
import org.apache.qpid.framing.QueueUnbindOkBody;
import org.apache.qpid.nclient.amqp.AMQPChannel;
import org.apache.qpid.nclient.amqp.AMQPClassFactory;
import org.apache.qpid.nclient.amqp.AMQPConnection;
import org.apache.qpid.nclient.amqp.AMQPExchange;
import org.apache.qpid.nclient.amqp.AMQPMessage;
import org.apache.qpid.nclient.amqp.AMQPMessageCallBack;
import org.apache.qpid.nclient.amqp.AMQPQueue;
import org.apache.qpid.nclient.amqp.event.AMQPEventManager;
import org.apache.qpid.nclient.amqp.state.AMQPStateManager;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.DefaultPhaseContext;
import org.apache.qpid.nclient.core.Phase;
import org.apache.qpid.nclient.core.PhaseContext;
import org.apache.qpid.nclient.core.QpidConstants;
import org.apache.qpid.nclient.transport.AMQPConnectionURL;
import org.apache.qpid.nclient.transport.ConnectionURL;
import org.apache.qpid.nclient.transport.TransportConnection;
import org.apache.qpid.nclient.transport.TransportConnectionFactory;
import org.apache.qpid.nclient.transport.TransportConnectionFactory.ConnectionType;
import org.apache.qpid.url.URLSyntaxException;

/**
 * The Class Factory creates AMQP Class
 * equivalents defined in the spec.
 * 
 * There should one instance per connection.
 * The factory class creates all the support
 * classes and provides an instance of the 
 * AMQP class in ready-to-use state.
 *
 */
public class QpidAMQPClassFactory implements AMQPClassFactory
{
	//Need an event manager per connection
	private AMQPEventManager _eventManager = new QpidEventManager();

	// Need a state manager per connection
	private AMQPStateManager _stateManager = new QpidStateManager();

	//Need a phase pipe per connection
	private Phase _phase;

	//One instance per connection
	private QpidAMQPConnection _amqpConnection;

	public QpidAMQPClassFactory()
	{

	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#createConnection(java.lang.String, org.apache.qpid.nclient.transport.TransportConnectionFactory.ConnectionType)
	 */
	public AMQPConnection createConnectionClass(String urlStr, ConnectionType type) throws AMQPException, URLSyntaxException
	{
		AMQPConnectionURL url = new AMQPConnectionURL(urlStr);
		return createConnectionClass(url, type);
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#createConnectionClass(org.apache.qpid.nclient.transport.ConnectionURL, org.apache.qpid.nclient.transport.TransportConnectionFactory.ConnectionType)
	 */
	public AMQPConnection createConnectionClass(ConnectionURL url, ConnectionType type) throws AMQPException
	{
		if (_amqpConnection == null)
		{
			PhaseContext ctx = new DefaultPhaseContext();
			ctx.setProperty(QpidConstants.EVENT_MANAGER, _eventManager);

			TransportConnection conn = TransportConnectionFactory.createTransportConnection(url, type, ctx);
			_amqpConnection = new QpidAMQPConnection(conn, _stateManager);
			_eventManager.addMethodEventListener(QpidConstants.CHANNEL_ZERO, ConnectionStartBody.class, _amqpConnection);
			_eventManager.addMethodEventListener(QpidConstants.CHANNEL_ZERO, ConnectionSecureBody.class, _amqpConnection);
			_eventManager.addMethodEventListener(QpidConstants.CHANNEL_ZERO, ConnectionTuneBody.class, _amqpConnection);
			_eventManager.addMethodEventListener(QpidConstants.CHANNEL_ZERO, ConnectionOpenOkBody.class, _amqpConnection);
			_eventManager.addMethodEventListener(QpidConstants.CHANNEL_ZERO, ConnectionCloseBody.class, _amqpConnection);
			_eventManager.addMethodEventListener(QpidConstants.CHANNEL_ZERO, ConnectionCloseOkBody.class, _amqpConnection);
		}
		return _amqpConnection;
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#createChannelClass(int)
	 */
	public AMQPChannel createChannelClass(int channel) throws AMQPException
	{
		checkIfConnectionStarted();
		QpidAMQPChannel amqpChannel = new QpidAMQPChannel(channel, _phase,_stateManager);
		_eventManager.addMethodEventListener(channel, ChannelOpenOkBody.class, amqpChannel);
		_eventManager.addMethodEventListener(channel, ChannelCloseBody.class, amqpChannel);
		_eventManager.addMethodEventListener(channel, ChannelCloseOkBody.class, amqpChannel);
		_eventManager.addMethodEventListener(channel, ChannelFlowBody.class, amqpChannel);
		_eventManager.addMethodEventListener(channel, ChannelFlowOkBody.class, amqpChannel);
		_eventManager.addMethodEventListener(channel, ChannelOkBody.class, amqpChannel);
		return amqpChannel;
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#destroyChannelClass(int, org.apache.qpid.nclient.amqp.AMQPChannel)
	 */
	public void destroyChannelClass(int channel, QpidAMQPChannel amqpChannel) throws AMQPException
	{
		_eventManager.removeMethodEventListener(channel, ChannelOpenOkBody.class, amqpChannel);
		_eventManager.removeMethodEventListener(channel, ChannelCloseBody.class, amqpChannel);
		_eventManager.removeMethodEventListener(channel, ChannelCloseOkBody.class, amqpChannel);
		_eventManager.removeMethodEventListener(channel, ChannelFlowBody.class, amqpChannel);
		_eventManager.removeMethodEventListener(channel, ChannelFlowOkBody.class, amqpChannel);
		_eventManager.removeMethodEventListener(channel, ChannelOkBody.class, amqpChannel);
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#createExchangeClass(int)
	 */
	public AMQPExchange createExchangeClass(int channel) throws AMQPException
	{
		checkIfConnectionStarted();
		QpidAMQPExchange amqpExchange = new QpidAMQPExchange(channel, _phase);
		_eventManager.addMethodEventListener(channel, ExchangeDeclareOkBody.class, amqpExchange);
		_eventManager.addMethodEventListener(channel, ExchangeDeleteOkBody.class, amqpExchange);
		return amqpExchange;
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#destoryExchangeClass(int, org.apache.qpid.nclient.amqp.AMQPExchange)
	 */
	public void destoryExchangeClass(int channel, QpidAMQPExchange amqpExchange) throws AMQPException
	{
		_eventManager.removeMethodEventListener(channel, ExchangeDeclareOkBody.class, amqpExchange);
		_eventManager.removeMethodEventListener(channel, ExchangeDeleteOkBody.class, amqpExchange);
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#createQueueClass(int)
	 */
	public AMQPQueue createQueueClass(int channel) throws AMQPException
	{
		checkIfConnectionStarted();
		QpidAMQPQueue amqpQueue = new QpidAMQPQueue(channel, _phase);
		_eventManager.addMethodEventListener(channel, QueueDeclareOkBody.class, amqpQueue);
		_eventManager.addMethodEventListener(channel, QueueBindOkBody.class, amqpQueue);
		_eventManager.addMethodEventListener(channel, QueueUnbindOkBody.class, amqpQueue);
		_eventManager.addMethodEventListener(channel, QueuePurgeOkBody.class, amqpQueue);
		_eventManager.addMethodEventListener(channel, QueueDeleteOkBody.class, amqpQueue);
		return amqpQueue;
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#destroyQueueClass(int, org.apache.qpid.nclient.amqp.AMQPQueue)
	 */
	public void destroyQueueClass(int channel, QpidAMQPQueue amqpQueue) throws AMQPException
	{
		_eventManager.removeMethodEventListener(channel, QueueDeclareOkBody.class, amqpQueue);
		_eventManager.removeMethodEventListener(channel, QueueBindOkBody.class, amqpQueue);
		_eventManager.removeMethodEventListener(channel, QueueUnbindOkBody.class, amqpQueue);
		_eventManager.removeMethodEventListener(channel, QueuePurgeOkBody.class, amqpQueue);
		_eventManager.removeMethodEventListener(channel, QueueDeleteOkBody.class, amqpQueue);
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#createMessageClass(int, org.apache.qpid.nclient.amqp.AMQPMessageCallBack)
	 */
	public AMQPMessage createMessageClass(int channel, AMQPMessageCallBack messageCb) throws AMQPException
	{
		checkIfConnectionStarted();
		QpidAMQPMessage amqpMessage = new QpidAMQPMessage(channel, _phase, messageCb);
		_eventManager.addMethodEventListener(channel, MessageAppendBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageCancelBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageCheckpointBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageCloseBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageGetBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageOffsetBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageOkBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageOpenBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageRecoverBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageRejectBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageResumeBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageQosBody.class, amqpMessage);
		_eventManager.addMethodEventListener(channel, MessageTransferBody.class, amqpMessage);

		return amqpMessage;
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#destoryMessageClass(int, org.apache.qpid.nclient.amqp.AMQPMessage)
	 */
	public void destoryMessageClass(int channel, QpidAMQPMessage amqpMessage) throws AMQPException
	{
		_eventManager.removeMethodEventListener(channel, MessageAppendBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageCancelBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageCheckpointBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageCloseBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageGetBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageOffsetBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageOkBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageOpenBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageRecoverBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageRejectBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageResumeBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageQosBody.class, amqpMessage);
		_eventManager.removeMethodEventListener(channel, MessageTransferBody.class, amqpMessage);
	}

	//This class should register as a state listener for AMQPConnection
	private void checkIfConnectionStarted() throws AMQPException
	{
		if (_phase == null)
		{
			_phase = _amqpConnection.getPhasePipe();

			if (_phase == null)
			{
				throw new AMQPException("Cannot create a channel until connection is ready");
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#getEventManager()
	 */
	public AMQPEventManager getEventManager()
	{
		return _eventManager;
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPClassFactory#getStateManager()
	 */
	public AMQPStateManager getStateManager()
	{
		return _stateManager;
	}
}
