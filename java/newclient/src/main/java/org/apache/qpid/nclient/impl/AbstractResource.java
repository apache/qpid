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
	
	public AbstractResource(String resourceName)
	{
		_resourceName = resourceName;
	}
	
	public void open() throws QpidException
	{
		try
		{
			openResource();

		}
		catch(AMQPException e)
		{
			throw new QpidException("Error creating " + _resourceName  + " due to " + e.getMessage(),e);
		}
	}
		
	public void close() throws QpidException
	{
		try
		{
			closeResource();

		}
		catch(Exception e)
		{
			throw new QpidException("Error destroying " + _resourceName  + " due to " + e.getMessage(),e);
		}
		
	}
	
	protected abstract void openResource() throws AMQPException;
	
	protected abstract void closeResource() throws AMQPException;
}
