package org.apache.qpid.nclient.util;

import org.apache.qpid.nclient.core.AMQPException;

public class AMQPValidator 
{
	public static void throwExceptionOnNull(Object obj, String msg) throws AMQPException
	{
		if(obj == null)
		{
			throw new AMQPException(msg);
		}
	}
}
