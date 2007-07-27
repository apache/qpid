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

import org.apache.qpid.nclient.api.MessageReceiver;
import org.apache.qpid.nclient.exception.QpidException;

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
     * The underlying qpid receiver
     */
    MessageReceiver _qpidReceiver;

    /**
     * This MessageConsumer's messageselector.
     */
    protected String _messageSelector = null;

    /**
     * A MessageListener set up for this consumer.
     */
    private MessageListener _messageListener = null;

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
         // create a message listener wrapper 
    }

    public Message receive() throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Message receive(long l) throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Message receiveNoWait() throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
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
        try
        {
            _qpidReceiver.stop();
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Start the delivery of messages to this consumer.
     *
     * @throws JMSException If the consumer cannot be started due to some internal error.
     */
    void start() throws JMSException
    {
        try
        {
            _qpidReceiver.start();
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }
}
