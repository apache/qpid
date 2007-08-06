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

import javax.jms.*;
import javax.jms.IllegalStateException;

/**
 * Implementation of javax.jms.QueueSession
 */
public class QueueSessionImpl extends SessionImpl implements QueueSession
{
    //--- constructor
    /**
     * Create a JMS Session
     *
     * @param connection      The ConnectionImpl object from which the Session is created.
     * @param transacted      Indicates if the session transacted.
     * @param acknowledgeMode The session's acknowledgement mode. This value is ignored and set to
     *                        {@link javax.jms.Session#SESSION_TRANSACTED} if the <code>transacted</code>
     *                        parameter is true.
     * @throws javax.jms.JMSSecurityException If the user could not be authenticated.
     * @throws javax.jms.JMSException         In case of internal error.
     */
    protected QueueSessionImpl(ConnectionImpl connection, boolean transacted, int acknowledgeMode) throws JMSException
    {
        super(connection, transacted, acknowledgeMode);
    }

    //-- Overwritten methods
    /**
     * Creates a durable subscriber to the specified topic,
     *
     * @param topic The non-temporary <CODE>Topic</CODE> to subscribe to.
     * @param name  The name used to identify this subscription.
     * @return Always throws an exception
     * @throws IllegalStateException Always
     */
    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException
    {
        throw new IllegalStateException("Cannot invoke createDurableSubscriber from QueueSession");
    }

    /**
     * Create a TemporaryTopic.
     *
     * @return Always throws an exception
     * @throws IllegalStateException Always
     */
    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        throw new IllegalStateException("Cannot invoke createTemporaryTopic from QueueSession");
    }

    /**
     * Creates a topic identity given a Topicname.
     *
     * @param topicName The name of this <CODE>Topic</CODE>
     * @return Always throws an exception
     * @throws IllegalStateException Always
     */
    @Override
    public Topic createTopic(String topicName) throws JMSException
    {
        throw new IllegalStateException("Cannot invoke createTopic from QueueSession");
    }

    /**
     * Unsubscribes a durable subscription that has been created by a client.
     *
     * @param name the name used to identify this subscription
     * @throws IllegalStateException Always
     */
    @Override
    public void unsubscribe(String name) throws JMSException
    {
        throw new IllegalStateException("Cannot invoke unsubscribe from QueueSession");
    }

    //--- Interface javax.jms.QueueSession
    /**
     * Create a QueueReceiver to receive messages from the specified queue.
     *
     * @param queue the <CODE>Queue</CODE> to access
     * @return A QueueReceiver
     * @throws JMSException                If creating a receiver fails due to some internal error.
     * @throws InvalidDestinationException If an invalid queue is specified.
     */
    public QueueReceiver createReceiver(Queue queue) throws JMSException
    {
        return createReceiver(queue, null);
    }

    /**
     * Create a QueueReceiver to receive messages from the specified queue for a given message selector.
     *
     * @param queue           the Queue to access
     * @param messageSelector A value of null or an empty string indicates that
     *                        there is no message selector for the message consumer.
     * @return A QueueReceiver
     * @throws JMSException                If creating a receiver fails due to some internal error.
     * @throws InvalidDestinationException If an invalid queue is specified.
     * @throws InvalidSelectorException    If the message selector is invalid.
     */
    public QueueReceiver createReceiver(Queue queue, String messageSelector) throws JMSException
    {
        checkNotClosed();
        checkDestination(queue);
        QueueReceiver receiver;
        try
        {
            receiver =  new QueueReceiverImpl(this, queue, messageSelector);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        return receiver;
    }

    /**
     * Create a QueueSender object to send messages to the specified queue.
     *
     * @param queue the Queue to access, or null if this is an unidentified producer
     * @return A QueueSender
     * @throws JMSException                If creating the sender fails due to some internal error.
     * @throws InvalidDestinationException If an invalid queue is specified.
     */
    public QueueSender createSender(Queue queue) throws JMSException
    {
        checkNotClosed();
        // we do not check the destination since unidentified producers are allowed (no default destination).
        return new QueueSenderImpl(this, (QueueImpl) queue);
    }
}
