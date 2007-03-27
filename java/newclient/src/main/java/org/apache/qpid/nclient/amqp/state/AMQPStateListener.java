package org.apache.qpid.nclient.amqp.state;

import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPStateListener 
{
	public void stateChanged(AMQPState oldState, AMQPState newState) throws AMQPException;
}
