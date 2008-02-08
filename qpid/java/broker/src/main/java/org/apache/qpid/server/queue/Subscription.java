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

import java.util.Queue;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.AMQChannel;

public interface Subscription
{
    void send(QueueEntry msg, AMQQueue queue) throws AMQException;

    boolean isSuspended();

    void queueDeleted(AMQQueue queue) throws AMQException;

    boolean filtersMessages();

    boolean hasInterest(QueueEntry msg);

    Queue<QueueEntry> getPreDeliveryQueue();

    Queue<QueueEntry> getResendQueue();

    Queue<QueueEntry> getNextQueue(Queue<QueueEntry> messages);

    void enqueueForPreDelivery(QueueEntry msg, boolean deliverFirst);

    boolean isAutoClose();

    void close();

    boolean isClosed();

    boolean isBrowser();

    boolean wouldSuspend(QueueEntry msg);

    void addToResendQueue(QueueEntry msg);

    Object getSendLock();

    AMQChannel getChannel();
}
