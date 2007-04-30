package org.apache.qpid.nclient.amqp.state;

import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPStateListener 
{
	public void stateChanged(AMQPStateChangedEvent event) throws AMQPException;
}
