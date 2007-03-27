package org.apache.qpid.nclient.amqp.state;

import org.apache.qpid.AMQException;

public interface AMQPStateManager 
{

	public void addListener(AMQPStateListener l)throws AMQException;
	
	public void removeListener(AMQPStateListener l)throws AMQException;
}