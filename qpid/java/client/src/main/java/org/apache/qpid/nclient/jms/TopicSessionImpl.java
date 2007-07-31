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

import javax.jms.*;
import javax.jms.IllegalStateException;

/**
 * Implements  TopicSession
 */
public class TopicSessionImpl extends SessionImpl implements TopicSession
{
    //-- constructor
    /**
     * Create a new TopicSessionImpl.
     *
     * @param connection      The ConnectionImpl object from which the Session is created.
     * @param transacted      Specifiy whether this session is transacted?
     * @param acknowledgeMode The session's acknowledgement mode. This value is ignored and set to
     *                        {@link javax.jms.Session#SESSION_TRANSACTED} if the <code>transacted</code> parameter
     *                        is true.
     * @throws javax.jms.JMSSecurityException If the user could not be authenticated.
     * @throws javax.jms.JMSException         In case of internal error.
     */
    protected TopicSessionImpl(ConnectionImpl connection, boolean transacted, int acknowledgeMode) throws JMSException
    {
        super(connection, transacted, acknowledgeMode);
    }

    //-- Overwritten methods
    /**
     * Create a QueueBrowser.
     *
     * @param queue           The <CODE>Queue</CODE> to browse.
     * @param messageSelector Only messages with properties matching the message selector expression are delivered.
     * @return Always throws an exception
     * @throws IllegalStateException Always
     */
    @Override
    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException
    {
        throw new IllegalStateException("Cannot invoke createBrowser from TopicSession");
    }

    /**
     * Create a QueueBrowser.
     *
     * @param queue The <CODE>Queue</CODE> to browse.
     * @return Always throws an exception
     * @throws IllegalStateException Always
     */
    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException
    {
        throw new IllegalStateException("Cannot invoke createBrowser from TopicSession");
    }

    /**
     * Creates a temporary queue.
     *
     * @return Always throws an exception
     * @throws IllegalStateException Always
     */
    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        throw new IllegalStateException("Cannot invoke createTemporaryQueue from TopicSession");
    }

    /**
     * Creates a queue identity by a given name.
     *
     * @param queueName the name of this <CODE>Queue</CODE>
     * @return Always throws an exception
     * @throws IllegalStateException Always
     */
    @Override
    public Queue createQueue(String queueName) throws JMSException
    {
        throw new IllegalStateException("Cannot invoke createQueue from TopicSession");
    }

    //--- Interface TopicSession
    /**
     * Create a publisher for the specified topic.
     *
     * @param topic the <CODE>Topic</CODE> to publish to, or null if this is an unidentified publisher.
     * @throws JMSException                If the creating a publisher fails due to some internal error.
     * @throws InvalidDestinationException If an invalid topic is specified.
     */
    public TopicPublisher createPublisher(Topic topic) throws JMSException
    {

        checkNotClosed();
        // we do not check the destination topic here, since unidentified publishers are allowed.
        return new TopicPublisherImpl(this, topic);
    }

    /**
     * Creates a nondurable subscriber to the specified topic.
     *
     * @param topic The Topic to subscribe to
     * @throws JMSException                If creating a subscriber fails due to some internal error.
     * @throws InvalidDestinationException If an invalid topic is specified.
     */
    public TopicSubscriber createSubscriber(Topic topic) throws JMSException
    {
        return createSubscriber(topic, null, false);
    }

    /**
     * Creates a nondurable subscriber to the specified topic, using a
     * message selector or specifying whether messages published by its
     * own connection should be delivered to it.
     *
     * @param topic           The Topic to subscribe to
     * @param messageSelector A value of null or an empty string indicates that there is no message selector.
     * @param noLocal         If true then inhibits the delivery of messages published by this subscriber's connection.
     * @throws JMSException                If creating a subscriber fails due to some internal error.
     * @throws InvalidDestinationException If an invalid topic is specified.
     * @throws InvalidSelectorException    If the message selector is invalid.
     */
    public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal) throws JMSException
    {
        checkNotClosed();
        checkDestination(topic);
        return new TopicSubscriberImpl(this, topic, messageSelector, noLocal, null);
    }       
}
