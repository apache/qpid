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
package org.apache.qpid.systest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.NamingException;

import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.systest.rest.RestTestHelper;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.url.URLSyntaxException;

public class MessageCompressionTest extends QpidBrokerTestCase
{
    private RestTestHelper _restTestHelper = new RestTestHelper(findFreePort());

    @Override
    public void setUp() throws Exception
    {
        // do nothing - only call setup after props set
    }

    public void doActualSetUp() throws Exception
    {
        // use webadmin account to perform tests
        _restTestHelper.setUsernameAndPassword("webadmin", "webadmin");

        TestBrokerConfiguration config = getBrokerConfiguration();
        config.addHttpManagementConfiguration();
        config.setObjectAttribute(Port.class,
                                  TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT,
                                  Port.PORT,
                                  _restTestHelper.getHttpPort());

        config.setObjectAttribute(AuthenticationProvider.class, TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER,
                                  "secureOnlyMechanisms",
                                  "{}");

        // set password authentication provider on http port for the tests
        config.setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.AUTHENTICATION_PROVIDER,
                                  TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
        config.setObjectAttribute(Plugin.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);

        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            _restTestHelper.tearDown();
        }
    }

    public void testSenderCompressesReceiverUncompresses() throws Exception
    {
        doTestCompression(true, true, true);
    }

    public void testSenderCompressesOnly() throws Exception
    {
        doTestCompression(true, false, true);

    }

    public void testReceiverUncompressesOnly() throws Exception
    {
        doTestCompression(false, true, true);

    }

    public void testNoCompression() throws Exception
    {
        doTestCompression(false, false, true);

    }


    public void testDisablingCompressionAtBroker() throws Exception
    {
        doTestCompression(true, true, false);
    }


    private void doTestCompression(final boolean senderCompresses,
                                   final boolean receiverUncompresses,
                                   final boolean brokerCompressionEnabled) throws Exception
    {

        setTestSystemProperty(Broker.BROKER_MESSAGE_COMPRESSION_ENABLED, String.valueOf(brokerCompressionEnabled));

        doActualSetUp();

        String messageText = createMessageText();
        Connection senderConnection = getConnection(senderCompresses);
        String virtualPath = getConnectionFactory().getVirtualPath();
        String testQueueName = getTestQueueName();

        // create the queue using REST and bind it
        assertEquals(201,
                     _restTestHelper.submitRequest("/api/latest/queue"
                                                   + virtualPath
                                                   + virtualPath
                                                   + "/"
                                                   + testQueueName, "PUT", Collections.<String, Object>emptyMap()));
        assertEquals(201,
                     _restTestHelper.submitRequest("/api/latest/binding"
                                                   + virtualPath
                                                   + virtualPath
                                                   + "/amq.direct/"
                                                   + testQueueName
                                                   + "/"
                                                   + testQueueName, "PUT", Collections.<String, Object>emptyMap()));

        Session session = senderConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // send a large message
        MessageProducer producer = session.createProducer(getTestQueue());
        TextMessage sentMessage = session.createTextMessage(messageText);
        sentMessage.setStringProperty("bar", "foo");

        producer.send(sentMessage);
        ((AMQSession)session).sync();

        // get the number of bytes received at the broker on the connection
        List<Map<String, Object>> connectionRestOutput = _restTestHelper.getJsonAsList("/api/latest/connection");
        assertEquals(1, connectionRestOutput.size());
        Map statistics = (Map) connectionRestOutput.get(0).get("statistics");
        int bytesIn = (Integer) statistics.get("bytesIn");

        // if sending compressed then the bytesIn statistic for the connection should reflect the compressed size of the
        // message
        if(senderCompresses && brokerCompressionEnabled)
        {
            assertTrue("Message was not sent compressed", bytesIn < messageText.length());
        }
        else
        {
            assertFalse("Message was incorrectly sent compressed", bytesIn < messageText.length());
        }
        senderConnection.close();

        // receive the message
        Connection consumerConnection = getConnection(receiverUncompresses);
        session = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(getTestQueue());
        consumerConnection.start();

        TextMessage message = (TextMessage) consumer.receive(500l);
        assertNotNull("Message was not received", message);
        assertEquals("Message was corrupted", messageText, message.getText());
        assertEquals("Header was corrupted", "foo", message.getStringProperty("bar"));

        // get the number of bytes sent by the broker
        connectionRestOutput = _restTestHelper.getJsonAsList("/api/latest/connection");
        assertEquals(1, connectionRestOutput.size());
        statistics = (Map) connectionRestOutput.get(0).get("statistics");
        int bytesOut = (Integer) statistics.get("bytesOut");

        // if receiving compressed the bytes out statistic from the connection should reflect the compressed size of the
        // message
        if(receiverUncompresses && brokerCompressionEnabled)
        {
            assertTrue("Message was not received compressed", bytesOut < messageText.length());
        }
        else
        {
            assertFalse("Message was incorrectly received compressed", bytesOut < messageText.length());
        }

        consumerConnection.close();
    }

    private String createMessageText()
    {
        StringBuilder stringBuilder = new StringBuilder();
        while(stringBuilder.length() < 2048*1024)
        {
            stringBuilder.append("This should compress easily. ");
        }
        return stringBuilder.toString();
    }

    private Connection getConnection(final boolean compress) throws URLSyntaxException, NamingException, JMSException
    {
        AMQConnectionURL url = new AMQConnectionURL(getConnectionFactory().getConnectionURLString());

        url.setOption(ConnectionURL.OPTIONS_COMPRESS_MESSAGES,String.valueOf(compress));
        url = new AMQConnectionURL(url.toString());
        url.setUsername(GUEST_USERNAME);
        url.setPassword(GUEST_PASSWORD);
        url.setOption(ConnectionURL.OPTIONS_COMPRESS_MESSAGES,String.valueOf(compress));
        return getConnection(url);
    }

}
