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
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.security.AuthorizationHolder;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockAMQQueue implements AMQQueue
{
    private boolean _deleted = false;
    private String _name;
    private VirtualHost _virtualhost;

    private AuthorizationHolder _authorizationHolder;

    private AMQSessionModel _exclusiveOwner;
    private List<Binding> _bindings = new CopyOnWriteArrayList<Binding>();
    private boolean _autoDelete;

    public MockAMQQueue(String name)
    {
       _name = name;
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

    public long getUnackedMessageBytes()
    {
        return 0;
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

    public long getTotalDequeueCount()
    {
        return 0;
    }

    public long getTotalEnqueueCount()
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

    public void setNoLocal(boolean b)
    {

    }

    public UUID getId()
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


    public String getOwner()
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

    @Override
    public boolean resend(final QueueEntry entry, final Consumer consumer) throws AMQException
    {
        return false;
    }

    @Override
    public void addQueueDeleteTask(final Action task)
    {

    }

    @Override
    public void enqueue(final ServerMessage message, final Action action) throws AMQException
    {

    }

    @Override
    public int compareTo(final Object o)
    {
        return 0;
    }

    @Override
    public Consumer addConsumer(final ConsumerTarget target,
                                final FilterManager filters,
                                final Class messageClass,
                                final String consumerName,
                                final EnumSet options) throws AMQException
    {
        return new QueueConsumer(filters, messageClass, options.contains(Consumer.Option.ACQUIRES),
                                 options.contains(Consumer.Option.SEES_REQUEUES), consumerName,
                                 options.contains(Consumer.Option.TRANSIENT), target );
    }


    public String getName()
    {
        return _name;
    }

    public  int send(final ServerMessage message,
                     final InstanceProperties instanceProperties,
                     final ServerTransaction txn,
                     final Action postEnqueueAction)
    {
        return 0;
    }

    public Collection<QueueConsumer> getConsumers()
    {
        return Collections.emptyList();
    }

    public void addConsumerRegistrationListener(final ConsumerRegistrationListener listener)
    {

    }

    public void removeConsumerRegistrationListener(final ConsumerRegistrationListener listener)
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

    public boolean hasExclusiveConsumer()
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


    public void requeue(QueueEntry entry)
    {
    }

    public void dequeue(QueueEntry entry)
    {
    }

    public boolean resend(QueueEntry entry, QueueConsumer consumer) throws AMQException
    {
        return false;
    }

    @Override
    public void removeQueueDeleteTask(final Action task)
    {

    }

    @Override
    public void decrementUnackedMsgCount(final QueueEntry queueEntry)
    {

    }

    @Override
    public List getMessagesOnTheQueue()
    {
        return null;
    }

    public List getMessagesOnTheQueue(long fromMessageId, long toMessageId)
    {
        return null;
    }

    public List<Long> getMessagesOnTheQueue(int num)
    {
        return null;
    }

    public List<Long> getMessagesOnTheQueue(int num, int offset)
    {
        return null;
    }

    public QueueEntry getMessageOnTheQueue(long messageId)
    {
        return null;
    }

    public List getMessagesRangeOnTheQueue(long fromPosition, long toPosition)
    {
        return null;
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

    public void flushConsumer(Consumer sub) throws AMQException
    {

    }

    public void deliverAsync(Consumer sub)
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

    @Override
    public Collection<String> getAvailableAttributes()
    {
        return null;
    }

    @Override
    public Object getAttribute(String attrName)
    {
        return null;
    }

    public void checkCapacity(AMQSessionModel channel)
    {
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

    public void configure(QueueConfiguration config)
    {

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

    public void visit(final QueueEntryVisitor visitor)
    {
    }

    @Override
    public void setNotificationListener(NotificationListener listener)
    {
    }

    @Override
    public void setDescription(String description)
    {
    }

    @Override
    public String getDescription()
    {
        return null;
    }

}
