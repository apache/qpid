package org.apache.qpid.nclient.amqp.sample;

import org.apache.qpid.nclient.amqp.state.AMQPStateChangedEvent;
import org.apache.qpid.nclient.amqp.state.AMQPStateListener;
import org.apache.qpid.nclient.core.AMQPException;

public class StateHelper implements AMQPStateListener
{

	public void stateChanged(AMQPStateChangedEvent event) throws AMQPException
	{
		String s = event.getStateType() + " changed state from " +
		           event.getOldState() + " to " + event.getNewState();
		
		System.out.println(s);

	}

}
