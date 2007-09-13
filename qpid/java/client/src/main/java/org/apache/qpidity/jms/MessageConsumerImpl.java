/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpidity.jms;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;

import org.apache.qpidity.QpidException;
import org.apache.qpidity.client.MessagePartListener;
import org.apache.qpidity.client.util.MessagePartListenerAdapter;
import org.apache.qpidity.exchange.ExchangeDefaults;
import org.apache.qpidity.filter.JMSSelectorFilter;
import org.apache.qpidity.filter.MessageFilter;
import org.apache.qpidity.jms.message.MessageFactory;
import org.apache.qpidity.jms.message.QpidMessage;
import org.apache.qpidity.transport.Option;
import org.apache.qpidity.transport.RangeSet;

/**
 * Implementation of JMS message consumer
 */
public class MessageConsumerImpl extends MessageActor
        implements MessageConsumer, org.apache.qpidity.client.util.MessageListener
{
    // we can receive up to 100 messages for an asynchronous listener
    public static final int MAX_MESSAGE_TRANSFERRED = 100;

    /**
     * This MessageConsumer's messageselector.
     */
    private String _messageSelector = null;

    /**
     * The message selector filter associated with this consumer message selector
     */
    private MessageFilter _filter = null;

    /**
     * NoLocal
     * If true, and the destination is a topic then inhibits the delivery of messages published
     * by its own connection.  The behavior for NoLocal is not specified if the destination is a queue.
     */
    protected boolean _noLocal;

    /**
     * The subscription name
     */
    protected String _subscriptionName;

    /**
     * Indicates whether this consumer receives pre-acquired messages
     */
    private boolean _preAcquire = true;

    /**
     * A MessagePartListener set up for this consumer.
     */
    private MessageListener _messageListener;

    /**
     * A lcok on the syncrhonous message
     */
    private final Object _incomingMessageLock = new Object();


    /**
     * Number of mesages received asynchronously
     * Nether exceed MAX_MESSAGE_TRANSFERRED
     */
    private int _messageAsyncrhonouslyReceived = 0;

    private LinkedBlockingQueue<QpidMessage> _queue = new LinkedBlockingQueue<QpidMessage>();

    //----- Constructors
    /**
     * Create a new MessageProducerImpl.
     *
     * @param session          The session from which the MessageProducerImpl is instantiated
     * @param destination      The default destination for this MessageProducerImpl
     * @param messageSelector  The message selector for this QueueReceiverImpl.
     * @param noLocal          If true inhibits the delivery of messages published by its own connection.
     * @param subscriptionName Name of the subscription if this is to be created as a durable subscriber.
     *                         If this value is null, a non-durable subscription is created.
     * @param consumerTag      Thi actor ID
     * @throws Exception If the MessageProducerImpl cannot be created due to some internal error.
     */
    protected MessageConsumerImpl(SessionImpl session, DestinationImpl destination, String messageSelector,
                                  boolean noLocal, String subscriptionName, String consumerTag) throws Exception
    {
        super(session, destination, consumerTag);
        if (messageSelector != null)
        {
            _messageSelector = messageSelector;
            _filter = new JMSSelectorFilter(messageSelector);
        }
        _noLocal = noLocal;
        _subscriptionName = subscriptionName;
        _isStopped = getSession().isStopped();
        // let's create a message part assembler

        MessagePartListener messageAssembler = new MessagePartListenerAdapter(this);

        if (destination instanceof Queue)
        {
            // this is a queue we expect that this queue exists
            getSession().getQpidSession()
                    .messageSubscribe(destination.getQpidQueueName(), // queue
                                      getMessageActorID(), // destination
                                      org.apache.qpidity.client.Session.TRANSFER_CONFIRM_MODE_NOT_REQUIRED,
                                      // When the message selctor is set we do not acquire the messages
                                      _messageSelector != null ? org.apache.qpidity.client.Session.TRANSFER_ACQUIRE_MODE_NO_ACQUIRE : org.apache.qpidity.client.Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE,
                                      messageAssembler, null, _noLocal ? Option.NO_LOCAL : Option.NO_OPTION);
            if (_messageSelector != null)
            {
                _preAcquire = false;
            }
        }
        else
        {
            // this is a topic we need to create a temporary queue for this consumer
            // unless this is a durable subscriber
            String queueName;
            if (subscriptionName != null)
            {
                // this ia a durable subscriber
                // create a persistent queue for this subscriber
                queueName = "topic-" + subscriptionName;
                getSession().getQpidSession()
                        .queueDeclare(queueName, null, null, Option.EXCLUSIVE, Option.DURABLE);
            }
            else
            {
                // this is a non durable subscriber
                queueName = destination.getQpidQueueName();
                getSession().getQpidSession()
                        .queueDeclare(queueName, null, null, Option.AUTO_DELETE, Option.EXCLUSIVE);
            }
            // bind this queue with the topic exchange
            getSession().getQpidSession()
                    .queueBind(queueName, ExchangeDefaults.TOPIC_EXCHANGE_NAME, destination.getRoutingKey(), null);
            // subscribe to this topic 
            getSession().getQpidSession()
                    .messageSubscribe(queueName, getMessageActorID(),
                                      org.apache.qpidity.client.Session.TRANSFER_CONFIRM_MODE_NOT_REQUIRED,
                                      // We always acquire the messages
                                      org.apache.qpidity.client.Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE,
                                      messageAssembler, null, _noLocal ? Option.NO_LOCAL : Option.NO_OPTION,
                                      // Request exclusive subscription access, meaning only this subscription
                                      // can access the queue.
                                      Option.EXCLUSIVE);

        }
        // set the flow mode
        getSession().getQpidSession()
                .messageFlowMode(getMessageActorID(), org.apache.qpidity.client.Session.MESSAGE_FLOW_MODE_CREDIT);

        // this will prevent the broker from sending more than one message
        // When a messageListener is set the flow will be adjusted.
        // until then we assume it's for synchronous message consumption
        requestCredit(1);
        requestSync();
        // check for an exception
        if (getSession().getCurrentException() != null)
        {
            throw getSession().getCurrentException();
        }
    }

    //----- Message consumer API
    /**
     * Gets this  MessageConsumer's message selector.
     *
     * @return This MessageConsumer's message selector, or null if no
     *         message selector exists for the message consumer (that is, if
     *         the message selector was not set or was set to null or the
     *         empty string)
     * @throws JMSException if getting the message selector fails due to some internal error.
     */
    public String getMessageSelector() throws JMSException
    {
        checkNotClosed();
        return _messageSelector;
    }

    /**
     * Gets this MessageConsumer's <CODE>MessagePartListener</CODE>.
     *
     * @return The listener for the MessageConsumer, or null if no listener is set
     * @throws JMSException if getting the message listener fails due to some internal error.
     */
    public MessageListener getMessageListener() throws JMSException
    {
        checkNotClosed();
        return _messageListener;
    }

    /**
     * Sets the MessageConsumer's <CODE>MessagePartListener</CODE>.
     * <p> The JMS specification says:
     * <P>Setting the message listener to null is the equivalent of
     * unsetting the message listener for the message consumer.
     * <P>The effect of calling <CODE>MessageConsumer.setMessageListener</CODE>
     * while messages are being consumed by an existing listener
     * or the consumer is being used to consume messages synchronously
     * is undefined.
     *
     * @param messageListener The listener to which the messages are to be delivered
     * @throws JMSException If setting the message listener fails due to some internal error.
     */
    public synchronized void setMessageListener(MessageListener messageListener) throws JMSException
    {
        // this method is synchronized as onMessage also access _messagelistener
        // onMessage, getMessageListener and this method are the only synchronized methods
        checkNotClosed();
        try
        {
            _messageListener = messageListener;
            if (messageListener != null)
            {
                resetAsynchMessageReceived();
            }
        }
        catch (Exception e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Contact the broker and ask for the delivery of MAX_MESSAGE_TRANSFERRED messages
     *
     * @throws QpidException If there is a communication error
     */
    private void resetAsynchMessageReceived() throws QpidException
    {
        if (!_isStopped && _messageAsyncrhonouslyReceived >= MAX_MESSAGE_TRANSFERRED)
        {
            getSession().getQpidSession().messageStop(getMessageActorID());
        }
        _messageAsyncrhonouslyReceived = 0;
        requestCredit(MAX_MESSAGE_TRANSFERRED);
    }

    /**
     * Receive the next message produced for this message consumer.
     * <P>This call blocks indefinitely until a message is produced or until this message consumer is closed.
     *
     * @return The next message produced for this message consumer, or
     *         null if this message consumer is concurrently closed
     * @throws JMSException If receiving the next message fails due to some internal error.
     */
    public Message receive() throws JMSException
    {
        // Check if we can get a message immediately
        Message result;
        result = receiveNoWait();
        if (result != null)
        {
            return result;
        }
        try
        {
            // Now issue a credit and wait for the broker to send a message
            // IMO no point doing a credit() flush() and sync() in a loop.
            // This will only overload the broker. After the initial try we can wait
            // for the broker to send a message when it gets one
            requestCredit(1);
            return (Message) _queue.take();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Receive the next message that arrives within the specified timeout interval.
     * <p> This call blocks until a message arrives, the timeout expires, or this message consumer
     * is closed.
     * <p> A timeout of zero never expires, and the call blocks indefinitely.
     * <p> A timeout less than 0 throws a JMSException.
     *
     * @param timeout The timeout value (in milliseconds)
     * @return The next message that arrives within the specified timeout interval.
     * @throws JMSException If receiving the next message fails due to some internal error.
     */
    public Message receive(long timeout) throws JMSException
    {
        checkClosed();
        checkIfListenerSet();
        if (timeout < 0)
        {
            throw new JMSException("Invalid timeout value: " + timeout);
        }

        Message result;
        try
        {
            // first check if we have any in the queue already
            result = (Message) _queue.poll();
            if (result == null)
            {
                requestCredit(1);
                requestFlush();
                // We shouldn't do a sync(). Bcos the timeout can happen
                // before the sync() returns
                return (Message) _queue.poll(timeout, TimeUnit.MILLISECONDS);
            }
            else
            {
                return result;
            }
        }
        catch (Exception e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Receive the next message if one is immediately available.
     *
     * @return the next message or null if one is not available.
     * @throws JMSException If receiving the next message fails due to some internal error.
     */
    public Message receiveNoWait() throws JMSException
    {
        checkClosed();
        checkIfListenerSet();
        Message result;
        try
        {
            // first check if we have any in the queue already
            result = (Message) _queue.poll();
            if (result == null)
            {
                requestCredit(1);
                requestFlush();
                requestSync();
                return (Message) _queue.poll();
            }
            else
            {
                return result;
            }
        }
        catch (Exception e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    // not public methods
    /**
     * Upon receipt of this method, the broker adds "value"
     * number of messages to the available credit balance for this consumer.
     *
     * @param value Number of credits, a value of 0 indicates an infinite amount of credit.
     */
    private void requestCredit(int value)
    {
        getSession().getQpidSession()
                .messageFlow(getMessageActorID(), org.apache.qpidity.client.Session.MESSAGE_FLOW_UNIT_MESSAGE, value);
    }

    /**
     * Forces the broker to exhaust its credit supply.
     * <p> The broker's credit will always be zero when
     * this method completes.
     */
    private void requestFlush()
    {
        getSession().getQpidSession().messageFlush(getMessageActorID());
    }

    /**
     * Sync method will block until all outstanding broker
     * commands
     * are executed.
     */
    private void requestSync()
    {
        getSession().getQpidSession().sync();
    }

    /**
     * Check whether this consumer is closed.
     *
     * @throws JMSException If this consumer is closed.
     */
    private void checkClosed() throws JMSException
    {
        if (_isStopped)
        {
            throw new JMSException("Session is closed");
        }
    }

    /**
     * Stop the delivery of messages to this consumer.
     * <p>For asynchronous receiver, this operation blocks until the message listener
     * finishes processing the current message,
     *
     * @throws Exception If the consumer cannot be stopped due to some internal error.
     */
    protected void stop() throws Exception
    {
        getSession().getQpidSession().messageStop(getMessageActorID());
        _isStopped = true;
    }

    /**
     * Start the delivery of messages to this consumer.
     *
     * @throws Exception If the consumer cannot be started due to some internal error.
     */
    protected void start() throws Exception
    {
        synchronized (_incomingMessageLock)
        {
            _isStopped = false;
        }
    }

    /**
     * This method notifies this consumer that a message has been delivered
     * @param message The received message.
     */
    public void onMessage(org.apache.qpidity.api.Message message)
    {
        try
        {
            QpidMessage jmsMessage = MessageFactory.getQpidMessage(message);
            if (checkPreConditions(jmsMessage))
            {
                preApplicationProcessing(jmsMessage);

                if (_messageListener == null)
                {
                    _queue.offer(jmsMessage);
                }
                else
                {
                    // I still think we don't need that additional thread in SessionImpl
                    // if the Application blocks on a message thats fine
                    // getSession().dispatchMessage(getMessageActorID(), jmsMessage);
                    notifyMessageListener(jmsMessage);
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }


    public void notifyMessageListener(QpidMessage message) throws RuntimeException
    {
        try
        {
            _messageAsyncrhonouslyReceived++;
            if (_messageAsyncrhonouslyReceived >= MAX_MESSAGE_TRANSFERRED)
            {
                // ask the server for the delivery of MAX_MESSAGE_TRANSFERRED more messages
                resetAsynchMessageReceived();
            }

            // The JMS specs says:
            /* The result of a listener throwing a RuntimeException depends on the session?s
            * acknowledgment mode.
            ? --- AUTO_ACKNOWLEDGE or DUPS_OK_ACKNOWLEDGE - the message
            * will be immediately redelivered. The number of times a JMS provider will
            * redeliver the same message before giving up is provider-dependent.
            ? --- CLIENT_ACKNOWLEDGE - the next message for the listener is delivered.
            * --- Transacted Session - the next message for the listener is delivered.
            *
            * The number of time we try redelivering the message is 0
            **/
            try
            {

                _messageListener.onMessage((Message) message);
            }
            catch (RuntimeException re)
            {
                // do nothing as this message will not be redelivered
            }


        }
        catch (Exception e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Check whether this consumer is asynchronous
     *
     * @throws javax.jms.IllegalStateException If this consumer is asynchronous.
     */
    private void checkIfListenerSet() throws javax.jms.IllegalStateException
    {

        if (_messageListener != null)
        {
            throw new javax.jms.IllegalStateException("A listener has already been set.");
        }
    }

    /**
     * pre process a received message.
     *
     * @param message The message to pre-process.
     * @throws Exception If the message  cannot be pre-processed due to some internal error.
     */
    private void preApplicationProcessing(QpidMessage message) throws Exception
    {
        getSession().preProcessMessage(message);
        // If the session is transacted we need to ack the message first
        // This is because a message is associated with its tx only when acked
        if (getSession().getTransacted())
        {
            getSession().acknowledgeMessage(message);
        }
        message.afterMessageReceive();
    }

    /**
     * Check whether a message can be delivered to this consumer.
     *
     * @param message The message to be checked.
     * @return true if the message matches the selector and can be acquired, false otherwise.
     * @throws QpidException If the message preConditions cannot be checked due to some internal error.
     */
    private boolean checkPreConditions(QpidMessage message) throws QpidException
    {
        boolean messageOk = true;
        if (_messageSelector != null)
        {
            messageOk = _filter.matches((Message) message);
        }
        if (_logger.isDebugEnabled())
        {
            _logger.debug("messageOk " + messageOk);
            _logger.debug("_preAcquire " + _preAcquire);
        }
        if (!messageOk && _preAcquire)
        {
            // this is the case for topics
            // We need to ack this message
            if (_logger.isDebugEnabled())
            {
                _logger.debug("filterMessage - trying to ack message");
            }
            acknowledgeMessage(message);
        }
        else if (!messageOk)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Message not OK, releasing");
            }
            releaseMessage(message);
        }
        // now we need to acquire this message if needed
        // this is the case of queue with a message selector set
        if (!_preAcquire && messageOk)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("filterMessage - trying to acquire message");
            }
            messageOk = acquireMessage(message);
        }
        return messageOk;
    }

    /**
     * Release a message
     *
     * @param message The message to be released
     * @throws QpidException If the message cannot be released due to some internal error.
     */
    private void releaseMessage(QpidMessage message) throws QpidException
    {
        if (_preAcquire)
        {
            RangeSet ranges = new RangeSet();
            ranges.add(message.getMessageTransferId());
            getSession().getQpidSession().messageRelease(ranges);
            getSession().testQpidException();
        }
    }

    /**
     * Acquire a message
     *
     * @param message The message to be acquired
     * @return true if the message has been acquired, false otherwise.
     * @throws QpidException If the message cannot be acquired due to some internal error.
     */
    private boolean acquireMessage(QpidMessage message) throws QpidException
    {
        boolean result = false;
        if (!_preAcquire)
        {
            RangeSet ranges = new RangeSet();
            ranges.add(message.getMessageTransferId());

            getSession().getQpidSession()
                    .messageAcquire(ranges, org.apache.qpidity.client.Session.MESSAGE_ACQUIRE_ANY_AVAILABLE_MESSAGE);
            getSession().getQpidSession().sync();
            RangeSet acquired = getSession().getQpidSession().getAccquiredMessages();
            if (acquired.size() > 0)
            {
                result = true;
            }
            getSession().testQpidException();
        }
        return result;
    }

    /**
     * Acknowledge a message
     *
     * @param message The message to be acknowledged
     * @throws QpidException If the message cannot be acquired due to some internal error.
     */
    private void acknowledgeMessage(QpidMessage message) throws QpidException
    {
        if (!_preAcquire)
        {
            RangeSet ranges = new RangeSet();
            ranges.add(message.getMessageTransferId());
            getSession().getQpidSession().messageAcknowledge(ranges);
            getSession().testQpidException();
        }
    }
}
