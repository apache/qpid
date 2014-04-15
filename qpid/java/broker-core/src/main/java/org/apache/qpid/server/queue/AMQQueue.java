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
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.exchange.ExchangeReferrer;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.QueueNotificationListener;
import org.apache.qpid.server.protocol.CapacityChecker;
import org.apache.qpid.server.util.Deletable;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public interface AMQQueue<X extends AMQQueue<X>>
        extends Queue<X>, Comparable<AMQQueue>, ExchangeReferrer, BaseQueue, MessageSource, CapacityChecker, MessageDestination,
                Deletable<AMQQueue>
{

    boolean isExclusive();

    void addBinding(BindingImpl binding);

    void removeBinding(BindingImpl binding);

    Collection<BindingImpl> getBindings();

    int getBindingCount();

    LogSubject getLogSubject();

    long getUnacknowledgedBytes();

    long getTotalDequeuedMessages();

    long getTotalEnqueuedMessages();

    LifetimePolicy getLifetimePolicy();

    String getOwner();

    VirtualHostImpl getVirtualHost();

    public Collection<QueueConsumer<?>> getConsumers();

    int getConsumerCount();

    int getConsumerCountWithCredit();

    boolean hasExclusiveConsumer();

    boolean isUnused();

    boolean isEmpty();

    int getQueueDepthMessages();


    long getQueueDepthBytes();

    long getOldestMessageArrivalTime();

    boolean isDeleted();

    int delete();

    void requeue(QueueEntry entry);

    void dequeue(QueueEntry entry);

    void decrementUnackedMsgCount(QueueEntry queueEntry);

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

    void visit(QueueEntryVisitor visitor);


    long getAlertThresholdMessageSize();

    void setAlertThresholdMessageSize(long value);


    long getAlertThresholdQueueDepthMessages();

    void setAlertThresholdQueueDepthMessages(long value);


    long getAlertThresholdQueueDepthBytes();

    void setAlertThresholdQueueDepthBytes(long value);


    long getAlertThresholdMessageAge();

    void setAlertThresholdMessageAge(final long maximumMessageAge);


    long getAlertRepeatGap();

    void setAlertRepeatGap(long value);


    long getQueueFlowControlSizeBytes();

    void setQueueFlowControlSizeBytes(long capacity);


    long getQueueFlowResumeSizeBytes();

    void setQueueFlowResumeSizeBytes(long flowResumeCapacity);

    boolean isOverfull();

    long clearQueue();

    /**
     * Checks the status of messages on the queue, purging expired ones, firing age related alerts etc.
     */
    void checkMessageStatus();

    Set<NotificationCheck> getNotificationChecks();

    void deliverAsync();

    void stop();

    ExchangeImpl getAlternateExchange();

    void setAlternateExchange(ExchangeImpl exchange);

    Collection<String> getAvailableAttributes();
    Object getAttribute(String attrName);

    /**
     * Gets the maximum delivery count.   If a message on this queue
     * is delivered more than maximumDeliveryCount, the message will be
     * routed to the {@link #getAlternateExchange()} (if set), or otherwise
     * discarded. 0 indicates that maximum deliver count should not be enforced.
     *
     * @return maximum delivery count
     */
    int getMaximumDeliveryAttempts();

    /**
     * Sets the maximum delivery count.
     *
     * @param maximumDeliveryCount maximum delivery count
     */
    public void setMaximumDeliveryAttempts(final int maximumDeliveryCount);

    void setNotificationListener(QueueNotificationListener listener);

    long getPersistentDequeuedBytes();

    long getPersistentDequeuedMessages();

    long getPersistentEnqueuedBytes();

    long getPersistentEnqueuedMessages();

    long getTotalDequeuedBytes();

    long getTotalEnqueuedBytes();

    long getUnacknowledgedMessages();

}
