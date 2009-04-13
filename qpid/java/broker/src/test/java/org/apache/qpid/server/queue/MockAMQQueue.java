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

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.AMQException;
import org.apache.commons.configuration.Configuration;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;

public class MockAMQQueue implements AMQQueue
{
    private boolean _deleted = false;
    private AMQShortString _name;

    public MockAMQQueue(String name)
    {
       _name = new AMQShortString(name);
    }

    public MockAMQQueue()
    {
       
    }

    public AMQShortString getName()
    {
        return _name;
    }

    public boolean isDurable()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isAutoDelete()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public AMQShortString getOwner()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public VirtualHost getVirtualHost()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void bind(Exchange exchange, AMQShortString routingKey, FieldTable arguments) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void unBind(Exchange exchange, AMQShortString routingKey, FieldTable arguments) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<ExchangeBinding> getExchangeBindings()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void registerSubscription(Subscription subscription, boolean exclusive) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void unregisterSubscription(Subscription subscription) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getConsumerCount()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getActiveConsumerCount()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isUnused()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isEmpty()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getMessageCount()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getUndeliveredMessageCount()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getQueueDepth()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getReceivedMessageCount()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getOldestMessageArrivalTime()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isDeleted()
    {
        return _deleted;
    }

    public int delete() throws AMQException
    {
       return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public QueueEntry enqueue(StoreContext storeContext, AMQMessage message) throws AMQException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void requeue(StoreContext storeContext, QueueEntry entry) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void dequeue(StoreContext storeContext, QueueEntry entry) throws FailedDequeueException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean resend(QueueEntry entry, Subscription subscription) throws AMQException
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void addQueueDeleteTask(Task task)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<QueueEntry> getMessagesOnTheQueue()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<QueueEntry> getMessagesOnTheQueue(long fromMessageId, long toMessageId)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Long> getMessagesOnTheQueue(int num)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Long> getMessagesOnTheQueue(int num, int offest)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public QueueEntry getMessageOnTheQueue(long messageId)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void moveMessagesToAnotherQueue(long fromMessageId, long toMessageId, String queueName, StoreContext storeContext)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void copyMessagesToAnotherQueue(long fromMessageId, long toMessageId, String queueName, StoreContext storeContext)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void removeMessagesFromQueue(long fromMessageId, long toMessageId, StoreContext storeContext)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getMaximumMessageSize()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setMaximumMessageSize(long value)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getMaximumMessageCount()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setMaximumMessageCount(long value)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getMaximumQueueDepth()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setMaximumQueueDepth(long value)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getMaximumMessageAge()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setMaximumMessageAge(long maximumMessageAge)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getMinimumAlertRepeatGap()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void deleteMessageFromTop(StoreContext storeContext) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public long clearQueue(StoreContext storeContext) throws AMQException
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void checkMessageStatus() throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Set<NotificationCheck> getNotificationChecks()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void flushSubscription(Subscription sub) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void deliverAsync(Subscription sub)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void deliverAsync()
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void stop()
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void configure(Configuration virtualHostDefaultQueueConfiguration)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public ManagedObject getManagedObject()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public int compareTo(AMQQueue o)
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
