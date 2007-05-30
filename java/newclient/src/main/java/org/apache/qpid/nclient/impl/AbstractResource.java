package org.apache.qpid.nclient.impl;

import org.apache.qpid.nclient.api.QpidException;
import org.apache.qpid.nclient.core.AMQPException;

/**
 * This abstracts the error handling for open
 * and close methods for a resource. This class
 * eliminates the duplication of error handling 
 * code
 */
public abstract class AbstractResource
{
	private String _resourceName;
	private boolean _closed = true;
	
	public AbstractResource(String resourceName)
	{
		_resourceName = resourceName;
	}
	
	public void open() throws QpidException
	{
		_closed = false;
		try
		{
			openResource();

		}
		catch(Exception e)
		{
			throw new QpidException("Error creating " + _resourceName  + " due to " + e.getMessage(),e);
		}
	}
		
	public void close() throws QpidException
	{
		_closed = true;
		try		
		{
			closeResource();

		}
		catch(Exception e)
		{
			throw new QpidException("Error destroying " + _resourceName  + " due to " + e.getMessage(),e);
		}
		
	}
	
	protected abstract void openResource() throws AMQPException, QpidException;
	
	protected abstract void closeResource() throws AMQPException, QpidException;
	
	public void checkClosed() throws QpidException
	{
		if(_closed)
		{
			throw new QpidException("The resource you are trying to access is closed");
		}
	}
}
