package org.apache.qpid.nclient.amqp;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;

public abstract class AMQPCallBack 
{
	private boolean _isComplete = false;
	
	public abstract void brokerResponded(AMQMethodBody body);
	
	public abstract void brokerRespondedWithError(AMQException e);
	
	public void setIsComplete(boolean isComplete)
	{
		_isComplete = isComplete;
	}
	
	public boolean isComplete()
	{
		return _isComplete;		
	}
}
