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
package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;

import java.util.concurrent.Executor;
import java.util.List;

interface DeliveryManager
{
    /**
     * Determines whether there are queued messages. Sets _queueing to false if
     * there are no queued messages. This needs to be atomic.
     *
     * @return true if there are queued messages
     */
    boolean hasQueuedMessages();

    /**
     * This method should not be used to determin if there are messages in the queue.
     *
     * @return int The number of messages in the queue
     * @use hasQueuedMessages() for all controls relating to having messages on the queue.
     */
    int getQueueMessageCount();

    /**
     * Requests that the delivery manager start processing the queue asynchronously
     * if there is work that can be done (i.e. there are messages queued up and
     * subscribers that can receive them.
     * <p/>
     * This should be called when subscribers are added, but only after the consume-ok
     * message has been returned as message delivery may start immediately. It should also
     * be called after unsuspending a client.
     * <p/>
     *
     * @param executor the executor on which the delivery should take place
     */
    void processAsync(Executor executor);

    /**
     * Handles message delivery. The delivery manager is always in one of two modes;
     * it is either queueing messages for asynchronous delivery or delivering
     * directly.
     *
     * @param name the name of the entity on whose behalf we are delivering the message
     * @param msg  the message to deliver
     * @throws org.apache.qpid.server.queue.FailedDequeueException if the message could not be dequeued
     */
    void deliver(String name, AMQMessage msg) throws FailedDequeueException;

    void removeAMessageFromTop() throws AMQException;

    void clearAllMessages() throws AMQException;

    List<AMQMessage> getMessages();

    void populatePreDeliveryQueue(Subscription subscription);
}
