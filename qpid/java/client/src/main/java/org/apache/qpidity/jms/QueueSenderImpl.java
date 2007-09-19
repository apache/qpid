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
package org.apache.qpidity.njms;

import javax.jms.QueueSender;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Message;

/**
 * Implements javax.njms.QueueSender
 */
public class QueueSenderImpl extends MessageProducerImpl implements QueueSender
{
    //--- Constructor
    /**
     * Create a new QueueSenderImpl.
     *
     * @param session the session from which the QueueSenderImpl is instantiated
     * @param queue   the default queue for this QueueSenderImpl
     * @throws JMSException If the QueueSenderImpl cannot be created due to some internal error.
     */
    protected QueueSenderImpl(SessionImpl session, QueueImpl queue) throws JMSException
    {
        super(session, queue);
    }

    //--- Interface javax.njms.QueueSender
    /**
     * Get the queue associated with this QueueSender.
     *
     * @return This QueueSender's queue
     * @throws JMSException If getting the queue for this QueueSender fails due to some internal error.
     */
    public Queue getQueue() throws JMSException
    {
        return (Queue) getDestination();
    }

    /**
     * Sends a message to the queue. Uses the <CODE>QueueSender</CODE>'s default delivery mode, priority,
     * and time to live.
     *
     * @param message The message to send.
     * @throws JMSException if sending the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If an invalid message is specified.
     * @throws javax.jms.InvalidDestinationException
     *                      If the queue is invalid.
     * @throws java.lang.UnsupportedOperationException
     *                      If invoked on QueueSender that did not specify a queue at creation time.
     */
    public void send(Message message) throws JMSException
    {
        super.send(message);
    }

    /**
     * Send a message to the queue, specifying delivery mode, priority, and time to live.
     *
     * @param message      The message to send
     * @param deliveryMode The delivery mode to use
     * @param priority     The priority for this message
     * @param timeToLive   The message's lifetime (in milliseconds)
     *  
     * @throws JMSException if sending the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If an invalid message is specified.
     * @throws javax.jms.InvalidDestinationException
     *                      If the queue is invalid.
     * @throws java.lang.UnsupportedOperationException
     *                      If invoked on QueueSender that did not specify a queue at creation time.
     */
    public void send(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException
    {
        super.send(message, deliveryMode, priority, timeToLive);
    }

    /**
     * Send a message to a queue for an unidentified message producer.
     * Uses the <CODE>QueueSender</CODE>'s default delivery mode, priority,
     * and time to live.
     *
     * @param queue   The queue to send this message to
     * @param message The message to send
     * @throws JMSException if sending the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If an invalid message is specified.
     * @throws javax.jms.InvalidDestinationException
     *                      If the queue is invalid.
     */
    public void send(Queue queue, Message message) throws JMSException
    {
        super.send(queue, message);
    }

    /**
     * Sends a message to a queue for an unidentified message producer,
     * specifying delivery mode, priority and time to live.
     *
     * @param queue        The queue to send this message to
     * @param message      The message to send
     * @param deliveryMode The delivery mode to use
     * @param priority     The priority for this message
     * @param timeToLive   The message's lifetime (in milliseconds)
     * @throws JMSException if sending the message fails due to some internal error.
     * @throws javax.jms.MessageFormatException
     *                      If an invalid message is specified.
     * @throws javax.jms.InvalidDestinationException
     *                      If the queue is invalid.
     */
    public void send(Queue queue, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException
    {
        super.send(queue, message, deliveryMode, priority, timeToLive);
    }
}
