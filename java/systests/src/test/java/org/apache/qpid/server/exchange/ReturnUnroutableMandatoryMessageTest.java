package org.apache.qpid.server.exchange;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.apache.qpid.test.VMBrokerSetup;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.TestApplicationRegistry;
import org.apache.qpid.client.*;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.PropertyFieldTable;

import javax.jms.*;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

public class ReturnUnroutableMandatoryMessageTest extends TestCase implements ExceptionListener
{
    private static final Logger _logger = Logger.getLogger(ReturnUnroutableMandatoryMessageTest.class);

    private final List<Message> _bouncedMessageList = Collections.synchronizedList(new ArrayList<Message>());

    static
    {
        String workdir = System.getProperty("QPID_WORK");
        if (workdir == null || workdir.equals(""))
        {
            String tempdir = System.getProperty("java.io.tmpdir");
            System.out.println("QPID_WORK not set using tmp directory: " + tempdir);
            System.setProperty("QPID_WORK", tempdir);
        }
//        DOMConfigurator.configure("../broker/etc/log4j.xml");
    }

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
        ApplicationRegistry.initialise(new TestApplicationRegistry(), 1);
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        TransportConnection.killAllVMBrokers();
    }

    /**
     * Tests that mandatory message which are not routable are returned to the producer
     *
     * @throws Exception
     */
    public void testReturnUnroutableMandatoryMessage() throws Exception
    {
        _bouncedMessageList.clear();
        Connection con = new AMQConnection("vm://:1", "guest", "guest", "consumer1", "/test");


        AMQSession consumerSession = (AMQSession) con.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        AMQHeadersExchange queue = new AMQHeadersExchange(new AMQBindingURL(ExchangeDefaults.HEADERS_EXCHANGE_CLASS + "://" + ExchangeDefaults.HEADERS_EXCHANGE_NAME + "/test/queue1?" + BindingURL.OPTION_ROUTING_KEY + "='F0000=1'"));
        FieldTable ft = new PropertyFieldTable();
        ft.setString("F1000", "1");
        MessageConsumer consumer = consumerSession.createConsumer(queue, AMQSession.DEFAULT_PREFETCH_LOW_MARK, AMQSession.DEFAULT_PREFETCH_HIGH_MARK, false, false, (String) null, ft);

        //force synch to ensure the consumer has resulted in a bound queue
        ((AMQSession) consumerSession).declareExchangeSynch(ExchangeDefaults.HEADERS_EXCHANGE_NAME, ExchangeDefaults.HEADERS_EXCHANGE_CLASS);

        Connection con2 = new AMQConnection("vm://:1", "guest", "guest", "producer1", "/test");

        con2.setExceptionListener(this);
        AMQSession producerSession = (AMQSession) con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        // Need to start the "producer" connection in order to receive bounced messages
        _logger.info("Starting producer connection");
        con2.start();


        MessageProducer nonMandatoryProducer = producerSession.createProducer(queue, false, false);
        MessageProducer mandatoryProducer = producerSession.createProducer(queue);

        // First test - should neither be bounced nor routed
        _logger.info("Sending non-routable non-mandatory message");
        TextMessage msg1 = producerSession.createTextMessage("msg1");
        nonMandatoryProducer.send(msg1);

        // Second test - should be bounced
        _logger.info("Sending non-routable mandatory message");
        TextMessage msg2 = producerSession.createTextMessage("msg2");
        mandatoryProducer.send(msg2);

        // Third test - should be routed
        _logger.info("Sending routable message");
        TextMessage msg3 = producerSession.createTextMessage("msg3");
        msg3.setStringProperty("F1000", "1");
        mandatoryProducer.send(msg3);


        _logger.info("Starting consumer connection");
        con.start();
        TextMessage tm = (TextMessage) consumer.receive(1000L);

        assertTrue("No message routed to receiver", tm != null);
        assertTrue("Wrong message routed to receiver: " + tm.getText(), "msg3".equals(tm.getText()));

        try
        {
            Thread.sleep(1000L);
        }
        catch (InterruptedException e)
        {
            ;
        }

        assertTrue("Wrong number of messages bounced (expect 1): " + _bouncedMessageList.size(), _bouncedMessageList.size() == 1);
        Message m = _bouncedMessageList.get(0);
        assertTrue("Wrong message bounced: " + m.toString(), m.toString().contains("msg2"));


        con.close();
        con2.close();


    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(ReturnUnroutableMandatoryMessageTest.class);
    }

    public void onException(JMSException jmsException)
    {

        Exception linkedException = jmsException.getLinkedException();
        if (linkedException instanceof AMQNoRouteException)
        {
            AMQNoRouteException noRoute = (AMQNoRouteException) linkedException;
            Message bounced = (Message) noRoute.getUndeliveredMessage();
            _bouncedMessageList.add(bounced);
            _logger.info("Caught expected NoRouteException");
        }
        else
        {
            _logger.warn("Caught exception on producer: ", jmsException);
        }
    }
}
