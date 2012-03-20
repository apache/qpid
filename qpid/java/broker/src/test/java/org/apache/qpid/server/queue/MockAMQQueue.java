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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ConfiguredObject;
import org.apache.qpid.server.configuration.QueueConfigType;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.security.AuthorizationHolder;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockAMQQueue implements AMQQueue
{
    private boolean _deleted = false;
    private AMQShortString _name;
    private VirtualHost _virtualhost;

    private AuthorizationHolder _authorizationHolder;

    private AMQSessionModel _exclusiveOwner;
    private AMQShortString _owner;
    private List<Binding> _bindings = new CopyOnWriteArrayList<Binding>();
    private boolean _autoDelete;

    public MockAMQQueue(String name)
    {
       _name = new AMQShortString(name);
    }

    public boolean getDeleteOnNoConsumers()
    {
        return false;
    }

    public void setDeleteOnNoConsumers(boolean b)
    {
    }

    public void addBinding(final Binding binding)
    {
        _bindings.add(binding);
    }

    public void removeBinding(final Binding binding)
    {
        _bindings.remove(binding);
    }

    public List<Binding> getBindings()
    {
        return _bindings;
    }

    public int getBindingCount()
    {
        return 0;
    }

    public LogSubject getLogSubject()
    {
       return new LogSubject()
        {
            public String toLogString()
            {
                return "[MockAMQQueue]";
            }

        };
    }

    public ConfigStore getConfigStore()
    {
        return getVirtualHost().getConfigStore();
    }

    public long getMessageDequeueCount()
    {
        return 0;
    }

    public long getTotalEnqueueSize()
    {
        return 0;
    }

    public long getTotalDequeueSize()
    {
        return 0;
    }

    public int getBindingCountHigh()
    {
        return 0;
    }

    public long getPersistentByteEnqueues()
    {
        return 0;
    }

    public long getPersistentByteDequeues()
    {
        return 0;
    }

    public long getPersistentMsgEnqueues()
    {
        return 0;
    }

    public long getPersistentMsgDequeues()
    {
        return 0;
    }

    public void purge(final long request)
    {

    }

    public long getCreateTime()
    {
        return 0;
    }

    public AMQShortString getNameShortString()
    {
        return _name;
    }

    public void setNoLocal(boolean b)
    {

    }

    public UUID getId()
    {
        return null;
    }

    public QueueConfigType getConfigType()
    {
        return null;
    }

    public ConfiguredObject getParent()
    {
        return null;
    }

    public boolean isDurable()
    {
        return false;
    }

    public boolean isAutoDelete()
    {
        return _autoDelete;
    }

    public void setAutoDelete(boolean autodelete)
    {
        _autoDelete = autodelete;
    }


    public AMQShortString getOwner()
    {
        return null;
    }

    public void setVirtualHost(VirtualHost virtualhost)
    {
        _virtualhost = virtualhost;
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualhost;
    }

    public String getName()
    {
        return _name.asString();
    }

    public void registerSubscription(Subscription subscription, boolean exclusive) throws AMQException
    {

    }

    public void unregisterSubscription(Subscription subscription) throws AMQException
    {

    }

    public Collection<Subscription> getConsumers()
    {
        return Collections.emptyList();
    }

    public void addSubscriptionRegistrationListener(final SubscriptionRegistrationListener listener)
    {

    }

    public void removeSubscriptionRegistrationListener(final SubscriptionRegistrationListener listener)
    {

    }

    public int getConsumerCount()
    {
        return 0;
    }

    public int getActiveConsumerCount()
    {
        return 0;
    }

    public boolean hasExclusiveSubscriber()
    {
        return false;
    }

    public boolean isUnused()
    {
        return false;
    }

    public boolean isEmpty()
    {
        return false;
    }

    public int getMessageCount()
    {
        return 0;
    }

    public int getUndeliveredMessageCount()
    {
        return 0;
    }

    public long getQueueDepth()
    {
        return 0;
    }

    public long getReceivedMessageCount()
    {
        return 0;
    }

    public long getOldestMessageArrivalTime()
    {
        return 0;
    }

    public boolean isDeleted()
    {
        return _deleted;
    }

    public int delete() throws AMQException
    {
       _deleted = true;
       return getMessageCount();
    }

    public void enqueue(ServerMessage message) throws AMQException
    {
    }

    public void enqueue(ServerMessage message, PostEnqueueAction action) throws AMQException
    {
    }


    public void enqueue(ServerMessage message, boolean sync, PostEnqueueAction action) throws AMQException
    {
    }

    public void requeue(QueueEntry entry)
    {
    }

    public void requeue(QueueEntryImpl storeContext, Subscription subscription)
    {
    }

    public void dequeue(QueueEntry entry, Subscription sub)
    {
    }

    public boolean resend(QueueEntry entry, Subscription subscription) throws AMQException
    {
        return false;
    }

    public void addQueueDeleteTask(Task task)
    {
    }

    public void removeQueueDeleteTask(final Task task)
    {
    }

    public List<QueueEntry> getMessagesOnTheQueue()
    {
        return null;
    }

    public List<QueueEntry> getMessagesOnTheQueue(long fromMessageId, long toMessageId)
    {
        return null;
    }

    public List<Long> getMessagesOnTheQueue(int num)
    {
        return null;
    }

    public List<Long> getMessagesOnTheQueue(int num, int offest)
    {
        return null;
    }

    public QueueEntry getMessageOnTheQueue(long messageId)
    {
        return null;
    }

    public List<QueueEntry> getMessagesRangeOnTheQueue(long fromPosition, long toPosition)
    {
        return null;
    }

    public void moveMessagesToAnotherQueue(long fromMessageId, long toMessageId, String queueName)
    {

    }

    public void copyMessagesToAnotherQueue(long fromMessageId, long toMessageId, String queueName)
    {

    }

    public void removeMessagesFromQueue(long fromMessageId, long toMessageId)
    {

    }

    public long getMaximumMessageSize()
    {
        return 0;
    }

    public void setMaximumMessageSize(long value)
    {

    }

    public long getMaximumMessageCount()
    {
        return 0;
    }

    public void setMaximumMessageCount(long value)
    {

    }

    public long getMaximumQueueDepth()
    {
        return 0;
    }

    public void setMaximumQueueDepth(long value)
    {

    }

    public long getMaximumMessageAge()
    {
        return 0;
    }

    public void setMaximumMessageAge(long maximumMessageAge)
    {

    }

    public long getMinimumAlertRepeatGap()
    {
        return 0;
    }

    public void deleteMessageFromTop()
    {

    }

    public long clearQueue()
    {
        return 0;
    }


    public void checkMessageStatus() throws AMQException
    {

    }

    public Set<NotificationCheck> getNotificationChecks()
    {
        return null;
    }

    public void flushSubscription(Subscription sub) throws AMQException
    {

    }

    public void deliverAsync(Subscription sub)
    {

    }

    public void deliverAsync()
    {

    }

    public void stop()
    {

    }

    public boolean isExclusive()
    {
        return false;
    }

    public Exchange getAlternateExchange()
    {
        return null;
    }

    public void setAlternateExchange(Exchange exchange)
    {

    }

    public Map<String, Object> getArguments()
    {
        return null;
    }

    public void checkCapacity(AMQSessionModel channel)
    {
    }

    public ManagedObject getManagedObject()
    {
        return null;
    }

    public int compareTo(AMQQueue o)
    {
        return 0;
    }

    public void setMinimumAlertRepeatGap(long value)
    {

    }

    public long getCapacity()
    {
        return 0;
    }

    public void setCapacity(long capacity)
    {

    }

    public long getFlowResumeCapacity()
    {
        return 0;
    }

    public void setFlowResumeCapacity(long flowResumeCapacity)
    {

    }

    public void configure(ConfigurationPlugin config)
    {

    }

    public ConfigurationPlugin getConfiguration()
    {
        return null;
    }

    public AuthorizationHolder getAuthorizationHolder()
    {
        return _authorizationHolder;
    }

    public void setAuthorizationHolder(final AuthorizationHolder authorizationHolder)
    {
        _authorizationHolder = authorizationHolder;
    }

    public AMQSessionModel getExclusiveOwningSession()
    {
        return _exclusiveOwner;
    }

    public void setExclusiveOwningSession(AMQSessionModel exclusiveOwner)
    {
        _exclusiveOwner = exclusiveOwner;
    }


    public String getResourceName()
    {
        return _name.toString();
    }

    public boolean isOverfull()
    {
        return false;
    }

    public int getConsumerCountHigh()
    {
        return 0;
    }

    public long getByteTxnEnqueues()
    {
        return 0;
    }

    public long getMsgTxnEnqueues()
    {
        return 0;
    }

    public long getByteTxnDequeues()
    {
        return 0;
    }

    public long getMsgTxnDequeues()
    {
        return 0;
    }

    public void decrementUnackedMsgCount()
    {

    }

    public long getUnackedMessageCount()
    {
        return 0;
    }

    public long getUnackedMessageCountHigh()
    {
        return 0;
    }

    public void setExclusive(boolean exclusive)
    {

    }

    public int getMaximumDeliveryCount()
    {
        return 0;
    }

    public void setMaximumDeliveryCount(int maximumDeliveryCount)
    {
    }

    public void setAlternateExchange(String exchangeName)
    {
    }

    public void visit(final QueueEntryVisitor visitor)
    {
    }
}
