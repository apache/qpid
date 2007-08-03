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
import org.apache.qpidity.QpidException;
import org.apache.qpidity.Option;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Message;

/**
 * Implementation of JMS message consumer
 */
public class MessageConsumerImpl extends MessageActor implements MessageConsumer
{

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
     * A MessageListener set up for this consumer.
     */
    private MessageListener _messageListener;

    /**
     * A warpper around the JSM message listener 
     */
    private MessageListenerWrapper _messageListenerWrapper;

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
     * @throws JMSException If the MessageProducerImpl cannot be created due to some internal error.
     */
    protected MessageConsumerImpl(SessionImpl session, DestinationImpl destination, String messageSelector,
                                  boolean noLocal, String subscriptionName) throws JMSException
    {
        super(session, destination);
        _messageSelector = messageSelector;
        _noLocal = noLocal;
        _subscriptionName = subscriptionName;
        /*try
        {
            // TODO define the relevant options 
            _qpidReceiver = _session.getQpidSession().createReceiver(destination.getName(), Option.DURABLE);
            _qpidResource = _qpidReceiver;
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }*/
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
     * Gets this MessageConsumer's <CODE>MessageListener</CODE>.
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
     * Sets the MessageConsumer's <CODE>MessageListener</CODE>.
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
    public void setMessageListener(MessageListener messageListener) throws JMSException
    {
        checkNotClosed();
        _messageListener = messageListener;
        if( messageListener == null )
        {

          _messageListenerWrapper = null;
        }
        else
        {
            _messageListenerWrapper = new MessageListenerWrapper(this);          
              //TODO      _qpidReceiver.setAsynchronous(_messageListenerWrapper);
        }
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
     * <p> This call blocks until a message arrives, the timeout expires, or this message consumer is closed.
     * <p> A timeout of zero never expires, and the call blocks indefinitely.
     * <p> A timeout less than 0 throws a JMSException.
     *
     * @param timeout The timeout value (in milliseconds)
     * @return The next message that arrives within the specified timeout interval.
     * @throws JMSException If receiving the next message fails due to some internal error.
     */
    public Message receive(long timeout) throws JMSException
    {
        Message message = null;
        // todo convert this message into a JMS one: _qpidReceiver.receive(-1);
        return message;
    }

    /**
     * Receive the next message if one is immediately available.
     *
     * @return the next message or null if one is not available.
     * @throws JMSException If receiving the next message fails due to some internal error.
     */
    public Message receiveNoWait() throws JMSException
    {
        return receive(-1);
    }


    // not public methods
    /**
     * Stop the delivery of messages to this receiver.
     * <p>For asynchronous receiver, this operation blocks until the message listener
     * finishes processing the current message,
     *
     * @throws JMSException If the consumer cannot be stopped due to some internal error.
     */
    void stop() throws JMSException
    {
        /*try
        {
            _qpidReceiver.stop();
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
            }*/
    }

    /**
     * Start the delivery of messages to this consumer.
     *
     * @throws JMSException If the consumer cannot be started due to some internal error.
     */
    void start() throws JMSException
    {
        /*try
        {
            _qpidReceiver.start();
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
            }*/
    }
}
