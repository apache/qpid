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

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.management.Managable;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.AMQException;

import java.util.List;
import java.util.Set;

public interface AMQQueue extends Managable, Comparable<AMQQueue>
{

    AMQShortString getName();

    boolean isDurable();

    boolean isAutoDelete();

    AMQShortString getOwner();

    VirtualHost getVirtualHost();


    void bind(Exchange exchange, AMQShortString routingKey, FieldTable arguments) throws AMQException;

    void unBind(Exchange exchange, AMQShortString routingKey, FieldTable arguments) throws AMQException;

    List<ExchangeBinding> getExchangeBindings();


    void registerSubscription(final Subscription subscription, final boolean exclusive) throws AMQException;

    void unregisterSubscription(final Subscription subscription) throws AMQException;


    int getConsumerCount();

    int getActiveConsumerCount();

    boolean isUnused();

    boolean isEmpty();

    int getMessageCount();

    int getUndeliveredMessageCount();


    long getQueueDepth();

    long getReceivedMessageCount();

    long getOldestMessageArrivalTime();

    boolean isDeleted();


    int delete() throws AMQException;


    QueueEntry enqueue(StoreContext storeContext, AMQMessage message) throws AMQException;

    void requeue(StoreContext storeContext, QueueEntry entry) throws AMQException;

    void dequeue(StoreContext storeContext, QueueEntry entry) throws FailedDequeueException;



    boolean resend(final QueueEntry entry, final Subscription subscription) throws AMQException;

    

    void addQueueDeleteTask(final Task task);


    List<QueueEntry> getMessagesOnTheQueue();

    List<QueueEntry> getMessagesOnTheQueue(long fromMessageId, long toMessageId);

    List<Long> getMessagesOnTheQueue(int num);

    List<Long> getMessagesOnTheQueue(int num, int offest);

    QueueEntry getMessageOnTheQueue(long messageId);


    void moveMessagesToAnotherQueue(long fromMessageId, long toMessageId, String queueName,
                                                        StoreContext storeContext);

    void copyMessagesToAnotherQueue(long fromMessageId, long toMessageId, String queueName, StoreContext storeContext);

    void removeMessagesFromQueue(long fromMessageId, long toMessageId, StoreContext storeContext);



    long getMaximumMessageSize();

    void setMaximumMessageSize(long value);


    long getMaximumMessageCount();

    void setMaximumMessageCount(long value);


    long getMaximumQueueDepth();

    void setMaximumQueueDepth(long value);


    long getMaximumMessageAge();

    void setMaximumMessageAge(final long maximumMessageAge);


    long getMinimumAlertRepeatGap();


    void deleteMessageFromTop(StoreContext storeContext) throws AMQException;

    long clearQueue(StoreContext storeContext) throws AMQException;



    void removeExpiredIfNoSubscribers() throws AMQException;

    Set<NotificationCheck> getNotificationChecks();

    void flushSubscription(final Subscription sub) throws AMQException;

    void deliverAsync(final Subscription sub);

    void deliverAsync();

    void stop();


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
     * ExistingSubscriptionPreventsExclusive signals a failure to create an exclusize subscription, as a subscription
     * already exists.
     *
     * <p/><table id="crc"><caption>CRC Card</caption>
     * <tr><th> Responsibilities <th> Collaborations
     * <tr><td> Represent failure to create an exclusize subscription, as a subscription already exists.
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

    static interface Task
    {
        public void doTask(AMQQueue queue) throws AMQException;
    }

    void configure(Configuration virtualHostDefaultQueueConfiguration);
}
