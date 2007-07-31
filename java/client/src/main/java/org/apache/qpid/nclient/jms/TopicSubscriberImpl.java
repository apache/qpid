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

import javax.jms.TopicSubscriber;
import javax.jms.Topic;
import javax.jms.JMSException;

/**
 * Implementation of the JMS TopicSubscriber interface.
 */
public class TopicSubscriberImpl extends MessageConsumerImpl implements TopicSubscriber
{
    //--- Constructor
    /**
     * Create a new TopicSubscriberImpl.
     *
     * @param session          The session of this topic subscriber.
     * @param topic            The default topic for this TopicSubscriberImpl
     * @param messageSelector  The MessageSelector
     * @param noLocal          If true inhibits the delivery of messages published by its own connection.
     * @param subscriptionName Name of the subscription if this is to be created as a durable subscriber.
     *                         If this value is null, a non-durable subscription is created.
     * @throws javax.jms.JMSException If the TopicSubscriberImpl cannot be created due to internal error.
     */
    protected TopicSubscriberImpl(SessionImpl session, Topic topic, String messageSelector, boolean noLocal,
                                  String subscriptionName) throws JMSException
    {
        super(session, (DestinationImpl) topic, messageSelector, noLocal, subscriptionName);
    }

    //---  javax.jms.TopicSubscriber interface
    /**
     * Get the Topic associated with this subscriber.
     *
     * @return This subscriber's Topic
     * @throws JMSException if getting the topic for this topicSubscriber fails due to some internal error.
     */
    public Topic getTopic() throws JMSException
    {
        checkNotClosed();
        return (TopicImpl) _destination;
    }


    /**
     * Get NoLocal for this subscriber.
     *
     * @return True if locally published messages are being inhibited, false otherwise
     * @throws JMSException If getting NoLocal for this topic subscriber fails due to some internal error.
     */
    public boolean getNoLocal() throws JMSException
    {
        checkNotClosed();
        return _noLocal;
    }
}
