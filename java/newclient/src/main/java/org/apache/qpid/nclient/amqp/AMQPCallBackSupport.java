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
package org.apache.qpid.nclient.amqp;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.nclient.amqp.event.AMQPMethodEvent;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.QpidConstants;

public abstract class AMQPCallBackSupport 
{
	private SecureRandom _localCorrelationIdGenerator = new SecureRandom();
	protected ConcurrentHashMap<Long,AMQPCallBack> _cbMap = new ConcurrentHashMap<Long,AMQPCallBack>();
	
	//the channelId assigned for this instance
	protected int _channelId;
	
	public AMQPCallBackSupport(int channelId)
	{
		_channelId = channelId; 
	}
	
	private long getNextCorrelationId()
	{
		return _localCorrelationIdGenerator.nextLong();
	}
	
	
	// For methods that still use nowait, hopefully they will remove nowait
	protected AMQPMethodEvent handleNoWait(boolean noWait,AMQMethodBody methodBody,AMQPCallBack cb)
	{
		if(noWait)
		{
		    	AMQPMethodEvent msg = new AMQPMethodEvent(_channelId,methodBody,QpidConstants.EMPTY_CORRELATION_ID);
			return msg;
		}
		else
		{
		    	//  u only need to register if u are expecting a response
			long localCorrelationId = getNextCorrelationId();
			AMQPMethodEvent msg = new AMQPMethodEvent(_channelId,methodBody,QpidConstants.EMPTY_CORRELATION_ID,localCorrelationId);
			_cbMap.put(localCorrelationId, cb);
			return msg;						
		}
	}
	
	protected AMQPMethodEvent handleAsynchronousCall(AMQMethodBody methodBody,AMQPCallBack cb)
	{
		long localCorrelationId = getNextCorrelationId();
		AMQPMethodEvent msg = new AMQPMethodEvent(_channelId,methodBody,QpidConstants.EMPTY_CORRELATION_ID,localCorrelationId);
		_cbMap.put(localCorrelationId, cb);
		return msg;
	}
	
	protected void invokeCallBack(long localCorrelationId, AMQMethodBody methodBody)throws AMQPException
	{
		if(_cbMap.containsKey(localCorrelationId))
		{
			AMQPCallBack cb = (AMQPCallBack)_cbMap.get(localCorrelationId);
			if(cb == null)
			{
			    throw new AMQPException("Unable to find the callback object responsible for handling " + methodBody);
			}
			else
			{
			    	cb.setIsComplete(true);
				cb.brokerResponded(methodBody);
			}
			_cbMap.remove(localCorrelationId);
		}
		else
		{
		    //ignore, as this event is for another class instance
		}
	}

}
