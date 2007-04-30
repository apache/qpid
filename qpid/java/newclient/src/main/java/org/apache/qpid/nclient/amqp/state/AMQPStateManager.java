package org.apache.qpid.nclient.amqp.state;

import org.apache.qpid.AMQException;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPStateManager 
{
	public void addListener(AMQPStateType stateType, AMQPStateListener l)throws AMQException;
	
	public void removeListener(AMQPStateType stateType, AMQPStateListener l)throws AMQException;
	
	public void notifyStateChanged(AMQPStateChangedEvent event) throws AMQPException;
}