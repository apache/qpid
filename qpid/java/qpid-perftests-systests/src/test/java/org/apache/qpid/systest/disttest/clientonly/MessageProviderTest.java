/*
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
 */
package org.apache.qpid.systest.disttest.clientonly;

import java.util.HashMap;
import java.util.Map;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.disttest.client.MessageProvider;
import org.apache.qpid.disttest.client.property.PropertyValue;
import org.apache.qpid.disttest.client.property.SimplePropertyValue;
import org.apache.qpid.disttest.message.CreateMessageProviderCommand;
import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.systest.disttest.DistributedTestSystemTestBase;
import org.apache.qpid.systest.disttest.clientonly.ProducerParticipantTest.TestClientJmsDelegate;

public class MessageProviderTest extends DistributedTestSystemTestBase
{
    private MessageConsumer _consumer;
    private Session _session;
    private TestClientJmsDelegate _delegate;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _consumer = _session.createConsumer(getTestQueue());
        _delegate = new TestClientJmsDelegate(getContext());
    }

    public void testMessageSize() throws Exception
    {
        runSizeTest(0);
        runSizeTest(5);
        runSizeTest(512);
    }

    public void runSizeTest(int size) throws Exception
    {
        CreateProducerCommand command = new CreateProducerCommand();
        command.setMessageSize(size);
        MessageProvider messageProvider = new MessageProvider(null);
        Message message = messageProvider.nextMessage(_session, command);
        assertNotNull("Message is not generated", message);
        assertTrue("Wrong message type", message instanceof TextMessage);
        TextMessage textMessage = (TextMessage)message;
        String text = textMessage.getText();
        assertNotNull("Message payload is not generated", text);
        assertEquals("Message payload size is incorrect", size, text.length());
    }

    public void testCreateMessageProviderAndSendMessage() throws Exception
    {
        final CreateMessageProviderCommand messageProviderCommand = new CreateMessageProviderCommand();
        messageProviderCommand.setProviderName("test1");
        Map<String, PropertyValue> messageProperties = new HashMap<String, PropertyValue>();
        messageProperties.put("test", new SimplePropertyValue("testValue"));
        messageProperties.put("priority", new SimplePropertyValue(new Integer(9)));
        messageProviderCommand.setMessageProperties(messageProperties);
        _delegate.createMessageProvider(messageProviderCommand);

        final CreateProducerCommand producerCommand = new CreateProducerCommand();
        producerCommand.setNumberOfMessages(1);
        producerCommand.setDeliveryMode(DeliveryMode.PERSISTENT);
        producerCommand.setPriority(6);
        producerCommand.setParticipantName("test");
        producerCommand.setMessageSize(10);
        producerCommand.setSessionName("testSession");
        producerCommand.setDestinationName(getTestQueueName());
        producerCommand.setMessageProviderName(messageProviderCommand.getProviderName());

        Session session = _connection.createSession(true, Session.SESSION_TRANSACTED);
        _delegate.addConnection("name-does-not-matter", _connection);
        _delegate.addSession(producerCommand.getSessionName(), session);
        _delegate.createProducer(producerCommand);

        Message message = _delegate.sendNextMessage(producerCommand);
        session.commit();
        assertMessage(message);

        _connection.start();
        Message receivedMessage = _consumer.receive(1000l);
        assertMessage(receivedMessage);
     }

    protected void assertMessage(Message message) throws JMSException
    {
        assertNotNull("Message should not be null", message);
        assertEquals("Unexpected test property", "testValue", message.getStringProperty("test"));
        assertEquals("Unexpected priority property", 9, message.getJMSPriority());
        assertTrue("Unexpected message type", message instanceof TextMessage);
        String text = ((TextMessage)message).getText();
        assertNotNull("Message text should not be null", text);
        assertNotNull("Unexpected message size ", text.length());
    }
}
