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

/**
 * Implements TopicPublisher
 */
public class TopicPublisherImpl extends MessageProducerImpl implements TopicPublisher
{
    //--- Constructor
    /**
     * Create a TopicPublisherImpl.
     *
     * @param session The session from which the TopicPublisherImpl is instantiated
     * @param topic   The default topic for this TopicPublisherImpl
     * @throws JMSException If the TopicPublisherImpl cannot be created due to some internal error.
     */
    protected TopicPublisherImpl(SessionImpl session, Topic topic) throws JMSException
    {
        super(session, (DestinationImpl) topic);
    }

    //--- Interface javax.jms.TopicPublisher
    /**
     * Get the topic associated with this TopicPublisher.
     *
     * @return This publisher's topic
     * @throws JMSException If getting the topic fails due to some internal error.
     */
    public Topic getTopic() throws JMSException
    {
        return (Topic) getDestination();
    }


    /**
     * Publish a message to the topic using the default delivery mode, priority and time to live.
     *
     * @param message The message to publish
     * @throws JMSException If publishing the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If an invalid message is specified.
     * @throws javax.jms.InvalidDestinationException
     *                      If an invalid topic is specified.
     * @throws java.lang.UnsupportedOperationException
     *                      If that publisher topic was not specified at creation time.
     */
    public void publish(Message message) throws JMSException
    {
        super.send(message);
    }

    /**
     * Publish a message to the topic, specifying delivery mode, priority and time to live.
     *
     * @param message      The message to publish
     * @param deliveryMode The delivery mode to use
     * @param priority     The priority for this message
     * @param timeToLive   The message's lifetime (in milliseconds)
     * @throws JMSException If publishing the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If an invalid message is specified.
     * @throws javax.jms.InvalidDestinationException
     *                      If an invalid topic is specified.
     * @throws java.lang.UnsupportedOperationException
     *                      If that publisher topic was not specified at creation time.
     */
    public void publish(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException
    {
        super.send(message, deliveryMode, priority, timeToLive);
    }


    /**
     * Publish a message to a topic for an unidentified message producer.
     * Uses this TopicPublisher's default delivery mode, priority and time to live.
     *
     * @param topic   The topic to publish this message to
     * @param message The message to publish
     * @throws JMSException If publishing the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If an invalid message is specified.
     * @throws javax.jms.InvalidDestinationException
     *                      If an invalid topic is specified.
     */
    public void publish(Topic topic, Message message) throws JMSException
    {
        super.send(topic, message);
    }

    /**
     * Publishes a message to a topic for an unidentified message
     * producer, specifying delivery mode, priority and time to live.
     *
     * @param topic        The topic to publish this message to
     * @param message      The message to publish
     * @param deliveryMode The delivery mode
     * @param priority     The priority for this message
     * @param timeToLive   The message's lifetime (in milliseconds)
     * @throws JMSException If publishing the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If an invalid message is specified.
     * @throws javax.jms.InvalidDestinationException
     *                      If an invalid topic is specified.
     */
    public void publish(Topic topic, Message message, int deliveryMode, int priority, long timeToLive) throws
                                                                                                       JMSException
    {
        super.send(topic, message, deliveryMode, priority, timeToLive);
    }
}
