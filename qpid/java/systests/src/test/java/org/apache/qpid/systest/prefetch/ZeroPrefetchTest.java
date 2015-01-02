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
package org.apache.qpid.systest.prefetch;

import java.util.UUID;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class ZeroPrefetchTest extends QpidBrokerTestCase
{

    private static final String TEST_PROPERTY_NAME = "testProp";

    // send two messages to the queue, consume and acknowledge one message on connection 1
    // create a second connection and attempt to consume the second message - this will only be possible
    // if the first connection has no prefetch
    public void testZeroPrefetch() throws Exception
    {
        setTestClientSystemProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, "0");
        Connection prefetch1Connection = getConnection();

        prefetch1Connection.start();

        final Session prefetch1session = prefetch1Connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = prefetch1session.createQueue(getTestQueueName());
        MessageConsumer prefetch1consumer = prefetch1session.createConsumer(queue);


        Session producerSession = prefetch1Connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);
        Message firstMessage = producerSession.createMessage();
        String firstPropertyValue = UUID.randomUUID().toString();
        firstMessage.setStringProperty(TEST_PROPERTY_NAME, firstPropertyValue);
        producer.send(firstMessage);

        Message secondMessage = producerSession.createMessage();
        String secondPropertyValue = UUID.randomUUID().toString();
        secondMessage.setStringProperty(TEST_PROPERTY_NAME, secondPropertyValue);
        producer.send(secondMessage);


        Message receivedMessage = prefetch1consumer.receive(2000l);
        assertNotNull("First message was not received", receivedMessage);
        assertEquals("Message property was not as expected", firstPropertyValue, receivedMessage.getStringProperty(TEST_PROPERTY_NAME));

        Connection prefetch2Connection = getConnection();

        prefetch2Connection.start();
        final Session prefetch2session = prefetch2Connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer prefetch2consumer = prefetch2session.createConsumer(queue);

        receivedMessage = prefetch2consumer.receive(2000l);
        assertNotNull("Second message was not received", receivedMessage);
        assertEquals("Message property was not as expected", secondPropertyValue, receivedMessage.getStringProperty(TEST_PROPERTY_NAME));

    }
}
