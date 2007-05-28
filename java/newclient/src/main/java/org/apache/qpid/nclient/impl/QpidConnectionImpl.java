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

import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;

import org.apache.log4j.Logger;
import org.apache.qpid.common.ClientProperties;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionOpenBody;
import org.apache.qpid.framing.ConnectionOpenOkBody;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionSecureOkBody;
import org.apache.qpid.framing.ConnectionStartBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.framing.ConnectionTuneOkBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.FieldTableFactory;
import org.apache.qpid.nclient.amqp.AMQPChannel;
import org.apache.qpid.nclient.amqp.AMQPClassFactory;
import org.apache.qpid.nclient.amqp.AMQPConnection;
import org.apache.qpid.nclient.amqp.AbstractAMQPClassFactory;
import org.apache.qpid.nclient.amqp.qpid.QpidAMQPChannel;
import org.apache.qpid.nclient.amqp.state.AMQPState;
import org.apache.qpid.nclient.amqp.state.AMQPStateChangedEvent;
import org.apache.qpid.nclient.amqp.state.AMQPStateListener;
import org.apache.qpid.nclient.amqp.state.AMQPStateType;
import org.apache.qpid.nclient.api.QpidConnection;
import org.apache.qpid.nclient.api.QpidException;
import org.apache.qpid.nclient.api.QpidSession;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.transport.ConnectionURL;
import org.apache.qpid.nclient.transport.TransportConnectionFactory.ConnectionType;

/**
 * 
 * Once the Session class is implemented the channel logic will be
 * replaced by the session methods.
 *
 */
public class QpidConnectionImpl implements QpidConnection, AMQPStateListener
{
	private static final Logger _logger = Logger.getLogger(QpidConnectionImpl.class);
	
	private byte _major;

	private byte _minor;

	private ConnectionURL _url;
	
	// Need a Class factory per connection
	private AMQPClassFactory _classFactory;

	private int _ticket;
	
	private AMQPConnection _amqpConnection;
	
	private AtomicInteger _channelNo = new AtomicInteger();
	
	private Map<Integer,QpidSession> _sessionMap = new ConcurrentHashMap<Integer,QpidSession>(); 
	
	private Lock _lock = new ReentrantLock();
	
	/** ---------------------------------------------
	 * Methods from o.a.qpid.client.Connection
	 * ----------------------------------------------
	 */
	
	public void close()
	{
		// handle failover
	}

	public void connect(String url) throws QpidException 
	{
		try
		{
			_classFactory = AbstractAMQPClassFactory.getFactoryInstance();
		}
		catch(Exception e)
		{
			throw new QpidException("Unable to create the class factory",e);
		}
		
		try
		{
			//_url = new AMQPConnectionURL("amqp://guest:guest@test/test?brokerlist='tcp://localhost:5672?'");
			_amqpConnection = _classFactory.createConnectionClass(url, ConnectionType.TCP);
		}
		catch(Exception e)
		{
			throw new QpidException("Unable to create a connection to the broker using url " + url + " due to " + e.getMessage(),e);
		}
		
		try
		{
			handleConnectionNegotiation();
		}
		catch(Exception e)
		{
			throw new QpidException("Connection negotiation failed due to " + e.getMessage(),e);
		}
	}

	public QpidSession createSession(int expiryInSeconds) throws QpidException
	{
		AMQPChannel channel = null;
		_lock.lock();
		try
		{
			int channelNo = _channelNo.addAndGet(1);
			channel = _classFactory.createChannelClass(channelNo);
			QpidSession session = new QpidSessionImpl(_classFactory,channel,channelNo, _major,_minor);
			_sessionMap.put(channelNo, session);
			return session;
		}
		catch(AMQPException e)
		{
			throw new QpidException("Unable to create channel class",e);
		}
		finally
		{
			_lock.unlock();
		}		
	}

	/** ---------------------------------------------
	 * Methods from AMQPStateListener
	 * ----------------------------------------------
	 */
	public void stateChanged(AMQPStateChangedEvent event) throws AMQPException
	{
		String s = event.getStateType() + " changed state from " +
		           event.getOldState() + " to " + event.getNewState();
		
		_logger.debug(s);
		
		if(event.getNewState() == AMQPState.CONNECTION_CLOSED)
		{
			//We need to notify the sessions that they need to
			//kick in the fail over logic
			for (Integer sessionId : _sessionMap.keySet())
			{
				QpidSession session = _sessionMap.get(sessionId);
				try
				{
					session.failover();
				}
				catch(Exception e)
				{
					_logger.error("Error executing failover logic for session : " + sessionId, e);
				}
			}
		}

	}
	
	/** ---------------------------------------------
	 * Helper methods
	 * ----------------------------------------------
	 */
	
	public void handleConnectionNegotiation() throws Exception
	{
		_classFactory.getStateManager().addListener(AMQPStateType.CONNECTION_STATE, this);

		//ConnectionStartBody
		ConnectionStartBody connectionStartBody = _amqpConnection.openTCPConnection();
		_major = connectionStartBody.getMajor();
		_minor = connectionStartBody.getMinor();

		FieldTable clientProperties = FieldTableFactory.newFieldTable();
		clientProperties.put(new AMQShortString(ClientProperties.instance.toString()), "Test"); // setting only the client id

		final String locales = new String(connectionStartBody.getLocales(), "utf8");
		final StringTokenizer tokenizer = new StringTokenizer(locales, " ");

		final String mechanism = SecurityHelper.chooseMechanism(connectionStartBody.getMechanisms());

		SaslClient sc = Sasl.createSaslClient(new String[]
		{ mechanism }, null, "AMQP", "localhost", null, SecurityHelper.createCallbackHandler(mechanism, _url));

		ConnectionStartOkBody connectionStartOkBody = ConnectionStartOkBody.createMethodBody(_major, _minor, clientProperties, new AMQShortString(
				tokenizer.nextToken()), new AMQShortString(mechanism), (sc.hasInitialResponse() ? sc.evaluateChallenge(new byte[0]) : null));
		// ConnectionSecureBody 
		AMQMethodBody body = _amqpConnection.startOk(connectionStartOkBody);
		ConnectionTuneBody connectionTuneBody;

		if (body instanceof ConnectionSecureBody)
		{
			ConnectionSecureBody connectionSecureBody = (ConnectionSecureBody) body;
			ConnectionSecureOkBody connectionSecureOkBody = ConnectionSecureOkBody.createMethodBody(_major, _minor, sc
					.evaluateChallenge(connectionSecureBody.getChallenge()));
			//Assuming the server is not going to send another challenge
			connectionTuneBody = (ConnectionTuneBody) _amqpConnection.secureOk(connectionSecureOkBody);

		}
		else
		{
			connectionTuneBody = (ConnectionTuneBody) body;
		}

		// Using broker supplied values
		ConnectionTuneOkBody connectionTuneOkBody = ConnectionTuneOkBody.createMethodBody(_major, _minor, connectionTuneBody.getChannelMax(),
				connectionTuneBody.getFrameMax(), connectionTuneBody.getHeartbeat());
		_amqpConnection.tuneOk(connectionTuneOkBody);

		ConnectionOpenBody connectionOpenBody = ConnectionOpenBody.createMethodBody(_major, _minor, null, true, new AMQShortString(_url
				.getVirtualHost()));

		ConnectionOpenOkBody connectionOpenOkBody = _amqpConnection.open(connectionOpenBody);
	}
	
}
