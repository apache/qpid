package org.apache.qpid.nclient.amqp;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.nclient.core.QpidConstants;
import org.apache.qpid.nclient.model.AMQPMethodEvent;

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
			// u only need to register if u are expecting a response
			long localCorrelationId = getNextCorrelationId();
			AMQPMethodEvent msg = new AMQPMethodEvent(_channelId,methodBody,QpidConstants.EMPTY_CORRELATION_ID,localCorrelationId);
			_cbMap.put(localCorrelationId, cb);
			return msg; 
		}
		else
		{
			AMQPMethodEvent msg = new AMQPMethodEvent(_channelId,methodBody,QpidConstants.EMPTY_CORRELATION_ID);
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
	
	protected void invokeCallBack(long localCorrelationId, AMQMethodBody methodBody)
	{
		if(_cbMap.contains(localCorrelationId))
		{
			AMQPCallBack cb = (AMQPCallBack)_cbMap.get(localCorrelationId);
			cb.brokerResponded(methodBody);
		}
	}

}
