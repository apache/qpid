package org.apache.qpid.nclient.impl;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.nclient.amqp.AMQPCallBack;

public class AMQPCallbackHelper extends AMQPCallBack
{
	private static final Logger _logger = Logger.getLogger(AMQPCallbackHelper.class);
	
	private AMQMethodBody _body;
	private Exception _e;
	private boolean _isError;
	
	@Override
	public void brokerResponded(AMQMethodBody body)
	{
		_body = body;
		_logger.debug("[Broker has responded " + body);
		_isError = false;
		this.setIsComplete(true);
	}

	@Override
	public void brokerRespondedWithError(AMQException e)
	{
		_e = e;
		_logger.debug("[Broker has responded with an error" + e.getMessage(),e);
	   _isError = true;	
	   this.setIsComplete(true);
	}

	public AMQMethodBody getMethodBody()
	{
		return _body;
	}

	public Exception getException()
	{
		return _e;
	}
	
	public boolean isError()
	{
		return _isError;
	}
	

}
