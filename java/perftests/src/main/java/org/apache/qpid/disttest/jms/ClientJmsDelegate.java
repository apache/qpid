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
 *
 */
package org.apache.qpid.disttest.jms;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ConnectionMetaData;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.qpid.disttest.DistributedTestConstants;
import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.client.Client;
import org.apache.qpid.disttest.client.ConnectionLostListener;
import org.apache.qpid.disttest.client.MessageProvider;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CreateConnectionCommand;
import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.disttest.message.CreateMessageProviderCommand;
import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.disttest.message.CreateSessionCommand;
import org.apache.qpid.disttest.message.RegisterClientCommand;
import org.apache.qpid.disttest.message.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientJmsDelegate
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientJmsDelegate.class);

    private final Context _context;
    private final Destination _controllerQueue;
    private final Connection _controllerConnection;
    private final Session _instructionListenerSession;
    private final Session _controllerSession;
    private final MessageProducer _controlQueueProducer;

    private final String _clientName;
    private Queue _instructionQueue;

    private Map<String, Connection> _testConnections;
    private Map<String, Session> _testSessions;
    private Map<String, MessageProducer> _testProducers;
    private Map<String, MessageConsumer> _testConsumers;
    private Map<String, Session> _testSubscriptions;
    private Map<String, MessageProvider> _testMessageProviders;

    private final MessageProvider _defaultMessageProvider;

    public ClientJmsDelegate(final Context context)
    {
        try
        {
            _context = context;
            final ConnectionFactory connectionFactory = (ConnectionFactory) _context.lookup("connectionfactory");
            _controllerConnection = connectionFactory.createConnection();
            _controllerConnection.start();
            _controllerQueue = (Destination) context.lookup(DistributedTestConstants.CONTROLLER_QUEUE_JNDI_NAME);
            _instructionListenerSession = _controllerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            _controllerSession = _controllerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            _controlQueueProducer = _controllerSession.createProducer(_controllerQueue);
            _clientName = UUID.randomUUID().toString();
            _testConnections = new HashMap<String, Connection>();
            _testSessions = new HashMap<String, Session>();
            _testProducers = new HashMap<String, MessageProducer>();
            _testConsumers = new HashMap<String, MessageConsumer>();
            _testSubscriptions = new HashMap<String, Session>();
            _testMessageProviders = new HashMap<String, MessageProvider>();
            _defaultMessageProvider = new MessageProvider(null);
        }
        catch (final NamingException ne)
        {
            throw new DistributedTestException("Unable to create client jms delegate", ne);
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to create client jms delegate", jmse);
        }
    }

    public void setInstructionListener(final Client client)
    {
        try
        {
            _instructionQueue = _instructionListenerSession.createTemporaryQueue();
            final MessageConsumer instructionConsumer = _instructionListenerSession.createConsumer(_instructionQueue);
            instructionConsumer.setMessageListener(new MessageListener()
            {
                @Override
                public void onMessage(final Message message)
                {
                    client.processInstruction(JmsMessageAdaptor.messageToCommand(message));
                }
            });
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to setup instruction listener", jmse);
        }
    }

    public void sendRegistrationMessage()
    {
        Command command;
        try
        {
            command = new RegisterClientCommand(_clientName, _instructionQueue.getQueueName());
        }
        catch (final JMSException e)
        {
            throw new DistributedTestException(e);
        }
        sendCommand(command);
    }

    public void sendResponseMessage(final Response responseMessage)
    {
        sendCommand(responseMessage);
    }

    private void sendCommand(final Command command)
    {
        try
        {
            final Message message = JmsMessageAdaptor.commandToMessage(_controllerSession, command);
            _controlQueueProducer.send(message);
            LOGGER.debug("Sent message for " + command.getType() + ". message id: " + message.getJMSMessageID());
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to send command: " + command, jmse);
        }
    }

    public void createConnection(final CreateConnectionCommand command)
    {
        try
        {
            final ConnectionFactory connectionFactory = (ConnectionFactory) _context.lookup(command
                            .getConnectionFactoryName());
            final Connection newConnection = connectionFactory.createConnection();
            addConnection(command.getConnectionName(), newConnection);
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Connection " + command.getConnectionName() + " is created " + metaDataToString(newConnection.getMetaData()));
            }
        }
        catch (final NamingException ne)
        {
            throw new DistributedTestException("Unable to lookup factoryName: " + command.getConnectionFactoryName(),
                            ne);
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to create connection: " + command.getConnectionName()
                            + " (using factory name: " + command.getConnectionFactoryName() + ")", jmse);
        }
    }

    private String metaDataToString(ConnectionMetaData metaData) throws JMSException
    {
        StringBuilder sb = new StringBuilder("ConnectionMetaData[");
        sb.append(" JMSProviderName : " + metaData.getJMSProviderName());
        sb.append(" JMSVersion : " + metaData.getJMSVersion() + " (" + metaData.getJMSMajorVersion() + "." + metaData.getJMSMinorVersion() +")");
        sb.append(" ProviderVersion : " + metaData.getProviderVersion()+ " (" + metaData.getProviderMajorVersion()+ "." + metaData.getProviderMinorVersion() +")" );
        sb.append(" JMSXPropertyNames : [");
        Enumeration en = metaData.getJMSXPropertyNames();
        while(en.hasMoreElements())
        {
            sb.append(" ").append(en.nextElement());
            if( en.hasMoreElements())
            {
                sb.append(",");
            }
        }
        sb.append("]]");
        return sb.toString();
    }

    public void createSession(final CreateSessionCommand command)
    {
        try
        {
            final Connection connection = _testConnections.get(command.getConnectionName());
            if (connection == null)
            {
                throw new DistributedTestException("No test connection found called: " + command.getConnectionName(),
                                command);
            }
            final boolean transacted = command.getAcknowledgeMode() == Session.SESSION_TRANSACTED;

            final Session newSession = connection.createSession(transacted, command.getAcknowledgeMode());
            LOGGER.debug("Created session " + command.getSessionName() + " with transacted = " + newSession.getTransacted() + " and acknowledgeMode = " + newSession.getAcknowledgeMode());

            addSession(command.getSessionName(), newSession);
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to create new session: " + command, jmse);
        }
    }

    public void createProducer(final CreateProducerCommand command)
    {
        try
        {
            final Session session = _testSessions.get(command.getSessionName());
            if (session == null)
            {
                throw new DistributedTestException("No test session found called: " + command.getSessionName(), command);
            }

            synchronized(session)
            {
                final Destination destination;
                if(command.isTopic())
                {
                    destination = session.createTopic(command.getDestinationName());
                }
                else
                {
                    destination = session.createQueue(command.getDestinationName());
                }

                final MessageProducer jmsProducer = session.createProducer(destination);

                if (command.getPriority() != -1)
                {
                    jmsProducer.setPriority(command.getPriority());
                }
                if (command.getTimeToLive() > 0)
                {
                    jmsProducer.setTimeToLive(command.getTimeToLive());
                }

                if (command.getDeliveryMode() == DeliveryMode.NON_PERSISTENT
                        || command.getDeliveryMode() == DeliveryMode.PERSISTENT)
                {
                    jmsProducer.setDeliveryMode(command.getDeliveryMode());
                }

                addProducer(command.getParticipantName(), jmsProducer);
            }
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to create new producer: " + command, jmse);
        }
    }

    public void createConsumer(final CreateConsumerCommand command)
    {
        try
        {
            final Session session = _testSessions.get(command.getSessionName());
            if (session == null)
            {
                throw new DistributedTestException("No test session found called: " + command.getSessionName(), command);
            }

            synchronized(session)
            {
                Destination destination;
                MessageConsumer jmsConsumer;
                if(command.isTopic())
                {
                    Topic topic = session.createTopic(command.getDestinationName());
                    if(command.isDurableSubscription())
                    {
                        String subscription = "subscription-" + command.getParticipantName() + System.currentTimeMillis();
                        jmsConsumer = session.createDurableSubscriber(topic, subscription);

                        _testSubscriptions.put(subscription, session);
                        LOGGER.debug("created durable subscription " + subscription + " to topic " + topic);
                    }
                    else
                    {
                        jmsConsumer = session.createConsumer(topic, command.getSelector());
                    }

                    destination = topic;
                }
                else
                {
                    destination = session.createQueue(command.getDestinationName());
                    jmsConsumer = session.createConsumer(destination, command.getSelector());
                }

                _testConsumers.put(command.getParticipantName(), jmsConsumer);
            }
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to create new consumer: " + command, jmse);
        }
    }

    /**
     * destroy the client. Don't call from the Dispatcher thread.
     */
    public void destroy()
    {
        try
        {
            // Stopping the connection allows in-flight onMessage calls to
            // finish.
            _controllerConnection.stop();

            if (_instructionListenerSession != null)
            {
                _instructionListenerSession.close();
            }
            if (_controllerSession != null)
            {
                _controllerSession.close();
            }
            _controllerConnection.close();

        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to destroy cleanly", jmse);
        }
    }

    public Destination getControllerQueue()
    {
        return _controllerQueue;
    }

    public String getClientName()
    {
        return _clientName;
    }

    public int getNoOfTestConnections()
    {
        return _testConnections.size();
    }

    public int getNoOfTestSessions()
    {
        return _testSessions.size();
    }

    public int getNoOfTestProducers()
    {
        return _testProducers.size();
    }

    public int getNoOfTestConsumers()
    {
        return _testConsumers.size();
    }

    public void startConnections()
    {
        // start connections for consumers
        // it would be better if we could track consumer connections and start
        // only those
        if (!_testConsumers.isEmpty())
        {
            for (final Map.Entry<String, Connection> entry : _testConnections.entrySet())
            {
                final Connection connection = entry.getValue();
                try
                {
                    connection.start();
                }
                catch (final JMSException e)
                {
                    throw new DistributedTestException("Failed to start connection '" + entry.getKey() + "' :"
                                    + e.getLocalizedMessage());
                }
            }
        }
    }

    public int getAcknowledgeMode(final String sessionName)
    {
        try
        {
            final Session session = _testSessions.get(sessionName);
            return session.getAcknowledgeMode();
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to determine acknowledgement mode for session: " +
                            sessionName, jmse);
        }
    }

    public Message sendNextMessage(final CreateProducerCommand command)
    {
        Message sentMessage = null;
        MessageProvider messageProvider = _testMessageProviders.get(command.getMessageProviderName());
        if (messageProvider == null)
        {
            messageProvider = _defaultMessageProvider;
        }

        final Session session = _testSessions.get(command.getSessionName());
        final MessageProducer producer = _testProducers.get(command.getParticipantName());
        try
        {
            sentMessage = messageProvider.nextMessage(session, command);
            int deliveryMode = producer.getDeliveryMode();
            int priority = producer.getPriority();
            long ttl = producer.getTimeToLive();
            if (messageProvider.isPropertySet(MessageProvider.PRIORITY))
            {
                priority = sentMessage.getJMSPriority();
            }
            if (messageProvider.isPropertySet(MessageProvider.DELIVERY_MODE))
            {
                deliveryMode = sentMessage.getJMSDeliveryMode();
            }
            if (messageProvider.isPropertySet(MessageProvider.TTL))
            {
                ttl = sentMessage.getLongProperty(MessageProvider.TTL);
            }
            producer.send(sentMessage, deliveryMode, priority, ttl);
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to create and send message with producer: " +
                            command.getParticipantName() + " on session: " + command.getSessionName(), jmse);
        }
        return sentMessage;
    }

    protected void addSession(final String sessionName, final Session newSession)
    {
        _testSessions.put(sessionName, newSession);
    }

    protected void addConnection(final String connectionName, final Connection newConnection)
    {
        _testConnections.put(connectionName, newConnection);
    }

    protected void addProducer(final String producerName, final MessageProducer jmsProducer)
    {
        _testProducers.put(producerName, jmsProducer);
    }

    public Message consumeMessage(String consumerName, long receiveInterval)
    {
        Message consumedMessage = null;
        MessageConsumer consumer = _testConsumers.get(consumerName);
        try
        {
            consumedMessage = consumer.receive(receiveInterval);
        }
        catch (JMSException e)
        {
            throw new DistributedTestException("Unable to consume message with consumer: " + consumerName, e);
        }
        return consumedMessage;
    }

    public void registerListener(String consumerName, MessageListener messageListener)
    {
        MessageConsumer consumer = _testConsumers.get(consumerName);
        try
        {
            consumer.setMessageListener(messageListener);
        }
        catch (JMSException e)
        {
            throw new DistributedTestException("Unable to register message listener with consumer: " + consumerName, e);
        }
    }

    public void commitOrAcknowledgeMessageIfNecessary(final String sessionName, final Message message)
    {
        try
        {
            final Session session = _testSessions.get(sessionName);
            if (session.getTransacted())
            {
                synchronized(session)
                {
                    session.commit();
                }
            }
            else if (message != null && session.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE)
            {
                message.acknowledge();
            }
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to commit or acknowledge message on session: " +
                            sessionName, jmse);
        }
    }

    public void commitIfNecessary(final String sessionName)
    {
        commitOrAcknowledgeMessageIfNecessary(sessionName, null);
    }

    public void rollbackOrRecoverIfNecessary(String sessionName)
    {
        try
        {
            final Session session = _testSessions.get(sessionName);
            synchronized(session)
            {
                if (session.getTransacted())
                {
                    session.rollback();
                }
                else if (session.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE)
                {
                    session.recover();
                }
            }
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to rollback or recover on session: " +
                            sessionName, jmse);
        }
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("clientName", _clientName).toString();
    }

    public void tearDownTest()
    {
        StringBuilder jmsErrorMessages = new StringBuilder();
        int failureCounter = 0;

        for(String subscription : _testSubscriptions.keySet())
        {
            Session session = _testSubscriptions.get(subscription);
            try
            {
                session.unsubscribe(subscription);
            }
            catch (JMSException e)
            {
                LOGGER.error("Failed to unsubscribe '" + subscription + "' :" + e.getLocalizedMessage(), e);
                failureCounter++;
                appendErrorMessage(jmsErrorMessages, e);
            }
        }

        for (Map.Entry<String, Connection> entry : _testConnections.entrySet())
        {
            Connection connection = entry.getValue();
            try
            {
                connection.close();
            }
            catch (JMSException e)
            {
                LOGGER.error("Failed to close connection '" + entry.getKey() + "' :" + e.getLocalizedMessage(), e);
                failureCounter++;
                appendErrorMessage(jmsErrorMessages, e);
            }
        }

        _testConnections.clear();
        _testSubscriptions.clear();
        _testSessions.clear();
        _testProducers.clear();
        _testConsumers.clear();

        if (failureCounter > 0)
        {
            throw new DistributedTestException("Tear down test encountered " + failureCounter + " failures with the following errors: " + jmsErrorMessages.toString());
        }
    }

    private void appendErrorMessage(StringBuilder errorMessages, JMSException e)
    {
        if (errorMessages.length() > 0)
        {
            errorMessages.append('\n');
        }
        errorMessages.append(e.getMessage());
    }

    public void closeTestConsumer(String consumerName)
    {
        MessageConsumer consumer = _testConsumers.get(consumerName);
        if (consumer != null)
        {
            try
            {
                consumer.close();
                LOGGER.debug("Closed test consumer " + consumerName);
            }
            catch (JMSException e)
            {
                throw new DistributedTestException("Failed to close consumer: " + consumerName, e);
            }
        }
    }

    public void closeTestProducer(String producerName)
    {
        MessageProducer producer = _testProducers.get(producerName);
        if (producer != null)
        {
            try
            {
                producer.close();
            }
            catch (JMSException e)
            {
                throw new DistributedTestException("Failed to close producer: " + producerName, e);
            }
        }
    }

    /** only supports text messages - returns 0 for other message types */
    public int calculatePayloadSizeFrom(Message message)
    {
        try
        {
            if (message != null && message instanceof TextMessage)
            {
                return ((TextMessage) message).getText().getBytes().length;
            }

            return 0;
        }
        catch (JMSException e)
        {
            throw new DistributedTestException("Unable to determine the payload size for message " + message, e);
        }
    }

    public void createMessageProvider(CreateMessageProviderCommand command)
    {
        _testMessageProviders.put(command.getProviderName(), new MessageProvider(command.getMessageProperties()));
    }

    public void setConnectionLostListener(final ConnectionLostListener connectionLostListener)
    {
        try
        {
            _controllerConnection.setExceptionListener(new ExceptionListener()
                {
                @Override
                public void onException(final JMSException exception)
                {
                    LOGGER.warn("Caught ", exception);

                    if (connectionLostListener != null)
                    {
                        try
                        {
                            _controllerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE).close();
                        }
                        catch (JMSException e)
                        {
                            LOGGER.warn("Unable to create/close a new session, assuming the connection is lost ", exception);

                            connectionLostListener.connectionLost();
                        }
                    }

                }
            });
        }
        catch (JMSException e)
        {
            // ignore
        }
    }
}
