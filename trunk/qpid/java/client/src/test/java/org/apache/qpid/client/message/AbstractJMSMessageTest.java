package org.apache.qpid.client.message;

import javax.jms.JMSException;

import junit.framework.TestCase;

public class AbstractJMSMessageTest extends TestCase
{

    public void testSetNullJMSReplyTo08() throws JMSException
    {
        JMSTextMessage message = new JMSTextMessage(AMQMessageDelegateFactory.FACTORY_0_8);
        try 
        {
            message.setJMSReplyTo(null);
        }
        catch (IllegalArgumentException e)
        {
            fail("Null destination should be allowed");
        }
    }

    public void testSetNullJMSReplyTo10() throws JMSException
    {
        JMSTextMessage message = new JMSTextMessage(AMQMessageDelegateFactory.FACTORY_0_10);
        try 
        {
            message.setJMSReplyTo(null);
        }
        catch (IllegalArgumentException e)
        {
            fail("Null destination should be allowed");
        }
    }

}
