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
package org.apache.qpid.nclient.jms;

//import org.apache.qpid.nclient.api.MessageReceiver;

import org.apache.qpid.nclient.jms.message.QpidMessage;
import org.apache.qpid.nclient.impl.MessagePartListenerAdapter;
import org.apache.qpid.nclient.MessagePartListener;
import org.apache.qpidity.Range;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.Option;

import javax.jms.*;

/**
 * Implementation of JMS message consumer
 */
public class MessageConsumerImpl extends MessageActor implements MessageConsumer
{
    public static final short MESSAGE_FLOW_MODE = 0; // we use message flow mode

    /**
     * This MessageConsumer's messageselector.
     */
    private String _messageSelector = null;

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
     * A MessagePartListener set up for this consumer.
     */
    private MessageListener _messageListener;

    /**
     * The synchronous message just delivered
     */
    private QpidMessage _incomingMessage;

    /**
     * A lcok on the syncrhonous message
     */
    private final Object _incomingMessageLock = new Object();

    /**
     * Indicates that this consumer is receiving a synch message
     */
    private boolean _isReceiving = false;
   

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
     * @throws Exception If the MessageProducerImpl cannot be created due to some internal error.
     */
    protected MessageConsumerImpl(SessionImpl session, DestinationImpl destination, String messageSelector,
                                  boolean noLocal, String subscriptionName) throws Exception
    {
        super(session, destination);
        _messageSelector = messageSelector;
        _noLocal = noLocal;
        _subscriptionName = subscriptionName;
        _isStopped = getSession().isStopped();
        if (destination instanceof Queue)
        {
            // this is a queue we expect that this queue exists
            // let's create a message part assembler
            /**
             * A Qpid message listener that pushes messages to this consumer session when this consumer is
             * asynchronous or directly to this consumer when it is synchronously accessed.
             */
            MessagePartListener messageAssembler = new MessagePartListenerAdapter(new QpidMessageListener(this));
            // we use the default options:  EXCLUSIVE = false, PRE-ACCQUIRE and CONFIRM = off
            if (_noLocal)
            {
                getSession().getQpidSession()
                        .messageSubscribe(destination.getName(), getMessageActorID(), messageAssembler, null,
                                          Option.NO_LOCAL);
            }
            else
            {
                getSession().getQpidSession()
                        .messageSubscribe(destination.getName(), getMessageActorID(), messageAssembler, null);
            }
        }
        else
        {
            // this is a topic we need to create a temporary queue for this consumer
            // unless this is a durable subscriber
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
        _messageListener = messageListener;
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
        return receive(0);
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
        if (timeout < 0)
        {
            throw new JMSException("Invalid timeout value: " + timeout);
        }
        Message result;
        try
        {
            result = internalReceive(timeout);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        return result;
    }

    /**
     * Receive the next message if one is immediately available.
     *
     * @return the next message or null if one is not available.
     * @throws JMSException If receiving the next message fails due to some internal error.
     */
    public Message receiveNoWait() throws JMSException
    {
        Message result;
        try
        {
            result = internalReceive(-1);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        return result;
    }

    // not public methods

    /**
     * Receive a synchronous message
     * <p> This call blocks until a message arrives, the timeout expires, or this message consumer
     * is closed.
     * <p> A timeout of zero never expires, and the call blocks indefinitely (unless this message consumer
     * is closed)
     * <p> A timeout less than 0 returns the next message or null if one is not available.
     *
     * @param timeout The timeout value (in milliseconds)
     * @return the next message or null if one is not available.
     * @throws Exception If receiving the next message fails due to some internal error.
     */
    private Message internalReceive(long timeout) throws Exception
    {
        checkNotClosed();
        if (_messageListener != null)
        {
            throw new javax.jms.IllegalStateException("A listener has already been set.");
        }

        Message result = null;
        synchronized (_incomingMessageLock)
        {
            // This indicate to the delivery thread to deliver the message to this consumer
            // as it can happens that a message is delivered after a receive operation as returned.
            _isReceiving = true;
            boolean received = false;
            if (!_isStopped)
            {
                // if this consumer is stopped then this will be call when starting
                getSession().getQpidSession().messageFlow(getMessageActorID(), MESSAGE_FLOW_MODE, 1);
                received = getSession().getQpidSession().messageFlush(getMessageActorID());
            }
            if (!received && timeout < 0)
            {
                // this is a nowait and we havent received a message then we must immediatly return
                result = null;
            }
            else
            {
                while (_incomingMessage == null && !_isClosed)
                {
                    try
                    {
                        _incomingMessageLock.wait(timeout);
                    }
                    catch (InterruptedException e)
                    {
                        // do nothing
                    }
                }
                if (_incomingMessage != null)
                {
                    result = _incomingMessage.getJMSMessage();
                    // tell the session that a message is inprocess
                    getSession().preProcessMessage(_incomingMessage);
                    // tell the session to acknowledge this message (if required)
                    getSession().acknowledgeMessage(_incomingMessage);
                }
                _incomingMessage = null;
                // We now release any message received for this consumer
                _isReceiving = false;
            }
        }
        return result;
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
            if (_isReceiving)
            {
                // there is a synch call waiting for a message to be delivered
                // so tell the broker to deliver a message
                getSession().getQpidSession().messageFlow(getMessageActorID(), MESSAGE_FLOW_MODE, 1);
                getSession().getQpidSession().messageFlush(getMessageActorID());
            }
        }
    }

    /**
     * Deliver a message to this consumer.
     *
     * @param message The message delivered to this consumer.
     */
    protected synchronized void onMessage(QpidMessage message)
    {
        try
        {
            // if this consumer is synchronous then set the current message and
            // notify the waiting thread
            if (_messageListener == null)
            {
                synchronized (_incomingMessageLock)
                {
                    if (_isReceiving)
                    {
                        _incomingMessage = message;
                        _incomingMessageLock.notify();
                    }
                    else
                    {
                        // this message has been received after a received as returned
                        // we need to release it
                        releaseMessage(message);
                    }
                }
            }
            else
            {
                // This is an asynchronous message
                // tell the session that a message is in process
                getSession().preProcessMessage(message);
                // If the session is transacted we need to ack the message first
                // This is because a message is associated with its tx only when acked
                if (getSession().getTransacted())
                {
                    getSession().acknowledgeMessage(message);
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
                    _messageListener.onMessage(message.getJMSMessage());
                }
                catch (RuntimeException re)
                {
                    // do nothing as this message will not be redelivered
                }
                // If the session has been recovered we then need to redelivered this message
                if (getSession().isInRecovery())
                {
                    releaseMessage(message);
                }
                else if (!getSession().getTransacted())
                {
                    // Tell the jms Session to ack this message if required
                    getSession().acknowledgeMessage(message);
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Release a message
     *
     * @param message The message to be released
     * @throws JMSException If the message cannot be released due to some internal error.
     */
    private void releaseMessage(QpidMessage message) throws JMSException
    {
        Range<Long> range = new Range<Long>(message.getMessageID(), message.getMessageID());
        try
        {
            getSession().getQpidSession().messageRelease(range);
        }
        catch (QpidException e)
        {
            // notify the Exception listener
            if (getSession().getConnection().getExceptionListener() != null)
            {
                getSession().getConnection().getExceptionListener()
                        .onException(ExceptionHelper.convertQpidExceptionToJMSException(e));
            }
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Excpetion when releasing message " + message, e);
            }
        }
    }
}
