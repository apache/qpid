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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpidity.jms.message.QpidMessage;
import org.apache.qpidity.RangeSet;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.Option;
import org.apache.qpidity.filter.MessageFilter;
import org.apache.qpidity.filter.JMSSelectorFilter;
import org.apache.qpidity.client.MessagePartListener;
import org.apache.qpidity.client.util.MessagePartListenerAdapter;
import org.apache.qpidity.exchange.ExchangeDefaults;

import javax.jms.*;

/**
 * Implementation of JMS message consumer
 */
public class MessageConsumerImpl extends MessageActor implements MessageConsumer
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

    /**
     * Indicates that a nowait is receiving a message.
     */
    private boolean _isNoWaitIsReceiving = false;

    /**
     * Number of mesages received asynchronously
     * Nether exceed MAX_MESSAGE_TRANSFERRED
     */
    private int _messageAsyncrhonouslyReceived = 0;
    
    private AtomicBoolean _messageReceived = new AtomicBoolean();

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
                                  boolean noLocal, String subscriptionName,String consumerTag) throws Exception
    {
        super(session, destination,consumerTag);
        if (messageSelector != null)
        {
            _messageSelector = messageSelector;
            _filter = new JMSSelectorFilter(messageSelector);
        }
        _noLocal = noLocal;
        _subscriptionName = subscriptionName;
        _isStopped = getSession().isStopped();
        // let's create a message part assembler
        /**
         * A Qpid message listener that pushes messages to this consumer session when this consumer is
         * asynchronous or directly to this consumer when it is synchronously accessed.
         */
        MessagePartListener messageAssembler = new MessagePartListenerAdapter(new QpidMessageListener(this));

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
        getSession().getQpidSession()
        .messageFlow(getMessageActorID(), org.apache.qpidity.client.Session.MESSAGE_FLOW_UNIT_MESSAGE,1);
        
        getSession().getQpidSession().sync();
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
        getSession().getQpidSession()
                .messageFlow(getMessageActorID(), org.apache.qpidity.client.Session.MESSAGE_FLOW_UNIT_MESSAGE,
                             MAX_MESSAGE_TRANSFERRED);
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
        Message result = null;
        
        if (_messageListener != null)
        {
            throw new javax.jms.IllegalStateException("A listener has already been set.");
        }
        
        if (_incomingMessage != null)
        {
            System.out.println("We already had a message in the queue");
            result = (Message) _incomingMessage;
            _incomingMessage = null;
            return result;
        }
       
        synchronized (_incomingMessageLock)
        {
            // This indicate to the delivery thread to deliver the message to this consumer
            // as it can happens that a message is delivered after a receive operation as returned.
            _isReceiving = true;
            if (!_isStopped)
            {
                // if this consumer is stopped then this will be call when starting
                getSession().getQpidSession()
                        .messageFlow(getMessageActorID(), org.apache.qpidity.client.Session.MESSAGE_FLOW_UNIT_MESSAGE,
                                     1);
                getSession().getQpidSession().messageFlush(getMessageActorID());
                _messageReceived.set(false);
                System.out.println("no message in the queue, issuing a flow(1) and waiting for message");
                                
                //When sync() returns we know whether we have received a message or not.
                getSession().getQpidSession().sync();
                
                System.out.println("we got returned from sync()"); 
                //received = getSession().getQpidSession().messagesReceived();
            }            
            if (_messageReceived.get() && timeout < 0)
            {
                // this is a nowait and we havent received a message then we must immediatly return
                result = null;
            }
            else
            {
                // right we need to let onMessage know that a nowait is potentially waiting for a message
                if (timeout < 0)
                {
                    _isNoWaitIsReceiving = true;
                }
                while (_incomingMessage == null && !_isClosed)
                {
                    try
                    {
                        System.out.println("waiting for message");                        
                        _incomingMessageLock.wait(timeout);
                    }
                    catch (InterruptedException e)
                    {
                        // do nothing
                    }
                }
                if (_incomingMessage != null)
                {
                    result = (Message) _incomingMessage;
                    // tell the session that a message is inprocess
                    getSession().preProcessMessage(_incomingMessage);
                    // tell the session to acknowledge this message (if required)
                    getSession().acknowledgeMessage(_incomingMessage);
                }
                _incomingMessage = null;
            }
            // We now release any message received for this consumer
            _isReceiving = false;
            _isNoWaitIsReceiving = false;
            getSession().testQpidException();            
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
                getSession().getQpidSession()
                        .messageFlow(getMessageActorID(), org.apache.qpidity.client.Session.MESSAGE_FLOW_UNIT_MESSAGE,
                                     1);
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
            // if there is a message selector then we need to evaluate it.
            boolean messageOk = true;
            if (_messageSelector != null)
            {
                messageOk = _filter.matches((Message) message);
            }
            if (!messageOk && _preAcquire)
            {
                // this is the case for topics
                // We need to ack this message
                acknowledgeMessage(message);
            }
            // now we need to acquire this message if needed
            // this is the case of queue with a message selector set
            if (!_preAcquire && messageOk)
            {
                messageOk = acquireMessage(message);
            }

            // if this consumer is synchronous then set the current message and
            // notify the waiting thread
            if (_messageListener == null)
            {
                System.out.println("Received a message- onMessage in message consumer Impl");
                
                synchronized (_incomingMessageLock)
                {
                    if (messageOk)
                    {
                        System.out.println("Received a message- onMessage in message ok " + messageOk);
                        // we have received a proper message that we can deliver
                        if (_isReceiving)
                        {
                            System.out.println("Received a message- onMessage in message _isReceiving");
                            
                            _incomingMessage = message;
                            _incomingMessageLock.notify();
                        }
                        else
                        {
                            System.out.println("Received a message- onMessage in message releasing");
                            // this message has been received after a received as returned
                            // we need to release it
                            releaseMessage(message);
                        }
                    }
                    else
                    {
                        // oups the message did not match the selector or we did not manage to acquire it
                        // If the receiver is still waiting for a message
                        // then we need to request a new one from the server
                        if (_isReceiving)
                        {
                            getSession().getQpidSession()
                                    .messageFlow(getMessageActorID(),
                                                 org.apache.qpidity.client.Session.MESSAGE_FLOW_UNIT_MESSAGE, 1);
                            getSession().getQpidSession().messageFlush(getMessageActorID());
                            _messageReceived.set(false);
                            
                            // When sync() returns we know whether we have received a message or not.
                            getSession().getQpidSession().sync();                       
                            
                            if (_messageReceived.get()  && _isNoWaitIsReceiving)
                            {
                                // Right a message nowait is waiting for a message
                                // but no one can be delivered it then need to return
                                _incomingMessageLock.notify();
                            }
                        }
                    }
                }
            }
            else
            {
                _messageAsyncrhonouslyReceived++;
                if (_messageAsyncrhonouslyReceived >= MAX_MESSAGE_TRANSFERRED)
                {
                    // ask the server for the delivery of MAX_MESSAGE_TRANSFERRED more messages
                    resetAsynchMessageReceived();
                }
                // only deliver the message if it is valid 
                if (messageOk)
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
                        _messageListener.onMessage((Message) message);
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
    
    public void notifyMessageReceived()
    {
        _messageReceived.set(true);
    }
}
