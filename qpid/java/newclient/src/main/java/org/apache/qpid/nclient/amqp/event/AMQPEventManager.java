package org.apache.qpid.nclient.amqp.event;

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPEventManager
{
    public void addMethodEventListener(int channelId, Class clazz, AMQPMethodListener l);
    public void removeMethodEventListener(int channelId, Class clazz, AMQPMethodListener l);
    public <B extends AMQMethodBody> boolean notifyEvent(AMQPMethodEvent<B> evt) throws AMQPException;
}