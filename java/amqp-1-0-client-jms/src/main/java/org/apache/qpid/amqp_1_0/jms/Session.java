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

package org.apache.qpid.amqp_1_0.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Topic;


import java.io.Serializable;

public interface Session extends javax.jms.Session
{
    static enum AcknowledgeMode { SESSION_TRANSACTED, AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE };

    BytesMessage createBytesMessage() throws JMSException;

    MapMessage createMapMessage() throws JMSException;

    Message createMessage() throws JMSException;

    ObjectMessage createObjectMessage() throws JMSException;

    ObjectMessage createObjectMessage(Serializable serializable) throws JMSException;

    StreamMessage createStreamMessage() throws JMSException;

    TextMessage createTextMessage() throws JMSException;

    TextMessage createTextMessage(String s) throws JMSException;

    AmqpMessage createAmqpMessage() throws JMSException;

    MessageProducer createProducer(Destination destination) throws JMSException;

    MessageConsumer createConsumer(Destination destination) throws JMSException;

    MessageConsumer createConsumer(Destination destination, String s) throws JMSException;

    MessageConsumer createConsumer(Destination destination, String s, boolean b) throws JMSException;

    TopicSubscriber createDurableSubscriber(Topic topic, String s) throws JMSException;

    TopicSubscriber createDurableSubscriber(Topic topic, String s, String s1, boolean b)
            throws JMSException;

    QueueBrowser createBrowser(Queue queue) throws JMSException;

    QueueBrowser createBrowser(Queue queue, String s) throws JMSException;

    TemporaryQueue createTemporaryQueue() throws JMSException;

    TemporaryTopic createTemporaryTopic() throws JMSException;

}
