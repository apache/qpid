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
package org.apache.qpid.test.unit.client;

import java.io.IOException;

import org.apache.qpid.AMQException;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.url.BindingURL;

import javax.jms.Connection;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;

public class DynamicQueueExchangeCreateTest extends QpidBrokerTestCase
{
    private JMXTestUtils _jmxUtils;

    @Override
    public void setUp() throws Exception
    {
        getBrokerConfiguration().addJmxManagementConfiguration();

        _jmxUtils = new JMXTestUtils(this);

        super.setUp();
        _jmxUtils.open();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_jmxUtils != null)
            {
                _jmxUtils.close();
            }
        }
        finally
        {
            super.tearDown();
        }
    }

    /*
     * Tests to validate that setting the respective qpid.declare_queues,
     * qpid.declare_exchanges system properties functions as expected.
     */

    public void testQueueNotDeclaredDuringConsumerCreation() throws Exception
    {
        setSystemProperty(ClientProperties.QPID_DECLARE_QUEUES_PROP_NAME, "false");

        Connection connection = getConnection();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue queue = session.createQueue(getTestQueueName());

        try
        {
            session.createConsumer(queue);
            fail("JMSException should be thrown as the queue does not exist");
        }
        catch (JMSException e)
        {
            checkExceptionErrorCode(e, AMQConstant.NOT_FOUND);
        }
    }

    public void testExchangeNotDeclaredDuringConsumerCreation() throws Exception
    {
        setSystemProperty(ClientProperties.QPID_DECLARE_EXCHANGES_PROP_NAME, "false");

        Connection connection = getConnection();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        String exchangeName = getTestQueueName();
        Queue queue = session.createQueue("direct://" + exchangeName + "/queue/queue");

        try
        {
            session.createConsumer(queue);
            fail("JMSException should be thrown as the exchange does not exist");
        }
        catch (JMSException e)
        {
            checkExceptionErrorCode(e, AMQConstant.NOT_FOUND);
        }

        //verify the exchange was not declared
        String exchangeObjectName = _jmxUtils.getExchangeObjectName("test", exchangeName);
        assertFalse("exchange should not exist", _jmxUtils.doesManagedObjectExist(exchangeObjectName));
    }

    /**
     * Checks that setting {@value ClientProperties#QPID_DECLARE_EXCHANGES_PROP_NAME} false results in
     * disabling implicit ExchangeDeclares during producer creation when using a {@link BindingURL}
     */
    public void testExchangeNotDeclaredDuringProducerCreation() throws Exception
    {
        Connection connection = getConnection();
        Session session1 = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        String exchangeName1 = getTestQueueName() + "1";


        Queue queue = session1.createQueue("direct://" + exchangeName1 + "/queue/queue");
        session1.createProducer(queue);

        //close the session to ensure any previous commands were fully processed by
        //the broker before observing their effect
        session1.close();

        //verify the exchange was declared
        String exchangeObjectName = _jmxUtils.getExchangeObjectName("test", exchangeName1);
        assertTrue("exchange should exist", _jmxUtils.doesManagedObjectExist(exchangeObjectName));

        //Now disable the implicit exchange declares and try again
        setSystemProperty(ClientProperties.QPID_DECLARE_EXCHANGES_PROP_NAME, "false");

        Session session2 = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        String exchangeName2 = getTestQueueName() + "2";

        Queue queue2 = session2.createQueue("direct://" + exchangeName2 + "/queue/queue");
        session2.createProducer(queue2);

        //close the session to ensure any previous commands were fully processed by
        //the broker before observing their effect
        session2.close();

        //verify the exchange was not declared
        String exchangeObjectName2 = _jmxUtils.getExchangeObjectName("test", exchangeName2);
        assertFalse("exchange should not exist", _jmxUtils.doesManagedObjectExist(exchangeObjectName2));
    }

    public void testQueueNotBoundDuringConsumerCreation() throws Exception
    {
        setSystemProperty(ClientProperties.QPID_BIND_QUEUES_PROP_NAME, "false");
        setSystemProperty(ClientProperties.VERIFY_QUEUE_ON_SEND, "true");

        Connection connection = getConnection();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue queue = session.createQueue(getTestQueueName());
        session.createConsumer(queue);

        try
        {
            session.createProducer(queue).send(session.createMessage());
            fail("JMSException should be thrown as the queue does not exist");
        }
        catch (InvalidDestinationException ide)
        {
            //PASS
        }
    }
    private void checkExceptionErrorCode(JMSException original, AMQConstant code)
    {
        Exception linked = original.getLinkedException();
        assertNotNull("Linked exception should have been set", linked);
        assertTrue("Linked exception should be an AMQException", linked instanceof AMQException);
        assertEquals("Error code should be " + code.getCode(), code, ((AMQException) linked).getErrorCode());
    }

    /*
     * Tests to validate that the custom exchanges declared by the client during
     * consumer and producer creation have the expected properties.
     */

    public void testPropertiesOfCustomExchangeDeclaredDuringProducerCreation() throws Exception
    {
        implTestPropertiesOfCustomExchange(true, false);
    }

    public void testPropertiesOfCustomExchangeDeclaredDuringConsumerCreation() throws Exception
    {
        implTestPropertiesOfCustomExchange(false, true);
    }

    private void implTestPropertiesOfCustomExchange(boolean createProducer, boolean createConsumer) throws Exception
    {
        Connection connection = getConnection();

        Session session1 = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        String exchangeName1 = getTestQueueName() + "1";
        String queueName1 = getTestQueueName() + "1";

        Queue queue = session1.createQueue("direct://" + exchangeName1 + "/" + queueName1 + "/" + queueName1 + "?" + BindingURL.OPTION_EXCHANGE_AUTODELETE + "='true'");
        if(createProducer)
        {
            session1.createProducer(queue);
        }

        if(createConsumer)
        {
            session1.createConsumer(queue);
        }
        session1.close();

        //verify the exchange was declared to expectation
        verifyDeclaredExchange(exchangeName1, true, false);

        Session session2 = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        String exchangeName2 = getTestQueueName() + "2";
        String queueName2 = getTestQueueName() + "2";

        Queue queue2 = session2.createQueue("direct://" + exchangeName2 + "/" + queueName2 + "/" + queueName2 + "?" + BindingURL.OPTION_EXCHANGE_DURABLE + "='true'");
        if(createProducer)
        {
            session2.createProducer(queue2);
        }

        if(createConsumer)
        {
            session2.createConsumer(queue2);
        }
        session2.close();

        //verify the exchange was declared to expectation
        verifyDeclaredExchange(exchangeName2, false, true);
    }

    private void verifyDeclaredExchange(String exchangeName, boolean isAutoDelete, boolean isDurable) throws IOException
    {
        String exchangeObjectName = _jmxUtils.getExchangeObjectName("test", exchangeName);
        assertTrue("exchange should exist", _jmxUtils.doesManagedObjectExist(exchangeObjectName));
        ManagedExchange exchange = _jmxUtils.getManagedExchange(exchangeName);
        assertEquals(isAutoDelete, exchange.isAutoDelete());
        assertEquals(isDurable,exchange.isDurable());
    }
}
