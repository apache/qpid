package org.apache.qpid.nclient.impl;

import org.apache.qpid.nclient.api.QpidException;
import org.apache.qpid.nclient.core.AMQPException;

/**
 * This class creates helper methods to avoid
 * duplication of routine logic and error handling
 * that can be reused.
 */
public abstract class HelperTemplate
{
	public abstract void amqpMethodCall() throws AMQPException;
	
	public void invokeAMQPMethodCall(String msg) throws QpidException
	{
		try
		{
			amqpMethodCall();
		}
		catch(Exception e)
		{
			throw new QpidException(msg + e.getMessage(),e);
		}
	}
	
	public void evaulateResponse(AMQPCallbackHelper cb) throws QpidException
	{
		// Blocking for response
		while (!cb.isComplete())
		{
		}
		
		if (cb.isError())
		{
			throw new QpidException("The broker responded with an error",cb.getException());
		}
	}
}
