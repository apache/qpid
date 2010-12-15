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

    public void testSetJMSXDeliveryCount_0_8() throws JMSException
    {
        doTestSetJMSXDeliveryCount(AMQMessageDelegateFactory.FACTORY_0_8);
    }
    
    public void testSetJMSXDeliveryCount_0_10() throws JMSException
    {
        doTestSetJMSXDeliveryCount(AMQMessageDelegateFactory.FACTORY_0_10);
    }
    
    private void doTestSetJMSXDeliveryCount(AMQMessageDelegateFactory<?> factory) throws JMSException
    {
        AbstractJMSMessage abstractMessage = new JMSTextMessage(factory);

        //verify the property is not set
        assertFalse("property should not yet exist", abstractMessage.propertyExists("JMSXDeliveryCount"));

        //check that retrieving the property now throws the expected NFE
        try 
        {
            abstractMessage.getIntProperty("JMSXDeliveryCount");
            fail("property should not be set, so NumberFormatException should be thrown");
        }
        catch (NumberFormatException e)
        {
            //expected, ignore.
        }

        //set the value, verify retrieval
        abstractMessage.setJMSXDeliveryCount(5);
        assertEquals("Value was incorrect", 5, abstractMessage.getIntProperty("JMSXDeliveryCount"));

        //remove the property
        abstractMessage.setJMSXDeliveryCount(null);

        //verify property is cleared
        assertFalse("property should not yet exist", abstractMessage.propertyExists("JMSXDeliveryCount"));

        //check that retrieving the property now throws the expected NFE
        try 
        {
            abstractMessage.getIntProperty("JMSXDeliveryCount");
            fail("property should not be set, so NumberFormatException should be thrown");
        }
        catch (NumberFormatException e)
        {
            //expected, ignore.
        }
    }
}
