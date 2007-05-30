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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ChannelOpenBody;
import org.apache.qpid.framing.ChannelOpenOkBody;
import org.apache.qpid.nclient.amqp.AMQPChannel;
import org.apache.qpid.nclient.amqp.AMQPClassFactory;
import org.apache.qpid.nclient.api.QpidConnection;
import org.apache.qpid.nclient.api.QpidConstants;
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
 * this will change. Therefore I will not implement failover logic yet.
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
    private QpidMessageHelperImpl _qpidMessageHelper;
    private List<QpidMessageProducerImpl> _producers = new ArrayList<QpidMessageProducerImpl>();
    private AtomicBoolean _closed;
    private AtomicInteger _consumerTag;
    private Lock _sessionCloseLock = new ReentrantLock();
        
    // this will be used as soon as Session class is finalized
    private int _expiryInSeconds = QpidConstants.SESSION_EXPIRY_TIED_TO_CHANNEL;
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
	protected void openResource() throws AMQPException, QpidException
	{
		// These methods will be changed to session methods
		openChannel();
		
		//initialize method helper
		_qpidMessageHelper = new QpidMessageHelperImpl(this);
		_qpidMessageHelper.open();
		
		_qpidExchangeHelper = new QpidExchangeHelperImpl(this);
		_qpidExchangeHelper.open();
		
		_qpidQueueHelper = new QpidQueueHelperImpl(this);
		_qpidQueueHelper.open();
	}	
	
	protected void closeResource() throws AMQPException, QpidException
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
		if(_qpidMessageHelper != null)
		{
			// The MessageHelper will close the Message Consumers too
			_qpidMessageHelper.closeResource();
		}	
		for (QpidMessageProducerImpl producer: _producers)
		{
			producer.close();
		}
	}
	
	@Override
	public void checkClosed() throws QpidException
	{
		if(_closed.get())
		{
			throw new QpidException("The resource you are trying to access is closed");
		}
	}
	
	/**
	 * -----------------------------------------------------
	 * Methods introduced by QpidSession
	 * -----------------------------------------------------
	 */
	@Override
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
		if (!_closed.getAndSet(false))
		{
			_sessionCloseLock.lock();
			try
			{
				super.open();
			}
			finally
			{
				_sessionCloseLock.unlock();
			}
		}
	}
	
	// not intended to be used at the jms layer
	public void suspend() throws QpidException
	{
		
	}
	
	public void failover() throws QpidException
	{
		if(_expiryInSeconds == QpidConstants.SESSION_EXPIRY_TIED_TO_CHANNEL)
		{
			// then close the session
		}
		else
		{
			//kick in the failover logic
		}
	}
	
	public QpidMessageConsumer createConsumer(String queueName, boolean noLocal, boolean exclusive) throws QpidException
	{
		checkClosed();
		String consumerTag = String.valueOf(_consumerTag.incrementAndGet());
		QpidMessageConsumerImpl qpidMessageConsumer = new QpidMessageConsumerImpl(this,consumerTag,queueName,noLocal,exclusive);
		_qpidMessageHelper.registerConsumer(consumerTag, qpidMessageConsumer); 
		
		return qpidMessageConsumer;
	}

	public QpidMessageProducer createProducer() throws QpidException
	{
		checkClosed();
		QpidMessageProducerImpl qpidMessageProducer = new QpidMessageProducerImpl(this);
		_producers.add(qpidMessageProducer);
		
		return qpidMessageProducer;
	}

	/** ------------------------------------------
	 *  These helper classes are employed to reduce
	 *  the clutter in session classes and improve
	 *  readability
	 * ------------------------------------------
	 */
	public QpidExchangeHelper getExchangeHelper() throws QpidException
	{
		checkClosed();
		return _qpidExchangeHelper;
	}

	public QpidMessageHelper getMessageHelper() throws QpidException
	{
		checkClosed();
		return _qpidMessageHelper;
	}

	public QpidQueueHelper getQueueHelper() throws QpidException
	{
		checkClosed();
		return _qpidQueueHelper;
	}
	
	public QpidTransactionHelper getTransactionHelper()throws QpidException
	{
		checkClosed();
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
