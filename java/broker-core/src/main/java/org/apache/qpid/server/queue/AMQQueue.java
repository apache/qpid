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
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeReferrer;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.security.AuthorizationHolder;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionTarget;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public interface AMQQueue extends Comparable<AMQQueue>, ExchangeReferrer, TransactionLogResource, BaseQueue
{
    String getName();

    public interface NotificationListener
    {
        void notifyClients(NotificationCheck notification, AMQQueue queue, String notificationMsg);
    }

    boolean getDeleteOnNoConsumers();

    void setDeleteOnNoConsumers(boolean b);

    void addBinding(Binding binding);

    void removeBinding(Binding binding);

    List<Binding> getBindings();

    int getBindingCount();

    LogSubject getLogSubject();

    long getUnackedMessageBytes();

    long getTotalDequeueCount();

    long getTotalEnqueueCount();

    public interface Context
    {
        QueueEntry getLastSeenEntry();
    }

    void setNoLocal(boolean b);

    boolean isAutoDelete();

    String getOwner();
    AuthorizationHolder getAuthorizationHolder();
    void setAuthorizationHolder(AuthorizationHolder principalHolder);

    void setExclusiveOwningSession(AMQSessionModel owner);
    AMQSessionModel getExclusiveOwningSession();

    VirtualHost getVirtualHost();

    Subscription registerSubscription(final SubscriptionTarget target, final FilterManager filters,
                                      final Class<? extends ServerMessage> messageClass,
                                      final String consumerName, EnumSet<Subscription.Option> options) throws AMQException;

    void unregisterSubscription(final Subscription subscription) throws AMQException;

    Collection<Subscription> getConsumers();

    interface SubscriptionRegistrationListener
    {
        void subscriptionRegistered(AMQQueue queue, Subscription subscription);
        void subscriptionUnregistered(AMQQueue queue, Subscription subscription);
    }

    void addSubscriptionRegistrationListener(SubscriptionRegistrationListener listener);
    void removeSubscriptionRegistrationListener(SubscriptionRegistrationListener listener);


    int getConsumerCount();

    int getActiveConsumerCount();

    boolean hasExclusiveSubscriber();

    boolean isUnused();

    boolean isEmpty();

    int getMessageCount();

    int getUndeliveredMessageCount();


    long getQueueDepth();

    long getReceivedMessageCount();

    long getOldestMessageArrivalTime();

    boolean isDeleted();

    int delete() throws AMQException;

    void requeue(QueueEntry entry);

    void dequeue(QueueEntry entry, Subscription sub);

    void decrementUnackedMsgCount(QueueEntry queueEntry);

    boolean resend(final QueueEntry entry, final Subscription subscription) throws AMQException;

    void addQueueDeleteTask(Action<AMQQueue> task);
    void removeQueueDeleteTask(Action<AMQQueue> task);



    List<QueueEntry> getMessagesOnTheQueue();

    List<QueueEntry> getMessagesOnTheQueue(long fromMessageId, long toMessageId);

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
    public List<QueueEntry> getMessagesRangeOnTheQueue(final long fromPosition, final long toPosition);

    void visit(QueueEntryVisitor visitor);


    long getMaximumMessageSize();

    void setMaximumMessageSize(long value);


    long getMaximumMessageCount();

    void setMaximumMessageCount(long value);


    long getMaximumQueueDepth();

    void setMaximumQueueDepth(long value);


    long getMaximumMessageAge();

    void setMaximumMessageAge(final long maximumMessageAge);


    long getMinimumAlertRepeatGap();

    void setMinimumAlertRepeatGap(long value);


    long getCapacity();

    void setCapacity(long capacity);


    long getFlowResumeCapacity();

    void setFlowResumeCapacity(long flowResumeCapacity);

    boolean isOverfull();

    void deleteMessageFromTop();

    long clearQueue() throws AMQException;

    /**
     * Checks the status of messages on the queue, purging expired ones, firing age related alerts etc.
     * @throws AMQException
     */
    void checkMessageStatus() throws AMQException;

    Set<NotificationCheck> getNotificationChecks();

    void flushSubscription(final Subscription sub) throws AMQException;

    void deliverAsync();

    void stop();

    boolean isExclusive();

    Exchange getAlternateExchange();

    void setAlternateExchange(Exchange exchange);

    Collection<String> getAvailableAttributes();
    Object getAttribute(String attrName);

    void checkCapacity(AMQSessionModel channel);

    /**
     * ExistingExclusiveSubscription signals a failure to create a subscription, because an exclusive subscription
     * already exists.
     *
     * <p/><table id="crc"><caption>CRC Card</caption>
     * <tr><th> Responsibilities <th> Collaborations
     * <tr><td> Represent failure to create a subscription, because an exclusive subscription already exists.
     * </table>
     *
     * @todo Not an AMQP exception as no status code.
     *
     * @todo Move to top level, used outside this class.
     */
    static final class ExistingExclusiveSubscription extends AMQException
    {

        public ExistingExclusiveSubscription()
        {
            super("");
        }
    }

    /**
     * ExistingSubscriptionPreventsExclusive signals a failure to create an exclusive subscription, as a subscription
     * already exists.
     *
     * <p/><table id="crc"><caption>CRC Card</caption>
     * <tr><th> Responsibilities <th> Collaborations
     * <tr><td> Represent failure to create an exclusive subscription, as a subscription already exists.
     * </table>
     *
     * @todo Not an AMQP exception as no status code.
     *
     * @todo Move to top level, used outside this class.
     */
    static final class ExistingSubscriptionPreventsExclusive extends AMQException
    {
        public ExistingSubscriptionPreventsExclusive()
        {
            super("");
        }
    }

    void configure(QueueConfiguration config);

    void setExclusive(boolean exclusive);

    /**
     * Gets the maximum delivery count.   If a message on this queue
     * is delivered more than maximumDeliveryCount, the message will be
     * routed to the {@link #getAlternateExchange()} (if set), or otherwise
     * discarded. 0 indicates that maximum deliver count should not be enforced.
     *
     * @return maximum delivery count
     */
    int getMaximumDeliveryCount();

    /**
     * Sets the maximum delivery count.
     *
     * @param maximumDeliveryCount maximum delivery count
     */
    public void setMaximumDeliveryCount(final int maximumDeliveryCount);

    void setNotificationListener(NotificationListener listener);

    /**
     * Sets the free text description of this queue.
     *
     * @param description
     *
     */
    void setDescription(String description);

    /**
     * Gets the free text description of this queue.
     */
    String getDescription();

    long getPersistentByteDequeues();

    long getPersistentMsgDequeues();

    long getPersistentByteEnqueues();

    long getPersistentMsgEnqueues();

    long getTotalDequeueSize();

    long getTotalEnqueueSize();

    long getUnackedMessageCount();

}
