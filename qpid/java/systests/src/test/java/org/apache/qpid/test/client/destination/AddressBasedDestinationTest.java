/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.test.client.destination;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.message.QpidMessageProperties;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.transport.ExecutionErrorCode;

public class AddressBasedDestinationTest extends QpidBrokerTestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(AddressBasedDestinationTest.class);
    private Connection _connection;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _connection = getConnection() ;
        _connection.start();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            _connection.close();
        }
        catch(JMSException e)
        {
            // ignore
        }
        super.tearDown();
    }

    public void testCreateOptions() throws Exception
    {
        Session jmsSession = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        MessageProducer prod;
        MessageConsumer cons;

        // default (create never, assert never) -------------------
        // create never --------------------------------------------
        String addr1 = "ADDR:testQueue1";
        AMQDestination  dest = new AMQAnyDestination(addr1);
        try
        {
            cons = jmsSession.createConsumer(dest);
        }
        catch(JMSException e)
        {
            // pass
            _connection = getConnection();
            jmsSession = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
            dest = new AMQAnyDestination(addr1);

        }

        assertFalse("Queue should not be created",(
                (AMQSession)jmsSession).isQueueExist(dest,false));


        // create always -------------------------------------------
        addr1 = "ADDR:testQueue1; { create: always }";
        dest = new AMQAnyDestination(addr1);
        cons = jmsSession.createConsumer(dest);

        assertTrue("Queue not created as expected",(
                (AMQSession)jmsSession).isQueueExist(dest, true));
        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("",
                    dest.getAddressName(),dest.getAddressName(), null));

        // create receiver -----------------------------------------
        addr1 = "ADDR:testQueue2; { create: receiver }";
        dest = new AMQAnyDestination(addr1);
        try
        {
            prod = jmsSession.createProducer(dest);
        }
        catch(JMSException e)
        {
            String expectedMessage = "The name 'testQueue2' supplied in the address " +
                       "doesn't resolve to an exchange or a queue";
            assertTrue(e.getCause().getMessage().contains(expectedMessage)
                       || e.getCause().getCause().getMessage().contains(expectedMessage));
        }

        assertFalse("Queue should not be created",(
                (AMQSession)jmsSession).isQueueExist(dest, false));


        cons = jmsSession.createConsumer(dest);

        assertTrue("Queue not created as expected",(
                (AMQSession)jmsSession).isQueueExist(dest, true));
        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("",
                    dest.getAddressName(),dest.getAddressName(), null));

        // create never --------------------------------------------
        addr1 = "ADDR:testQueue3; { create: never }";
        dest = new AMQAnyDestination(addr1);
        String testQueue3ErrorMessage = "The name 'testQueue3' supplied in the address " +
                   "doesn't resolve to an exchange or a queue";
        try
        {
            cons = jmsSession.createConsumer(dest);
        }
        catch(JMSException e)
        {
            // pass
        }

        try
        {
            prod = jmsSession.createProducer(dest);
        }
        catch(JMSException e)
        {
            // pass
            _connection = getConnection();
            jmsSession = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
            dest = new AMQAnyDestination(addr1);
        }

        assertFalse("Queue should not be created",(
                (AMQSession)jmsSession).isQueueExist(dest, false));

        // create sender ------------------------------------------
        addr1 = "ADDR:testQueue3; { create: sender }";
        dest = new AMQAnyDestination(addr1);

        try
        {
            cons = jmsSession.createConsumer(dest);
        }
        catch(JMSException e)
        {
            // pass
            _connection = getConnection();
            jmsSession = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
            dest = new AMQAnyDestination(addr1);
        }

        assertFalse("Queue should not be created",(
                (AMQSession)jmsSession).isQueueExist(dest, false));

        prod = jmsSession.createProducer(dest);
        assertTrue("Queue not created as expected",(
                (AMQSession)jmsSession).isQueueExist(dest, true));
        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("",
                    dest.getAddressName(),dest.getAddressName(), null));

    }

    public void testCreateQueue() throws Exception
    {
        Session jmsSession = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);

        String addr = "ADDR:my-queue/hello; " +
                      "{" +
                            "create: always, " +
                            "node: " +
                            "{" +
                                 "durable: true ," +
                                 "x-declare: " +
                                 "{" +
                                     "exclusive: true," +
                                     "arguments: {" +
                                        "'qpid.alert_size': 1000," +
                                        "'qpid.alert_count': 100" +
                                     "}" +
                                  "}, " +
                                  "x-bindings: [{exchange : 'amq.direct', key : test}, " +
                                               "{exchange : 'amq.fanout'}," +
                                               "{exchange: 'amq.match', arguments: {x-match: any, dep: sales, loc: CA}}," +
                                               "{exchange : 'amq.topic', key : 'a.#'}" +
                                              "]," +

                            "}" +
                      "}";
        AMQDestination dest = new AMQAnyDestination(addr);
        MessageConsumer cons = jmsSession.createConsumer(dest);
        cons.close();

        // Even if the consumer is closed the queue and the bindings should be intact.

        assertTrue("Queue not created as expected",(
                (AMQSession)jmsSession).isQueueExist(dest, true));

        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("",
                    dest.getAddressName(),dest.getAddressName(), null));

        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("amq.direct",
                    dest.getAddressName(),"test", null));

        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("amq.fanout",
                    dest.getAddressName(),null, null));

        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("amq.topic",
                    dest.getAddressName(),"a.#", null));

        Map<String,Object> args = new HashMap<String,Object>();
        args.put("x-match","any");
        args.put("dep","sales");
        args.put("loc","CA");
        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("amq.match",
                    dest.getAddressName(),null, args));

        MessageProducer prod = jmsSession.createProducer(dest);
        prod.send(jmsSession.createTextMessage("test"));

        MessageConsumer cons2 = jmsSession.createConsumer(jmsSession.createQueue("ADDR:my-queue"));
        Message m = cons2.receive(1000);
        assertNotNull("Should receive message sent to my-queue",m);
        assertEquals("The subject set in the message is incorrect","hello",m.getStringProperty(QpidMessageProperties.QPID_SUBJECT));
    }

    public void testCreateExchange() throws Exception
    {
        createExchangeImpl(false, false, false);
    }

    /**
     * Verify creating an exchange via an Address, with supported
     * exchange-declare arguments.
     */
    public void testCreateExchangeWithArgs() throws Exception
    {
        createExchangeImpl(true, false, false);
    }

    /**
     * Verify that when creating an exchange via an Address, if a
     * nonsense argument is specified the broker throws an execution
     * exception back on the session with NOT_IMPLEMENTED status.
     */
    public void testCreateExchangeWithNonsenseArgs() throws Exception
    {
        createExchangeImpl(true, true, false);
    }

    private void createExchangeImpl(final boolean withExchangeArgs,
                                    final boolean useNonsenseArguments,
                                    final boolean useNonsenseExchangeType) throws Exception
    {
        Session jmsSession = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);

        String addr = "ADDR:my-exchange/hello; " +
                      "{ " +
                        "create: always, " +
                        "node: " +
                        "{" +
                             "type: topic, " +
                             "x-declare: " +
                             "{ " +
                                 "type:" +
                                 (useNonsenseExchangeType ? "nonsense" : "direct") +
                                 ", " +
                                 "auto-delete: true" +
                                 createExchangeArgsString(withExchangeArgs, useNonsenseArguments) +
                             "}" +
                        "}" +
                      "}";

        AMQDestination dest = new AMQAnyDestination(addr);

        MessageConsumer cons;
        try
        {
            cons = jmsSession.createConsumer(dest);
            if(useNonsenseArguments || useNonsenseExchangeType)
            {
                fail("Expected execution exception during exchange declare did not occur");
            }
        }
        catch(JMSException e)
        {
            if(useNonsenseArguments && e.getCause().getMessage().contains(ExecutionErrorCode.NOT_IMPLEMENTED.toString()))
            {
                //expected because we used an argument which the broker doesn't have functionality
                //for. We can't do the rest of the test as a result of the exception, just stop.
                return;
            }
            else if(useNonsenseExchangeType && (e.getErrorCode().equals(String.valueOf(AMQConstant.NOT_FOUND.getCode()))))
            {
                return;
            }
            else if((useNonsenseExchangeType || useNonsenseArguments) && !isBroker010()
                    && String.valueOf(AMQConstant.COMMAND_INVALID.getCode()).equals(e.getErrorCode()))
            {
                return;
            }
            else
            {
                fail("Unexpected exception whilst creating consumer: " + e);
            }
        }

        assertTrue("Exchange not created as expected",(
                (AMQSession)jmsSession).isExchangeExist(dest,true));

        // The existence of the queue is implicitly tested here
        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("my-exchange",
                    dest.getQueueName(),"hello", null));

        // The client should be able to query and verify the existence of my-exchange (QPID-2774)
        dest = new AMQAnyDestination("ADDR:my-exchange; {create: never}");
        cons = jmsSession.createConsumer(dest);
    }

    private String createExchangeArgsString(final boolean withExchangeArgs,
                                            final boolean useNonsenseArguments)
    {
        String argsString;

        if(withExchangeArgs && useNonsenseArguments)
        {
            argsString = ", arguments: {" +
            "'abcd.1234.wxyz': 1, " +
            "}";
        }
        else if(withExchangeArgs)
        {
            argsString = ", arguments: {" +
            "'qpid.msg_sequence': 1, " +
            "'qpid.ive': 1" +
            "}";
        }
        else
        {
            argsString = "";
        }

        return argsString;
    }

    public void checkQueueForBindings(Session jmsSession, AMQDestination dest,String headersBinding) throws Exception
    {
    	assertTrue("Queue not created as expected",(
                (AMQSession)jmsSession).isQueueExist(dest, true));

        assertTrue("Queue not bound as expected", (
                (AMQSession) jmsSession).isQueueBound("",
                                                      dest.getAddressName(), dest.getAddressName(), null));

        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("amq.direct",
                    dest.getAddressName(),"test", null));

        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("amq.topic",
                    dest.getAddressName(),"a.#", null));

        Address a = Address.parse(headersBinding);
        assertTrue("Queue not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("amq.match",
                    dest.getAddressName(),null, a.getOptions()));
    }

    /**
     * Test goal: Verifies that a producer and consumer creation triggers the correct
     *            behavior for x-bindings specified in node props.
     */
    public void testBindQueueWithArgs() throws Exception
    {

    	Session jmsSession = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        String headersBinding = "{exchange: 'amq.match', arguments: {x-match: any, dep: sales, loc: CA}}";

        String addr = "node: "  +
                           "{" +
                               "durable: true ," +
                               "x-declare: " +
                               "{ " +
                                     "auto-delete: true," +
                                     "arguments: {'qpid.alert_count': 100}" +
                               "}, " +
                               "x-bindings: [{exchange : 'amq.direct', key : test}, " +
                                            "{exchange : 'amq.topic', key : 'a.#'}," +
                                             headersBinding +
                                           "]" +
                           "}" +
                      "}";


        AMQDestination dest1 = new AMQAnyDestination("ADDR:my-queue/hello; {create: receiver, " +addr);
        MessageConsumer cons = jmsSession.createConsumer(dest1);
        checkQueueForBindings(jmsSession,dest1,headersBinding);

        AMQDestination dest2 = new AMQAnyDestination("ADDR:my-queue2/hello; {create: sender, " +addr);
        MessageProducer prod = jmsSession.createProducer(dest2);
        checkQueueForBindings(jmsSession,dest2,headersBinding);
    }

    /**
     * Test goal: Verifies the capacity property in address string is handled properly.
     * Test strategy:
     * Creates a destination with capacity 10.
     * Creates consumer with client ack.
     * Sends 15 messages to the queue, tries to receive 10.
     * Tries to receive the 11th message and checks if its null.
     *
     * Since capacity is 10 and we haven't acked any messages,
     * we should not have received the 11th.
     *
     * Acks the 10th message and verifies we receive the rest of the msgs.
     */
    public void testCapacity() throws Exception
    {
        verifyCapacity("ADDR:my-queue; {create: always, link:{capacity: 10}}");
    }

    public void testSourceAndTargetCapacity() throws Exception
    {
        verifyCapacity("ADDR:my-queue; {create: always, link:{capacity: {source:10, target:15} }}");
    }

    private void verifyCapacity(String address) throws Exception
    {
        if (!isCppBroker())
        {
            _logger.info("Not C++ broker, exiting test");
            return;
        }

        Session jmsSession = _connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);

        AMQDestination dest = new AMQAnyDestination(address);
        MessageConsumer cons = jmsSession.createConsumer(dest);
        MessageProducer prod = jmsSession.createProducer(dest);

        for (int i=0; i< 15; i++)
        {
            prod.send(jmsSession.createTextMessage("msg" + i) );
        }
        Message msg = null;
        for (int i=0; i< 10; i++)
        {
            msg = cons.receive(RECEIVE_TIMEOUT);
            assertNotNull("Should have received " + i + " message", msg);
            assertEquals("Unexpected message received", "msg" + i, ((TextMessage)msg).getText());
        }
        assertNull("Shouldn't have received the 11th message as capacity is 10",cons.receive(RECEIVE_TIMEOUT));
        msg.acknowledge();
        for (int i=11; i<16; i++)
        {
            assertNotNull("Should have received the " + i + "th message as we acked the last 10",cons.receive(RECEIVE_TIMEOUT));
        }
    }

    /**
     * Test goal: Verifies if the new address format based destinations
     *            can be specified and loaded correctly from the properties file.
     *
     */
    public void testLoadingFromPropertiesFile() throws Exception
    {
        Hashtable<String,String> map = new Hashtable<String,String>();
        map.put("destination.myQueue1", "ADDR:my-queue/hello; {create: always, node: " +
                "{x-declare: {auto-delete: true, arguments : {'qpid.alert_size': 1000}}}}");

        map.put("destination.myQueue2", "ADDR:my-queue2; { create: receiver }");

        map.put("destination.myQueue3", "BURL:direct://amq.direct/my-queue3?routingkey='test'");

        PropertiesFileInitialContextFactory props = new PropertiesFileInitialContextFactory();
        Context ctx = props.getInitialContext(map);

        AMQDestination dest1 = (AMQDestination)ctx.lookup("myQueue1");
        AMQDestination dest2 = (AMQDestination)ctx.lookup("myQueue2");
        AMQDestination dest3 = (AMQDestination)ctx.lookup("myQueue3");

        Session jmsSession = _connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);
        MessageConsumer cons1 = jmsSession.createConsumer(dest1);
        MessageConsumer cons2 = jmsSession.createConsumer(dest2);
        MessageConsumer cons3 = jmsSession.createConsumer(dest3);

        assertTrue("Destination1 was not created as expected",(
                (AMQSession)jmsSession).isQueueExist(dest1, true));

        assertTrue("Destination1 was not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("",
                    dest1.getAddressName(),dest1.getAddressName(), null));

        assertTrue("Destination2 was not created as expected",(
                (AMQSession)jmsSession).isQueueExist(dest2,true));

        assertTrue("Destination2 was not bound as expected",(
                (AMQSession)jmsSession).isQueueBound("",
                    dest2.getAddressName(),dest2.getAddressName(), null));

        MessageProducer producer = jmsSession.createProducer(dest3);
        producer.send(jmsSession.createTextMessage("Hello"));
        TextMessage msg = (TextMessage)cons3.receive(1000);
        assertEquals("Destination3 was not created as expected.",msg.getText(),"Hello");
    }

    /**
     * Test goal: Verifies the subject can be overridden using "qpid.subject" message property.
     * Test strategy: Creates and address with a default subject "topic1"
     *                Creates a message with "qpid.subject"="topic2" and sends it.
     *                Verifies that the message goes to "topic2" instead of "topic1".
     */
    public void testOverridingSubject() throws Exception
    {
        Session jmsSession = _connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);

        AMQDestination topic1 = new AMQAnyDestination("ADDR:amq.topic/topic1; {link:{name: queue1}}");

        MessageProducer prod = jmsSession.createProducer(topic1);

        Message m = jmsSession.createTextMessage("Hello");
        m.setStringProperty("qpid.subject", "topic2");

        MessageConsumer consForTopic1 = jmsSession.createConsumer(topic1);
        MessageConsumer consForTopic2 = jmsSession.createConsumer(new AMQAnyDestination("ADDR:amq.topic/topic2; {link:{name: queue2}}"));

        prod.send(m);
        Message msg = consForTopic1.receive(1000);
        assertNull("message shouldn't have been sent to topic1",msg);

        msg = consForTopic2.receive(1000);
        assertNotNull("message should have been sent to topic2",msg);

    }

    /**
     * Test goal: Verifies that session.createQueue method
     *            works as expected both with the new and old addressing scheme.
     */
    public void testSessionCreateQueue() throws Exception
    {
        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);

        // Using the BURL method
        Destination queue = ssn.createQueue("my-queue");
        MessageProducer prod = ssn.createProducer(queue);
        MessageConsumer cons = ssn.createConsumer(queue);
        assertTrue("my-queue was not created as expected",(
                (AMQSession)ssn).isQueueBound("amq.direct",
                    "my-queue","my-queue", null));

        prod.send(ssn.createTextMessage("test"));
        assertNotNull("consumer should receive a message",cons.receive(1000));
        cons.close();

        // Using the ADDR method
        // default case
        queue = ssn.createQueue("ADDR:my-queue2 ; { assert : sender }");
        try
        {
        	prod = ssn.createProducer(queue);
        	fail("The client should throw an exception, since there is no queue present in the broker");
        }
        catch(Exception e)
        {
        	String s = "Assert failed for queue : my-queue2";
        	assertTrue(e.getCause().getMessage().contains(s) || e.getCause().getCause().getMessage().contains(s));
        }

        // explicit create case
        queue = ssn.createQueue("ADDR:my-queue2; {create: sender}");
        prod = ssn.createProducer(queue);
        cons = ssn.createConsumer(queue);
        assertTrue("my-queue2 was not created as expected",(
                (AMQSession)ssn).isQueueBound("",
                    "my-queue2","my-queue2", null));

        prod.send(ssn.createTextMessage("test"));
        assertNotNull("consumer should receive a message",cons.receive(1000));
        cons.close();

        // Using the ADDR method to create a more complicated queue
        String addr = "ADDR:amq.direct/x512; {" +
        "link : {name : 'MY.RESP.QUEUE', " +
        "x-declare : { auto-delete: true, exclusive: true, " +
                     "arguments : {'qpid.alert_size': 1000, 'qpid.policy_type': ring} } } }";
        queue = ssn.createQueue(addr);

        cons = ssn.createConsumer(queue);
        prod = ssn.createProducer(queue);
        assertTrue("MY.RESP.QUEUE was not created as expected",(
                (AMQSession)ssn).isQueueBound("amq.direct",
                    "MY.RESP.QUEUE","x512", null));
        cons.close();
    }

    /**
     * Test goal: Verifies that session.creatTopic method works as expected
     * both with the new and old addressing scheme.
     */
    public void testSessionCreateTopic() throws Exception
    {
        sessionCreateTopicImpl(false);
    }

    /**
     * Test goal: Verifies that session.creatTopic method works as expected
     * both with the new and old addressing scheme when adding exchange arguments.
     */
    public void testSessionCreateTopicWithExchangeArgs() throws Exception
    {
        sessionCreateTopicImpl(true);
    }

    private void sessionCreateTopicImpl(boolean withExchangeArgs) throws Exception
    {
        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);

        // Using the BURL method
        Topic topic = ssn.createTopic("ACME");
        MessageProducer prod = ssn.createProducer(topic);
        MessageConsumer cons = ssn.createConsumer(topic);

        prod.send(ssn.createTextMessage("test"));
        assertNotNull("consumer should receive a message",cons.receive(1000));
        cons.close();

        // Using the ADDR method
        topic = ssn.createTopic("ADDR:ACME");
        prod = ssn.createProducer(topic);
        cons = ssn.createConsumer(topic);

        prod.send(ssn.createTextMessage("test"));
        assertNotNull("consumer should receive a message",cons.receive(1000));
        cons.close();

        String addr = "ADDR:vehicles/bus; " +
        "{ " +
          "create: always, " +
          "node: " +
          "{" +
               "type: topic, " +
               "x-declare: " +
               "{ " +
                   "type:direct, " +
                   "auto-delete: true" +
                   createExchangeArgsString(withExchangeArgs, false) +
               "}" +
          "}, " +
          "link: {name : my-topic, " +
              "x-bindings: [{exchange : 'vehicles', key : car}, " +
                           "{exchange : 'vehicles', key : van}]" +
          "}" +
        "}";

        // Using the ADDR method to create a more complicated topic
        topic = ssn.createTopic(addr);
        cons = ssn.createConsumer(topic);
        prod = ssn.createProducer(topic);

        assertTrue("The queue was not bound to vehicle exchange using bus as the binding key",(
                (AMQSession)ssn).isQueueBound("vehicles",
                    "my-topic","bus", null));

        assertTrue("The queue was not bound to vehicle exchange using car as the binding key",(
                (AMQSession)ssn).isQueueBound("vehicles",
                    "my-topic","car", null));

        assertTrue("The queue was not bound to vehicle exchange using van as the binding key",(
                (AMQSession)ssn).isQueueBound("vehicles",
                    "my-topic","van", null));

        Message msg = ssn.createTextMessage("test");
        msg.setStringProperty("qpid.subject", "van");
        prod.send(msg);
        assertNotNull("consumer should receive a message",cons.receive(1000));
        cons.close();
    }

    /**
     * Test Goal : Verify the default subjects used for each exchange type.
     * The default for amq.topic is "#" and for the rest it's ""
     */
    public void testDefaultSubjects() throws Exception
    {
        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);

        MessageConsumer queueCons = ssn.createConsumer(new AMQAnyDestination("ADDR:amq.direct"));
        MessageConsumer topicCons = ssn.createConsumer(new AMQAnyDestination("ADDR:amq.topic"));

        MessageProducer queueProducer = ssn.createProducer(new AMQAnyDestination("ADDR:amq.direct"));
        MessageProducer topicProducer1 = ssn.createProducer(new AMQAnyDestination("ADDR:amq.topic/usa.weather"));
        MessageProducer topicProducer2 = ssn.createProducer(new AMQAnyDestination("ADDR:amq.topic/sales"));

        queueProducer.send(ssn.createBytesMessage());
        assertNotNull("The consumer subscribed to amq.direct " +
        		"with empty binding key should have received the message ",queueCons.receive(1000));

        topicProducer1.send(ssn.createTextMessage("25c"));
        assertEquals("The consumer subscribed to amq.topic " +
                "with '#' binding key should have received the message ",
                ((TextMessage)topicCons.receive(1000)).getText(),"25c");

        topicProducer2.send(ssn.createTextMessage("1000"));
        assertEquals("The consumer subscribed to amq.topic " +
                "with '#' binding key should have received the message ",
                ((TextMessage)topicCons.receive(1000)).getText(),"1000");
    }

    /**
     * Test Goal : Verify that 'mode : browse' works as expected using a regular consumer.
     *             This indirectly tests ring queues as well.
     */
    public void testBrowseMode() throws Exception
    {

        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);

        String addr = "ADDR:my-ring-queue; {create: always, mode: browse, " +
            "node: {x-bindings: [{exchange : 'amq.direct', key : test}], " +
                   "x-declare:{arguments : {'qpid.policy_type':ring, 'qpid.max_count':2}}}}";

        Destination dest = ssn.createQueue(addr);
        MessageConsumer browseCons = ssn.createConsumer(dest);
        MessageProducer prod = ssn.createProducer(ssn.createQueue("ADDR:amq.direct/test"));

        prod.send(ssn.createTextMessage("Test1"));
        prod.send(ssn.createTextMessage("Test2"));

        TextMessage msg = (TextMessage)browseCons.receive(1000);
        assertEquals("Didn't receive the first message",msg.getText(),"Test1");

        msg = (TextMessage)browseCons.receive(1000);
        assertEquals("Didn't receive the first message",msg.getText(),"Test2");

        browseCons.close();
        prod.send(ssn.createTextMessage("Test3"));
        browseCons = ssn.createConsumer(dest);

        msg = (TextMessage)browseCons.receive(1000);
        assertEquals("Should receive the second message again",msg.getText(),"Test2");

        msg = (TextMessage)browseCons.receive(1000);
        assertEquals("Should receive the third message since it's a ring queue",msg.getText(),"Test3");

        assertNull("Should not receive anymore messages",browseCons.receive(500));
    }

    /**
     * Test Goal : When the same destination is used when creating two consumers,
     *             If the type == topic, verify that unique subscription queues are created,
     *             unless subscription queue has a name.
     *
     *             If the type == queue, same queue should be shared.
     */
    public void testSubscriptionForSameDestination() throws Exception
    {
        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        Destination dest = ssn.createTopic("ADDR:amq.topic/foo");
        MessageConsumer consumer1 = ssn.createConsumer(dest);
        MessageConsumer consumer2 = ssn.createConsumer(dest);
        MessageProducer prod = ssn.createProducer(dest);

        prod.send(ssn.createTextMessage("A"));
        TextMessage m = (TextMessage)consumer1.receive(1000);
        assertEquals("Consumer1 should recieve message A",m.getText(),"A");
        m = (TextMessage)consumer2.receive(1000);
        assertEquals("Consumer2 should recieve message A",m.getText(),"A");

        consumer1.close();
        consumer2.close();

        dest = ssn.createTopic("ADDR:amq.topic/foo; { link: {name: my-queue}}");
        consumer1 = ssn.createConsumer(dest);
        try
        {
            consumer2 = ssn.createConsumer(dest);
            fail("An exception should be thrown as 'my-queue' already have an exclusive subscriber");
        }
        catch(Exception e)
        {
        }

    }

    public void testJMSTopicIsTreatedAsQueueIn0_10() throws Exception
    {
        _connection = getConnection() ;
        _connection.start();
        final Session ssn = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final Destination dest = ssn.createTopic("ADDR:my_queue; {create: always}");
        final MessageConsumer consumer1 = ssn.createConsumer(dest);
        final MessageConsumer consumer2 = ssn.createConsumer(dest);
        final MessageProducer prod = ssn.createProducer(dest);

        prod.send(ssn.createTextMessage("A"));
        Message m1 = consumer1.receive(1000);
        Message m2 = consumer2.receive(1000);

        if (m1 != null)
        {
            assertNull("Only one consumer should receive the message",m2);
        }
        else
        {
            assertNotNull("Only one consumer should receive the message",m2);
        }
    }

    public void testXBindingsWithoutExchangeName() throws Exception
    {
        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        String addr = "ADDR:MRKT; " +
        		"{" +
        		    "create: receiver," +
        		    "node : {type: topic, x-declare: {type: topic} },"  +
        		    "link:{" +
        		         "name: my-topic," +
        		         "x-bindings:[{key:'NYSE.#'},{key:'NASDAQ.#'},{key:'CNTL.#'}]" +
        		         "}" +
        		"}";

        // Using the ADDR method to create a more complicated topic
        Topic topic = ssn.createTopic(addr);
        MessageConsumer  cons = ssn.createConsumer(topic);

        assertTrue("The queue was not bound to MRKT exchange using NYSE.# as the binding key",(
                (AMQSession)ssn).isQueueBound("MRKT",
                    "my-topic","NYSE.#", null));

        assertTrue("The queue was not bound to MRKT exchange using NASDAQ.# as the binding key",(
                (AMQSession)ssn).isQueueBound("MRKT",
                    "my-topic","NASDAQ.#", null));

        assertTrue("The queue was not bound to MRKT exchange using CNTL.# as the binding key",(
                (AMQSession)ssn).isQueueBound("MRKT",
                    "my-topic","CNTL.#", null));

        MessageProducer prod = ssn.createProducer(topic);
        Message msg = ssn.createTextMessage("test");
        msg.setStringProperty("qpid.subject", "NASDAQ.ABCD");
        prod.send(msg);
        assertNotNull("consumer should receive a message",cons.receive(1000));
        cons.close();
    }

    public void testXSubscribeOverrides() throws Exception
    {
        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        String str = "ADDR:my_queue; {create:always, node: { type: queue }, link: {x-subscribes:{exclusive: true, arguments: {a:b,x:y}}}}";
        Destination dest = ssn.createTopic(str);
        MessageConsumer consumer1 = ssn.createConsumer(dest);
        try
        {
            MessageConsumer consumer2 = ssn.createConsumer(dest);
            fail("An exception should be thrown as 'my-queue' already have an exclusive subscriber");
        }
        catch(Exception e)
        {
        }
    }

    public void testQueueReceiversAndTopicSubscriber() throws Exception
    {
        Queue queue = new AMQAnyDestination("ADDR:my-queue; {create: always}");
        Topic topic = new AMQAnyDestination("ADDR:amq.topic/test");

        QueueSession qSession = ((AMQConnection)_connection).createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
        QueueReceiver receiver = qSession.createReceiver(queue);

        TopicSession tSession = ((AMQConnection)_connection).createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TopicSubscriber sub = tSession.createSubscriber(topic);

        Session ssn = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer prod1 = ssn.createProducer(ssn.createQueue("ADDR:my-queue"));
        prod1.send(ssn.createTextMessage("test1"));

        MessageProducer prod2 = ssn.createProducer(ssn.createTopic("ADDR:amq.topic/test"));
        prod2.send(ssn.createTextMessage("test2"));

        Message msg1 = receiver.receive();
        assertNotNull(msg1);
        assertEquals("test1",((TextMessage)msg1).getText());

        Message msg2 = sub.receive();
        assertNotNull(msg2);
        assertEquals("test2",((TextMessage)msg2).getText());
    }

    public void testDurableSubscriber() throws Exception
    {
        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);

        String bindingStr = "x-bindings:[{key:'NYSE.#'},{key:'NASDAQ.#'},{key:'CNTL.#'}]}}";

        Properties props = new Properties();
        props.setProperty("java.naming.factory.initial", "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
        props.setProperty("destination.address1", "ADDR:amq.topic/test");
        props.setProperty("destination.address2", "ADDR:amq.topic/test; {node:{" + bindingStr);
        props.setProperty("destination.address3", "ADDR:amq.topic/test; {link:{" + bindingStr);
        String addrStr = "ADDR:my_queue; {create:always,node : {type: queue}, link: {x-subscribes:{exclusive: true, arguments: {a:b,x:y}}}}";
        props.setProperty("destination.address5", addrStr);

        Context ctx = new InitialContext(props);

        for (int i=1; i < 4; i++)
        {
            Topic topic = (Topic) ctx.lookup("address"+i);
            createDurableSubscriber(ctx,ssn,"address"+i,topic,"ADDR:amq.topic/test");
        }

        Topic topic = ssn.createTopic("ADDR:news.us");
        createDurableSubscriber(ctx,ssn,"my-dest",topic,"ADDR:news.us");

        Topic namedQueue = (Topic) ctx.lookup("address5");
        try
        {
            createDurableSubscriber(ctx,ssn,"my-queue",namedQueue,"ADDR:amq.topic/test");
            fail("Exception should be thrown. Durable subscribers cannot be created for Queues");
        }
        catch(JMSException e)
        {
            assertEquals("Durable subscribers can only be created for Topics",
                    e.getMessage());
        }
    }

    public void testDurableSubscription() throws Exception
    {
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic("ADDR:amq.topic/" + getTestQueueName());
        MessageProducer publisher = session.createProducer(topic);
        MessageConsumer subscriber = session.createDurableSubscriber(topic, getTestQueueName());

        TextMessage messageToSend = session.createTextMessage("Test0");
        publisher.send(messageToSend);
        ((AMQSession<?,?>)session).sync();

        Message receivedMessage = subscriber.receive(1000);
        assertNotNull("Message has not been received", receivedMessage);
        assertEquals("Unexpected message", messageToSend.getText(), ((TextMessage)receivedMessage).getText());

        subscriber.close();

        messageToSend = session.createTextMessage("Test1");
        publisher.send(messageToSend);
        ((AMQSession<?,?>)session).sync();

        subscriber = session.createDurableSubscriber(topic, getTestQueueName());
        receivedMessage = subscriber.receive(1000);
        assertNotNull("Message has not been received", receivedMessage);
        assertEquals("Unexpected message", messageToSend.getText(), ((TextMessage)receivedMessage).getText());
    }

    public void testDurableSubscriptionnWithSelector() throws Exception
    {
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic("ADDR:amq.topic/" + getTestQueueName());
        MessageProducer publisher = session.createProducer(topic);
        MessageConsumer subscriber = session.createDurableSubscriber(topic, getTestQueueName(), "id=1", false);

        TextMessage messageToSend = session.createTextMessage("Test0");
        messageToSend.setIntProperty("id", 1);
        publisher.send(messageToSend);
        ((AMQSession<?,?>)session).sync();

        Message receivedMessage = subscriber.receive(1000);
        assertNotNull("Message has not been received", receivedMessage);
        assertEquals("Unexpected message", messageToSend.getText(), ((TextMessage)receivedMessage).getText());
        assertEquals("Unexpected id", 1, receivedMessage.getIntProperty("id"));

        subscriber.close();

        messageToSend = session.createTextMessage("Test1");
        messageToSend.setIntProperty("id", 1);
        publisher.send(messageToSend);
        ((AMQSession<?,?>)session).sync();

        subscriber = session.createDurableSubscriber(topic, getTestQueueName(), "id=1", false);
        receivedMessage = subscriber.receive(1000);
        assertNotNull("Message has not been received", receivedMessage);
        assertEquals("Unexpected message", messageToSend.getText(), ((TextMessage)receivedMessage).getText());
        assertEquals("Unexpected id", 1, receivedMessage.getIntProperty("id"));
    }

    private void createDurableSubscriber(Context ctx,Session ssn,String destName,Topic topic, String producerAddr) throws Exception
    {
        MessageConsumer cons = ssn.createDurableSubscriber(topic, destName);
        MessageProducer prod = ssn.createProducer(ssn.createTopic(producerAddr));

        Message m = ssn.createTextMessage(destName);
        prod.send(m);
        Message msg = cons.receive(1000);
        assertNotNull("Message not received as expected when using Topic : " + topic,msg);
        assertEquals(destName,((TextMessage)msg).getText());
        ssn.unsubscribe(destName);
    }

    public void testDeleteOptions() throws Exception
    {
        Session jmsSession = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        MessageConsumer cons;

        // default (create never, assert never) -------------------
        // create never --------------------------------------------
        String addr1 = "ADDR:testQueue1;{create: always, delete: always}";
        AMQDestination  dest = new AMQAnyDestination(addr1);
        try
        {
            cons = jmsSession.createConsumer(dest);
            cons.close();
        }
        catch(JMSException e)
        {
            fail("Exception should not be thrown. Exception thrown is : " + e);
        }

        assertFalse("Queue not deleted as expected",(
                (AMQSession)jmsSession).isQueueExist(dest, false));


        String addr2 = "ADDR:testQueue2;{create: always, delete: receiver}";
        dest = new AMQAnyDestination(addr2);
        try
        {
            cons = jmsSession.createConsumer(dest);
            cons.close();
        }
        catch(JMSException e)
        {
            fail("Exception should not be thrown. Exception thrown is : " + e);
        }

        assertFalse("Queue not deleted as expected",(
                (AMQSession)jmsSession).isQueueExist(dest, false));


        String addr3 = "ADDR:testQueue3;{create: always, delete: sender}";
        dest = new AMQAnyDestination(addr3);
        try
        {
            cons = jmsSession.createConsumer(dest);
            MessageProducer prod = jmsSession.createProducer(dest);
            prod.close();
        }
        catch(JMSException e)
        {
            fail("Exception should not be thrown. Exception thrown is : " + e);
        }

        assertFalse("Queue not deleted as expected",(
                (AMQSession)jmsSession).isQueueExist(dest, false));
    }

    /**
     * Test Goals : 1. Test if the client sets the correct accept mode for unreliable
     *                and at-least-once.
     *             2. Test default reliability modes for Queues and Topics.
     *             3. Test if an exception is thrown if exactly-once is used.
     *             4. Test if an exception is thrown if at-least-once is used with topics.
     *
     * Test Strategy: For goal #1 & #2
     *                For unreliable and at-least-once the test tries to receives messages
     *                in client_ack mode but does not ack the messages.
     *                It will then close the session, recreate a new session
     *                and will then try to verify the queue depth.
     *                For unreliable the messages should have been taken off the queue.
     *                For at-least-once the messages should be put back onto the queue.
     *
     */

    public void testReliabilityOptions() throws Exception
    {
        String addr1 = "ADDR:testQueue1;{create: always, delete : receiver, link : {reliability : unreliable}}";
        acceptModeTest(addr1,0);

        String addr2 = "ADDR:testQueue2;{create: always, delete : receiver, link : {reliability : at-least-once}}";
        acceptModeTest(addr2,2);

        // Default accept-mode for topics
        acceptModeTest("ADDR:amq.topic/test",0);

        // Default accept-mode for queues
        acceptModeTest("ADDR:testQueue1;{create: always}",2);

        String addr3 = "ADDR:testQueue2;{create: always, delete : receiver, link : {reliability : exactly-once}}";
        try
        {
            AMQAnyDestination dest = new AMQAnyDestination(addr3);
            fail("An exception should be thrown indicating it's an unsupported type");
        }
        catch(Exception e)
        {
            assertTrue(e.getCause().getMessage().contains("The reliability mode 'exactly-once' is not yet supported"));
        }
    }

    private void acceptModeTest(String address, int expectedQueueDepth) throws Exception
    {
        Session ssn = _connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);
        MessageConsumer cons;
        MessageProducer prod;

        AMQDestination  dest = new AMQAnyDestination(address);
        cons = ssn.createConsumer(dest);
        prod = ssn.createProducer(dest);

        for (int i=0; i < expectedQueueDepth; i++)
        {
            prod.send(ssn.createTextMessage("Msg" + i));
        }

        for (int i=0; i < expectedQueueDepth; i++)
        {
            Message msg = cons.receive(1000);
            assertNotNull(msg);
            assertEquals("Msg" + i,((TextMessage)msg).getText());
        }

        ssn.close();
        ssn = _connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);
        long queueDepth = ((AMQSession) ssn).getQueueDepth(dest);
        assertEquals(expectedQueueDepth,queueDepth);
        cons.close();
        prod.close();
    }

    public void testDestinationOnSend() throws Exception
    {
    	Session ssn = _connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);
        MessageConsumer cons = ssn.createConsumer(ssn.createTopic("ADDR:amq.topic/test"));
        MessageProducer prod = ssn.createProducer(null);

        Topic queue = ssn.createTopic("ADDR:amq.topic/test");
        prod.send(queue,ssn.createTextMessage("A"));

        Message msg = cons.receive(1000);
        assertNotNull(msg);
        assertEquals("A",((TextMessage)msg).getText());
        prod.close();
        cons.close();
    }

    public void testReplyToWithNamelessExchange() throws Exception
    {
    	System.setProperty("qpid.declare_exchanges","false");
    	replyToTest("ADDR:my-queue;{create: always}");
    	System.setProperty("qpid.declare_exchanges","true");
    }

    public void testReplyToWithCustomExchange() throws Exception
    {
    	replyToTest("ADDR:hello;{create:always,node:{type:topic}}");
    }

    private void replyToTest(String replyTo) throws Exception
    {
		Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination replyToDest = AMQDestination.createDestination(replyTo);
	    MessageConsumer replyToCons = session.createConsumer(replyToDest);

		Destination dest = session.createQueue("ADDR:amq.direct/test");

		MessageConsumer cons = session.createConsumer(dest);
		MessageProducer prod = session.createProducer(dest);
		Message m = session.createTextMessage("test");
		m.setJMSReplyTo(replyToDest);
		prod.send(m);

		Message msg = cons.receive(5000l);
		MessageProducer prodR = session.createProducer(msg.getJMSReplyTo());
		prodR.send(session.createTextMessage("x"));

		Message m1 = replyToCons.receive(5000l);
		assertNotNull("The reply to consumer should have received the messsage",m1);
    }

    public void testAltExchangeInAddressString() throws Exception
    {
        String addr1 = "ADDR:my-exchange/test; {create: always, node:{type: topic,x-declare:{alternate-exchange:'amq.fanout'}}}";
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        String altQueueAddr = "ADDR:my-alt-queue;{create: always, delete: receiver,node:{x-bindings:[{exchange:'amq.fanout'}] }}";
        MessageConsumer cons = session.createConsumer(session.createQueue(altQueueAddr));

        MessageProducer prod = session.createProducer(session.createTopic(addr1));
        prod.send(session.createMessage());
        prod.close();
        assertNotNull("The consumer on the queue bound to the alt-exchange should receive the message",cons.receive(1000));

        String addr2 = "ADDR:test-queue;{create:sender, delete: sender,node:{type:queue,x-declare:{alternate-exchange:'amq.fanout'}}}";
        prod = session.createProducer(session.createTopic(addr2));
        prod.send(session.createMessage());
        prod.close();
        assertNotNull("The consumer on the queue bound to the alt-exchange should receive the message",cons.receive(1000));
        cons.close();
    }

    public void testUnknownAltExchange() throws Exception
    {
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        String altQueueAddr = "ADDR:my-alt-queue;{create: always, delete: receiver,node:{x-bindings:[{exchange:'doesnotexist'}] }}";
        try
        {
            session.createConsumer(session.createQueue(altQueueAddr));
            fail("Attempt to create a queue with an unknown alternate exchange should fail");
        }
        catch(JMSException e)
        {
            assertEquals("Failure code is not as expected", "404", e.getErrorCode());
        }
    }

    public void testUnknownExchangeType() throws Exception
    {
        createExchangeImpl(false, false, true);
    }

    public void testQueueBrowserWithSelectorAutoAcknowledgement() throws Exception
    {
        assertQueueBrowserWithSelector(Session.AUTO_ACKNOWLEDGE);
    }

    public void testQueueBrowserWithSelectorClientAcknowldgement() throws Exception
    {
        assertQueueBrowserWithSelector(Session.CLIENT_ACKNOWLEDGE);
    }

    public void testQueueBrowserWithSelectorTransactedSession() throws Exception
    {
        assertQueueBrowserWithSelector(Session.SESSION_TRANSACTED);
    }

    public void testConsumerWithSelectorAutoAcknowledgement() throws Exception
    {
        assertConsumerWithSelector(Session.AUTO_ACKNOWLEDGE);
    }

    public void testConsumerWithSelectorClientAcknowldgement() throws Exception
    {
        assertConsumerWithSelector(Session.CLIENT_ACKNOWLEDGE);
    }

    public void testConsumerWithSelectorTransactedSession() throws Exception
    {
        assertConsumerWithSelector(Session.SESSION_TRANSACTED);
    }

    private void assertQueueBrowserWithSelector(int acknowledgement) throws Exception
    {
        String queueAddress = "ADDR:" + getTestQueueName() + ";{create: always}";

        boolean transacted = acknowledgement == Session.SESSION_TRANSACTED;
        Session session = _connection.createSession(transacted, acknowledgement);

        Queue queue = session.createQueue(queueAddress);

        final int numberOfMessages = 10;
        List<Message> sentMessages = sendMessage(session, queue, numberOfMessages);
        assertNotNull("Messages were not sent", sentMessages);
        assertEquals("Unexpected number of messages were sent", numberOfMessages, sentMessages.size());

        QueueBrowser browser = session.createBrowser(queue, INDEX + "%2=0");
        _connection.start();

        Enumeration<Message> enumaration = browser.getEnumeration();

        int counter = 0;
        int expectedIndex = 0;
        while (enumaration.hasMoreElements())
        {
            Message m = enumaration.nextElement();
            assertNotNull("Expected not null message at step " + counter, m);
            int messageIndex = m.getIntProperty(INDEX);
            assertEquals("Unexpected index", expectedIndex, messageIndex);
            expectedIndex += 2;
            counter++;
        }
        assertEquals("Unexpected number of messsages received", 5, counter);
    }

    private void assertConsumerWithSelector(int acknowledgement) throws Exception
    {
        String queueAddress = "ADDR:" + getTestQueueName() + ";{create: always}";

        boolean transacted = acknowledgement == Session.SESSION_TRANSACTED;
        Session session = _connection.createSession(transacted, acknowledgement);

        Queue queue = session.createQueue(queueAddress);

        final int numberOfMessages = 10;
        List<Message> sentMessages = sendMessage(session, queue, numberOfMessages);
        assertNotNull("Messages were not sent", sentMessages);
        assertEquals("Unexpected number of messages were sent", numberOfMessages, sentMessages.size());

        MessageConsumer consumer = session.createConsumer(queue, INDEX + "%2=0");

        int expectedIndex = 0;
        for (int i = 0; i < 5; i++)
        {
            Message m = consumer.receive(RECEIVE_TIMEOUT);
            assertNotNull("Expected not null message at step " + i, m);
            int messageIndex = m.getIntProperty(INDEX);
            assertEquals("Unexpected index", expectedIndex, messageIndex);
            expectedIndex += 2;

            if (transacted)
            {
                session.commit();
            }
            else if (acknowledgement == Session.CLIENT_ACKNOWLEDGE)
            {
                m.acknowledge();
            }
        }

        Message m = consumer.receive(RECEIVE_TIMEOUT);
        assertNull("Unexpected message received", m);
    }

    /**
     * Tests that a client using a session in {@link Session#CLIENT_ACKNOWLEDGE} can correctly
     * recover a session and re-receive the same message.
     */
    public void testTopicRereceiveAfterRecover() throws Exception
    {
        final Session jmsSession = _connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);
        final Destination topic = jmsSession.createTopic("ADDR:amq.topic/topic1; {link:{name: queue1}}");

        final MessageProducer prod = jmsSession.createProducer(topic);
        final MessageConsumer consForTopic1 = jmsSession.createConsumer(topic);
        final Message sentMessage = jmsSession.createTextMessage("Hello");

        prod.send(sentMessage);
        Message receivedMessage = consForTopic1.receive(1000);
        assertNotNull("message should be received by consumer", receivedMessage);

        jmsSession.recover();
        receivedMessage = consForTopic1.receive(1000);
        assertNotNull("message should be re-received by consumer after recover", receivedMessage);
        receivedMessage.acknowledge();
    }

    /**
    * Tests that a client using a session in {@link Session#SESSION_TRANSACTED} can correctly
    * rollback a session and re-receive the same message.
    */
    public void testTopicRereceiveAfterRollback() throws Exception
    {
        final Session jmsSession = _connection.createSession(true,Session.SESSION_TRANSACTED);
        final Destination topic = jmsSession.createTopic("ADDR:amq.topic/topic1; {link:{name: queue1}}");

        final MessageProducer prod = jmsSession.createProducer(topic);
        final MessageConsumer consForTopic1 = jmsSession.createConsumer(topic);
        final Message sentMessage = jmsSession.createTextMessage("Hello");

        prod.send(sentMessage);
        jmsSession.commit();

        Message receivedMessage = consForTopic1.receive(1000);
        assertNotNull("message should be received by consumer", receivedMessage);

        jmsSession.rollback();
        receivedMessage = consForTopic1.receive(1000);
        assertNotNull("message should be re-received by consumer after rollback", receivedMessage);
        jmsSession.commit();
    }

    /**
     * Test Goals :
     *
     * 1. Verify that link bindings are created and destroyed after creating and closing a subscriber.
     * 2. Verify that link bindings are created and destroyed after creating and closing a subscriber.
     */
    public void testLinkBindingBehavior() throws Exception
    {
        Session jmsSession = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        String addr = "ADDR:my-queue; {create: always, " +
              "link: " +
              "{" +
                   "x-bindings: [{exchange : 'amq.direct', key : test}]," +
              "}" +
        "}";

        AMQDestination dest = (AMQDestination)jmsSession.createQueue(addr);
        MessageConsumer cons = jmsSession.createConsumer(dest);
        AMQSession ssn = (AMQSession)jmsSession;

        assertTrue("Queue not created as expected",ssn.isQueueExist(dest, true));
        assertTrue("Queue not bound as expected",ssn.isQueueBound("amq.direct","my-queue","test", null));

        cons.close(); // closing consumer, link binding should be removed now.
        assertTrue("Queue should still be there",ssn.isQueueExist(dest, true));
        assertFalse("Binding should not exist anymore",ssn.isQueueBound("amq.direct","my-queue","test", null));

        MessageProducer prod = jmsSession.createProducer(dest);
        assertTrue("Queue not bound as expected",ssn.isQueueBound("amq.direct","my-queue","test", null));
        prod.close();
        assertFalse("Binding should not exist anymore",ssn.isQueueBound("amq.direct","my-queue","test", null));
    }

    /**
     * Test Goals : Verifies that the subscription queue created is as specified under link properties.
     */
    public void testCustomizingSubscriptionQueue() throws Exception
    {
        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        String xDeclareArgs = "x-declare: { exclusive: false, auto-delete: false," +
                                           "alternate-exchange: 'amq.fanout'," +
                                           "arguments: {'qpid.alert_size': 1000,'qpid.alert_count': 100}" +
                                          "}";

        String addr = "ADDR:amq.topic/test; {link: {name:my-queue, durable:true," + xDeclareArgs + "}}";
        Destination dest = ssn.createTopic(addr);
        MessageConsumer cons = ssn.createConsumer(dest);

        String verifyAddr = "ADDR:my-queue;{ node: {durable:true, " + xDeclareArgs + "}}";
        AMQDestination verifyDest = (AMQDestination)ssn.createQueue(verifyAddr);
        ((AMQSession)ssn).isQueueExist(verifyDest, true);

        // Verify that the producer does not delete the subscription queue.
        MessageProducer prod = ssn.createProducer(dest);
        prod.close();
        ((AMQSession)ssn).isQueueExist(verifyDest, true);
    }
}
