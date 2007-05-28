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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ChannelOpenBody;
import org.apache.qpid.framing.ChannelOpenOkBody;
import org.apache.qpid.nclient.amqp.AMQPChannel;
import org.apache.qpid.nclient.amqp.AMQPClassFactory;
import org.apache.qpid.nclient.api.QpidConnection;
import org.apache.qpid.nclient.api.QpidExchangeHelper;
import org.apache.qpid.nclient.api.QpidMessageConsumer;
import org.apache.qpid.nclient.api.QpidMessageHelper;
import org.apache.qpid.nclient.api.QpidMessageProducer;
import org.apache.qpid.nclient.api.QpidException;
import org.apache.qpid.nclient.api.QpidQueueHelper;
import org.apache.qpid.nclient.api.QpidSession;
import org.apache.qpid.nclient.api.QpidTransactionHelper;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.protocol.AMQConstant;

/**
 * According to the 0-9 spec, the session is built on channels(1-1 map) and when a channel is closed
 * the session should be closed. However with the introdution of the session class in 0-10
 * this may change. Therefore I will not implement that logic yet.
 * 
 * Once the dust settles there will be a Failover Helper that will manage the sessions
 * failover logic. 
 */
public class QpidSessionImpl extends AbstractResource implements QpidSession
{
	private AMQPChannel _channelClass;
	private int _channel;
	private byte _major;
	private byte _minor;
    private AMQPClassFactory _classFactory;
    private int _ticket = 0; //currently useless
    private QpidExchangeHelperImpl _qpidExchangeHelper;
    private QpidQueueHelperImpl _qpidQueueHelper;
    private QpidMessageConsumerImpl _qpidMessageConsumer;
    private QpidMessageProducerImpl _qpidMessageProducer;
    private AtomicBoolean _closed;
    private Lock _sessionCloseLock = new ReentrantLock();
    
    // this will be used as soon as Session class is finalized
    private int _expiryInSeconds = QpidConnection.SESSION_EXPIRY_TIED_TO_CHANNEL;
    private QpidConnection _qpidConnection;
    
	public QpidSessionImpl(AMQPClassFactory classFactory,AMQPChannel channelClass,int channel,byte major, byte minor)
	{
		super("Session");
		_channelClass = channelClass;
		_channel = channel;
		_major = major;
		_minor = minor;
		_classFactory = classFactory;		
		_qpidConnection = null;
	}

	/**
	 * -----------------------------------------------------
	 * Methods introduced by AbstractResource
	 * -----------------------------------------------------
	 */
	protected void openResource() throws AMQPException
	{
		// These methods will be changed to session methods
		openChannel();
	}	
	
	protected void closeResource() throws AMQPException
	{
		ChannelCloseBody channelCloseBody = ChannelCloseBody.createMethodBody(_major, _minor,
				                                                              0, //classId
				                                                              0, //methodId
				                                                              AMQConstant.REPLY_SUCCESS.getCode(),
				                                                              new AMQShortString("Qpid Client closing channel"));
		
		_channelClass.close(channelCloseBody);
		
		_classFactory.destroyChannelClass(_channel, _channelClass);
		if (_qpidQueueHelper != null)
		{
			_qpidQueueHelper.closeResource();
		}
		if (_qpidExchangeHelper != null)
		{
			_qpidExchangeHelper.closeResource();
		}
		if(_qpidMessageConsumer != null)
		{
			_qpidMessageConsumer.closeResource();
		}
		if(_qpidMessageProducer != null)
		{
			_qpidMessageProducer.closeResource();
		}
	}
	
	/**
	 * -----------------------------------------------------
	 * Methods introduced by QpidSession
	 * -----------------------------------------------------
	 */
	public void close() throws QpidException
	{
		if (!_closed.getAndSet(true))
		{
			_sessionCloseLock.lock();
			try
			{
				super.close();
			}
			finally
			{
				_sessionCloseLock.unlock();
			}
		}
	}

	public void resume() throws QpidException
	{
		
	}
	
	// not intended to be used at the jms layer
	public void suspend() throws QpidException
	{
		
	}
	
	public void failover() throws QpidException
	{
		if(_expiryInSeconds == QpidConnection.SESSION_EXPIRY_TIED_TO_CHANNEL)
		{
			// then close the session
		}
		else
		{
			//kick in the failover logic
		}
	}
	
	public QpidMessageConsumer createConsumer() throws QpidException
	{
		if (_qpidMessageConsumer == null)
		{
			_qpidMessageConsumer = new QpidMessageConsumerImpl(this);
			_qpidMessageConsumer.open();
		}
		return _qpidMessageConsumer;
	}

	public QpidMessageProducer createProducer() throws QpidException
	{
		if (_qpidMessageProducer == null)
		{
			_qpidMessageProducer = new QpidMessageProducerImpl(this);
			_qpidMessageProducer.open();
		}
		return _qpidMessageProducer;
	}

	/** ------------------------------------------
	 *  These helper classes are employed to reduce
	 *  the clutter in session classes and improve
	 *  readability
	 * ------------------------------------------
	 */
	public QpidExchangeHelper getExchangeHelper() throws QpidException
	{
		if (_qpidExchangeHelper == null)
		{
			_qpidExchangeHelper = new QpidExchangeHelperImpl(this);
			_qpidExchangeHelper.open();
		}
		return _qpidExchangeHelper;
	}

	public QpidMessageHelper getMessageHelper() throws QpidException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public QpidQueueHelper getQueueHelper() throws QpidException
	{
		if (_qpidQueueHelper == null)
		{
			_qpidQueueHelper = new QpidQueueHelperImpl(this);
			_qpidQueueHelper.open();
		}
		return _qpidQueueHelper;
	}
	
	public QpidTransactionHelper getTransactionHelper()throws QpidException
	{
		return null;
	}
	
	/** ------------------------------------------
	 *  These protected methods are for the qpid 
	 *  implementation of the api package
	 * ------------------------------------------
	 */
	protected byte getMinor()
	{
		return _minor;
	}

	protected byte getMajor()
	{
		return _major;
	}
	
	protected int getChannel()
	{
		return _channel;
	}
	
	protected AMQPClassFactory getClassFactory()
	{
		return _classFactory;
	}
	
	protected int getAccessTicket()
	{
		return _ticket;
	}
	
	private void openChannel() throws AMQPException
	{
		ChannelOpenBody channelOpenBody = ChannelOpenBody.createMethodBody(_major, _minor, new AMQShortString("myChannel1"));
		ChannelOpenOkBody channelOpenOkBody = _channelClass.open(channelOpenBody);
	}
}
