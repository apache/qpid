/**
 * User: Robert Greig
 * Date: 12-Dec-2006
 ******************************************************************************
 * (c) Copyright JP Morgan Chase Ltd 2006. All rights reserved. No part of
 * this program may be photocopied reproduced or translated to another
 * program language without prior written consent of JP Morgan Chase Ltd
 ******************************************************************************/
package org.apache.qpid.test.unit.message;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQHeadersExchange;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.PropertyFieldTable;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;

import javax.jms.*;

/**
 * @author Apache Software Foundation
 */
public class StreamMessageTest extends TestCase
{

    private static final Logger _logger = Logger.getLogger(StreamMessageTest.class);

    public String _connectionString = "vm://:1";

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }


    protected void tearDown() throws Exception
    {
        super.tearDown();
        TransportConnection.killAllVMBrokers();
    }

    public void testStreamMessageEOF() throws Exception
    {
        Connection con = new AMQConnection("vm://:1", "guest", "guest", "consumer1", "/test");
        AMQSession consumerSession = (AMQSession) con.createSession(false, Session.CLIENT_ACKNOWLEDGE);


        AMQHeadersExchange queue = new AMQHeadersExchange(new AMQBindingURL(ExchangeDefaults.HEADERS_EXCHANGE_CLASS+"://"+ExchangeDefaults.HEADERS_EXCHANGE_NAME+"/test/queue1?"+ BindingURL.OPTION_ROUTING_KEY+"='F0000=1'"));
        FieldTable ft = new PropertyFieldTable();
        ft.setString("F1000","1");
        MessageConsumer consumer = consumerSession.createConsumer(queue, AMQSession.DEFAULT_PREFETCH_LOW_MARK, AMQSession.DEFAULT_PREFETCH_HIGH_MARK, false, false, (String)null, ft);


        //force synch to ensure the consumer has resulted in a bound queue
        ((AMQSession) consumerSession).declareExchangeSynch(ExchangeDefaults.HEADERS_EXCHANGE_NAME, ExchangeDefaults.HEADERS_EXCHANGE_CLASS);

        Connection con2 = new AMQConnection("vm://:1", "guest", "guest", "producer1", "/test");

        AMQSession producerSession = (AMQSession) con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        // Need to start the "producer" connection in order to receive bounced messages
        _logger.info("Starting producer connection");
        con2.start();


        MessageProducer mandatoryProducer = producerSession.createProducer(queue);

        // Third test - should be routed
        _logger.info("Sending routable message");
        StreamMessage msg =  producerSession.createStreamMessage();

        msg.setStringProperty("F1000","1");

        msg.writeByte((byte)42);

        mandatoryProducer.send(msg);



        _logger.info("Starting consumer connection");
        con.start();

        StreamMessage msg2 = (StreamMessage) consumer.receive();

        byte b1 = msg2.readByte();
        try
        {
            byte b2 = msg2.readByte();
        }
        catch (Exception e)
        {
            assertTrue("Expected MessageEOFException: " + e, e instanceof MessageEOFException);
        }
    }

    public void testModifyReceivedMessageExpandsBuffer() throws Exception
    {
        Connection con = new AMQConnection("vm://:1", "guest", "guest", "consumer1", "/test");
        AMQSession consumerSession = (AMQSession) con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        AMQQueue queue = new AMQQueue("testQ");
        MessageConsumer consumer = consumerSession.createConsumer(queue);
        consumer.setMessageListener(new MessageListener()
        {

            public void onMessage(Message message)
            {
                StreamMessage sm = (StreamMessage) message;
                try
                {
                    sm.clearBody();
                    sm.writeString("dfgjshfslfjshflsjfdlsjfhdsljkfhdsljkfhsd");
                }
                catch (JMSException e)
                {
                    _logger.error("Error when writing large string to received msg: " + e, e);
                    fail("Error when writing large string to received msg" + e);
                }
            }
        });
        Connection con2 = new AMQConnection("vm://:1", "guest", "guest", "producer1", "/test");
        AMQSession producerSession = (AMQSession) con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer mandatoryProducer = producerSession.createProducer(queue);
        con.start();
        StreamMessage sm = producerSession.createStreamMessage();
        sm.writeInt(42);
        mandatoryProducer.send(sm);
        Thread.sleep(2000);
    }
}
