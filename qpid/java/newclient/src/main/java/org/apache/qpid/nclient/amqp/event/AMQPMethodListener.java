package org.apache.qpid.nclient.amqp.event;

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPMethodListener 
{

	public <B extends AMQMethodBody> boolean methodReceived(AMQPMethodEvent<B> evt) throws AMQPException;
	
}
