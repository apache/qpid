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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.exchange.ExchangeReferrer;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.QueueNotificationListener;
import org.apache.qpid.server.protocol.CapacityChecker;
import org.apache.qpid.server.store.MessageEnqueueRecord;
import org.apache.qpid.server.util.Deletable;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public interface AMQQueue<X extends AMQQueue<X>>
        extends Queue<X>, Comparable<AMQQueue>, ExchangeReferrer, BaseQueue, MessageSource, CapacityChecker, MessageDestination,
                Deletable<AMQQueue>
{

    boolean isExclusive();

    void addBinding(BindingImpl binding);

    void removeBinding(BindingImpl binding);

    @Override
    Collection<BindingImpl> getBindings();

    LogSubject getLogSubject();

    VirtualHostImpl getVirtualHost();

    @Override
    public Collection<QueueConsumer<?>> getConsumers();

    boolean isUnused();

    boolean isEmpty();

    long getOldestMessageArrivalTime();

    boolean isDeleted();

    void requeue(QueueEntry entry);

    void dequeue(QueueEntry entry);

    void decrementUnackedMsgCount(QueueEntry queueEntry);

    void incrementUnackedMsgCount(QueueEntry entry);

    boolean resend(final QueueEntry entry, final QueueConsumer<?> consumer);

    List<? extends QueueEntry> getMessagesOnTheQueue();

    List<Long> getMessagesOnTheQueue(int num);

    List<Long> getMessagesOnTheQueue(int num, int offset);

    QueueEntry getMessageOnTheQueue(long messageId);

    /**
     * Returns a list of QueEntries from a given range of queue positions, eg messages 5 to 10 on the queue.
     *
     * The 'queue position' index starts from 1. Using 0 in 'from' will be ignored and continue from 1.
     * Using 0 in the 'to' field will return an empty list regardless of the 'from' value.
     * @param fromPosition
     * @param toPosition
     * @return
     */
    public List<? extends QueueEntry> getMessagesRangeOnTheQueue(final long fromPosition, final long toPosition);

    long clearQueue();

    /**
     * Checks the status of messages on the queue, purging expired ones, firing age related alerts etc.
     */
    void checkMessageStatus();

    Set<NotificationCheck> getNotificationChecks();

    void deliverAsync();

    Collection<String> getAvailableAttributes();

    void setNotificationListener(QueueNotificationListener listener);


    void completeRecovery();

    void recover(ServerMessage<?> message, final MessageEnqueueRecord enqueueRecord);

    void setTargetSize(long targetSize);

    long getPotentialMemoryFootprint();
}
