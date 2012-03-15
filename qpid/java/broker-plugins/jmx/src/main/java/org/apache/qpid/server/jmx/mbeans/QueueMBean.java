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

package org.apache.qpid.server.jmx.mbeans;

import java.io.IOException;
import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;

public class QueueMBean extends AMQManagedObject implements ManagedQueue
{
    private final Queue _queue;
    private final VirtualHostMBean _vhostMBean;

    public QueueMBean(Queue queue, VirtualHostMBean virtualHostMBean) throws JMException
    {
        super(ManagedQueue.class, ManagedQueue.TYPE, virtualHostMBean.getRegistry());
        _queue = queue;
        _vhostMBean = virtualHostMBean;
        register();
    }

    public ManagedObject getParentObject()
    {
        return _vhostMBean;
    }

    public String getObjectInstanceName()
    {
        return ObjectName.quote(getName());
    }

    public String getName()
    {
        return _queue.getName();
    }

    public Integer getMessageCount()
    {
        return getStatisticValue(Queue.QUEUE_DEPTH_MESSAGES).intValue();
    }

    private Number getStatisticValue(String name)
    {
        final Number statistic = _queue.getStatistics().getStatistic(name);
        return statistic == null ? Integer.valueOf(0) : statistic;
    }

    public Integer getMaximumDeliveryCount()
    {
        return null;  // TODO
    }

    public Long getReceivedMessageCount()
    {
        return getStatisticValue(Queue.TOTAL_ENQUEUED_MESSAGES).longValue();
    }

    public Long getQueueDepth()
    {
        return getStatisticValue(Queue.QUEUE_DEPTH_BYTES).longValue();
    }

    public Integer getActiveConsumerCount()
    {
        return getStatisticValue(Queue.CONSUMER_COUNT_WITH_CREDIT).intValue();
    }

    public Integer getConsumerCount()
    {
        return getStatisticValue(Queue.CONSUMER_COUNT).intValue();
    }

    public String getOwner()
    {
        return (String) _queue.getAttribute(Queue.OWNER);
    }

    public boolean isDurable()
    {
        return _queue.isDurable();
    }

    public boolean isAutoDelete()
    {
        return _queue.getLifetimePolicy() == LifetimePolicy.AUTO_DELETE;
    }

    public Long getMaximumMessageAge()
    {
        return (Long) _queue.getAttribute(Queue.ALERT_THRESHOLD_MESSAGE_AGE);
    }

    public void setMaximumMessageAge(Long age)
    {
        _queue.setAttribute(Queue.ALERT_THRESHOLD_MESSAGE_AGE, getMaximumMessageAge(), age);
    }

    public Long getMaximumMessageSize()
    {
        return (Long) _queue.getAttribute(Queue.ALERT_THRESHOLD_MESSAGE_SIZE);
    }

    public void setMaximumMessageSize(Long size)
    {
        _queue.setAttribute(Queue.ALERT_THRESHOLD_MESSAGE_SIZE, getMaximumMessageSize(), size);
    }

    public Long getMaximumMessageCount()
    {
        return (Long) _queue.getAttribute(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES);
    }

    public void setMaximumMessageCount(Long value)
    {
        _queue.setAttribute(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, getMaximumMessageCount(), value);
    }

    public Long getMaximumQueueDepth()
    {
        return (Long) _queue.getAttribute(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_BYTES);
    }

    public void setMaximumQueueDepth(Long value)
    {
        _queue.setAttribute(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, getMaximumQueueDepth(), value);
    }

    public Long getCapacity()
    {
        return (Long) _queue.getAttribute(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES);
    }

    public void setCapacity(Long value)
    {
        _queue.setAttribute(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES, getCapacity(), value);
    }

    public Long getFlowResumeCapacity()
    {
        return (Long) _queue.getAttribute(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES);
    }

    public void setFlowResumeCapacity(Long value)
    {
        _queue.setAttribute(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES, getFlowResumeCapacity(), value);
    }

    public boolean isFlowOverfull()
    {
        return false;  // TODO
    }

    public boolean isExclusive()
    {
        return (Boolean) _queue.getAttribute(Queue.EXCLUSIVE);
    }

    public void setExclusive(boolean exclusive)
    {
        _queue.setAttribute(Queue.EXCLUSIVE, isExclusive(), exclusive);
    }

    public void setAlternateExchange(String exchangeName)
    {
        // TODO
    }

    public String getAlternateExchange()
    {
        return null;  // TODO
    }

    public TabularData viewMessages(int fromIndex, int toIndex)
            throws IOException, JMException
    {
        return viewMessages((long)fromIndex, (long)toIndex);
    }

    public TabularData viewMessages(long startPosition, long endPosition)
            throws IOException, JMException
    {
        return null;  // TODO
    }

    public CompositeData viewMessageContent(long messageId)
            throws IOException, JMException
    {
        return null;  // TODO
    }

    public void deleteMessageFromTop() throws IOException, JMException
    {
        // TODO
    }

    public Long clearQueue() throws IOException, JMException
    {
        return null;  // TODO
    }

    public void moveMessages(long fromMessageId, long toMessageId, String toQueue)
            throws IOException, JMException
    {
        // TODO
    }

    public void deleteMessages(long fromMessageId, long toMessageId)
            throws IOException, JMException
    {
        // TODO
    }

    public void copyMessages(long fromMessageId, long toMessageId, String toQueue)
            throws IOException, JMException
    {
        // TODO
    }
}
