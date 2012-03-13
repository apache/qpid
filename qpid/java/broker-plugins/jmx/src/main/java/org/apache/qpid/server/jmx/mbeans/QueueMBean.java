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

import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;
import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;

import javax.management.JMException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.io.IOException;

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

    public Integer getMessageCount() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Integer getMaximumDeliveryCount() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Long getReceivedMessageCount() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Long getQueueDepth() throws IOException, JMException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Integer getActiveConsumerCount() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Integer getConsumerCount() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getOwner() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isDurable() throws IOException
    {
        return _queue.isDurable();
    }

    public boolean isAutoDelete() throws IOException
    {
        return _queue.getLifetimePolicy() == LifetimePolicy.AUTO_DELETE;
    }

    public Long getMaximumMessageAge() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setMaximumMessageAge(Long age) throws IOException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Long getMaximumMessageSize() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setMaximumMessageSize(Long size) throws IOException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Long getMaximumMessageCount() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setMaximumMessageCount(Long value) throws IOException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Long getMaximumQueueDepth() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setMaximumQueueDepth(Long value) throws IOException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Long getCapacity() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setCapacity(Long value) throws IOException, IllegalArgumentException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Long getFlowResumeCapacity() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setFlowResumeCapacity(Long value) throws IOException, IllegalArgumentException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isFlowOverfull() throws IOException
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isExclusive() throws IOException
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setExclusive(boolean exclusive) throws IOException, JMException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setAlternateExchange(String exchangeName) throws IOException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getAlternateExchange() throws IOException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public TabularData viewMessages(
            @MBeanOperationParameter(name = "from index", description = "from index") int fromIndex,
            @MBeanOperationParameter(name = "to index", description = "to index") int toIndex)
            throws IOException, JMException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public TabularData viewMessages(
            @MBeanOperationParameter(name = "start position", description = "start position") long startPosition,
            @MBeanOperationParameter(name = "end position", description = "end position") long endPosition)
            throws IOException, JMException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public CompositeData viewMessageContent(
            @MBeanOperationParameter(name = "Message Id", description = "Message Id") long messageId)
            throws IOException, JMException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void deleteMessageFromTop() throws IOException, JMException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Long clearQueue() throws IOException, JMException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void moveMessages(
            @MBeanOperationParameter(name = "from MessageId", description = "from MessageId") long fromMessageId,
            @MBeanOperationParameter(name = "to MessageId", description = "to MessageId") long toMessageId,
            @MBeanOperationParameter(name = ManagedQueue.TYPE, description = "to Queue Name") String toQueue)
            throws IOException, JMException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void deleteMessages(
            @MBeanOperationParameter(name = "from MessageId", description = "from MessageId") long fromMessageId,
            @MBeanOperationParameter(name = "to MessageId", description = "to MessageId") long toMessageId)
            throws IOException, JMException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void copyMessages(
            @MBeanOperationParameter(name = "from MessageId", description = "from MessageId") long fromMessageId,
            @MBeanOperationParameter(name = "to MessageId", description = "to MessageId") long toMessageId,
            @MBeanOperationParameter(name = ManagedQueue.TYPE, description = "to Queue Name") String toQueue)
            throws IOException, JMException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
