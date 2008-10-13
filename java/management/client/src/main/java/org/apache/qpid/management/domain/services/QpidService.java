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
package org.apache.qpid.management.domain.services;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.qpid.QpidException;
import org.apache.qpid.management.Constants;
import org.apache.qpid.management.Names;
import org.apache.qpid.management.configuration.Configuration;
import org.apache.qpid.management.configuration.QpidDatasource;
import org.apache.qpid.nclient.util.MessageListener;
import org.apache.qpid.nclient.util.MessagePartListenerAdapter;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageCreditUnit;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.SessionListener;
import org.apache.qpid.transport.util.Logger;

/**
 * Qpid Broker facade.
 *
 * @author Andrea Gazzarini
 */
public class QpidService implements SessionListener
{
    private final static Logger LOGGER = Logger.get(QpidService.class);

    // Inner static class used for logging and avoid conditional logic (isDebugEnabled()) duplication.
    private static class Log
    {
        /**
         * Logs the content f the message.
         * This will be written on log only if DEBUG level is enabled.
         *
         * @param messageContent the raw content of the message.
         */
        static void logMessageContent(byte [] messageContent)
        {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "<QMAN-200001> : Message has been sent to management exchange. Message content : %s",
                        Arrays.toString(messageContent));
            }
        }

        /**
         * Logs the content f the message.
         * This will be written on log only if DEBUG level is enabled.
         *
         * @param messageContent the raw content of the message.
         */
        static void logMessageContent(ByteBuffer messageContent)
        {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "<QMAN-200002> : Message has been sent to management exchange.");
            }
        }
    }

    private UUID _brokerId;
    private Connection _connection;
    private Session _session;
    private Map<String,MessagePartListenerAdapter> _listeners;

    /**
     * Builds a new service with the given connection data.
     *
     * @param connectionData the connection data of the broker.
     */
    public QpidService(UUID brokerId)
    {
        this._brokerId = brokerId;
    }

    /**
     * Estabilishes a connection with the broker.
     *
     * @throws QpidException in case of connection failure.
     */
    public void connect() throws Exception
    {
        _connection = QpidDatasource.getInstance().getConnection(_brokerId);
        _listeners = new ConcurrentHashMap<String,MessagePartListenerAdapter>();
        _session = _connection.createSession(Constants.NO_EXPIRATION);
        _session.setSessionListener(this);
    }

    public void opened(Session ssn) {}

    public void message(Session ssn, MessageTransfer xfr)
    {
        MessagePartListenerAdapter l = _listeners.get(xfr.getDestination());
        if (l == null)
        {
            LOGGER.error("unhandled message: %s", xfr);
        }
        else
        {
            l.messageTransfer(xfr);
        }
    }

    public void exception(Session ssn, SessionException exc)
    {
        LOGGER.error(exc, "session %s exception", ssn);
    }

    public void closed(Session ssn) {}

    /**
     * All the previously entered outstanding commands are asynchronous.
     * Synchronous behavior is achieved through invoking this  method.
     */
    public void sync()
    {
        _session.sync();
    }

    /**
     * Closes communication with broker.
     */
    public void close()
    {
        try
        {
            _session.close();
            _session = null;
            _listeners = null;
        } catch (Exception e)
        {
        }
        try
        {
            _connection.close();
            _connection = null;
        } catch (Exception e)
        {
        }
    }

    /**
     * Associate a message listener with a destination therefore creating a new subscription.
     *
     * @param queueName The name of the queue that the subscriber is receiving messages from.
     * @param destinationName the name of the destination, or delivery tag, for the subscriber.
     * @param listener the listener for this destination.
     *
     * @see Session#messageSubscribe(String, String, short, short, org.apache.qpid.nclient.MessagePartListener, java.util.Map, org.apache.qpid.transport.Option...)
     */
    public void createSubscription(String queueName, String destinationName, MessageListener listener)
    {
        _listeners.put(destinationName, new MessagePartListenerAdapter(listener));
        _session.messageSubscribe
            (queueName,
             destinationName,
             MessageAcceptMode.NONE,
             MessageAcquireMode.PRE_ACQUIRED,
             null, 0, null);

        _session.messageFlow(destinationName, MessageCreditUnit.BYTE, Session.UNLIMITED_CREDIT);
        _session.messageFlow(destinationName, MessageCreditUnit.MESSAGE, Session.UNLIMITED_CREDIT);

        LOGGER.debug(
                "<QMAN-200003> : New subscription between queue %s and destination %s has been declared.",
                queueName,
                destinationName);
    }

    /**
     * Removes a previously declared consumer from the broker.
     *
     * @param destinationName the name of the destination, or delivery tag, for the subscriber.
     * @see Session#messageCancel(String, Option...)
     */
    public void removeSubscription(String destinationName)
    {
        _session.messageCancel(destinationName);
        LOGGER.debug(
                "<QMAN-200026> : Subscription named %s has been removed from remote broker.",
                destinationName);
    }

    /**
     * Declares a queue on the broker with the given name.
     *
     * @param queueName the name of the declared queue.
     * @see Session#queueDeclare(String, String, java.util.Map, Option...)
     */
    public void declareQueue(String queueName)
    {
        _session.queueDeclare(queueName, null, null);
        LOGGER.debug("<QMAN-200004> : New queue with name %s has been declared.",queueName);
    }

    /**
     * Removes the queue with the given name from the broker.
     *
     * @param queueName the name of the queue.
     * @see Session#queueDelete(String, Option...)
     */
    public void deleteQueue(String queueName)
    {
        _session.queueDelete(queueName);
        LOGGER.debug("<QMAN-2000025> : Queue with name %s has been removed.",queueName);
    }

    /**
     * Binds (on the broker) a queue with an exchange.
     *
     * @param queueName the name of the queue to bind.
     * @param exchangeName the exchange name.
     * @param routingKey the routing key used for the binding.
     * @see Session#exchangeBind(String, String, String, java.util.Map, Option...)
     */
    public void declareBinding(String queueName, String exchangeName, String routingKey)
    {
        _session.exchangeBind(queueName, exchangeName, routingKey, null);
        LOGGER.debug(
                "<QMAN-200005> : New binding with %s as routing key has been declared between queue %s and exchange %s.",
                routingKey,queueName,
                exchangeName);
    }

    /**
     * Removes a previously declare binding between an exchange and a queue.
     *
     * @param queueName the name of the queue.
     * @param exchangeName the name of the exchange.
     * @param routingKey the routing key used for binding.
     */
    public void declareUnbinding(String queueName, String exchangeName, String routingKey)
    {
        _session.exchangeUnbind(queueName, exchangeName, routingKey);
        LOGGER.debug(
                "<QMAN-200005> : Binding with %s as routing key has been removed between queue %s and exchange %s.",
                routingKey,queueName,
                exchangeName);
    }

    /**
     * Sends a command message with the given data on the management queue.
     *
     * @param messageData the command message content.
     */
    public void sendCommandMessage(byte [] messageData)
    {
        _session.messageTransfer(
                Names.MANAGEMENT_EXCHANGE,
                MessageAcceptMode.EXPLICIT,
                MessageAcquireMode.PRE_ACQUIRED,
                Configuration.getInstance().getCommandMessageHeader(),
                messageData);

        Log.logMessageContent (messageData);
    }

    /**
     * Sends a command message with the given data on the management queue.
     *
     * @param messageData the command message content.
     */
    public void sendCommandMessage(ByteBuffer messageData)
    {
        _session.messageTransfer(
                Names.MANAGEMENT_EXCHANGE,
                MessageAcceptMode.EXPLICIT,
                MessageAcquireMode.PRE_ACQUIRED,
                Configuration.getInstance().getCommandMessageHeader(),
                messageData);

        Log.logMessageContent (messageData);
    }
}