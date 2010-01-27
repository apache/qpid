package org.apache.qpid.client.message;

import javax.jms.JMSException;

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;

public class AMQPEncodedMapMessageFactory extends AbstractJMSMessageFactory
{

    @Override
    protected AbstractJMSMessage createMessage(AMQMessageDelegate delegate,
            ByteBuffer data) throws AMQException
    {
        return new AMQPEncodedMapMessage(delegate,data);
    }

    @Override
    public AbstractJMSMessage createMessage(
            AMQMessageDelegateFactory delegateFactory) throws JMSException
    {
        return new AMQPEncodedMapMessage(delegateFactory);
    }

}
