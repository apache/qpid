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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.management.common.mbeans.annotations.MBeanConstructor;
import org.apache.qpid.management.common.mbeans.annotations.MBeanDescription;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;

@MBeanDescription("This MBean exposes the broker level management features")
public class VirtualHostManagerMBean extends AbstractStatisticsGatheringMBean<VirtualHost> implements ManagedBroker
{

    private final VirtualHostMBean _virtualHostMBean;

    @MBeanConstructor("Creates the Broker Manager MBean")
    public VirtualHostManagerMBean(VirtualHostMBean virtualHostMBean) throws JMException
    {
        super(ManagedBroker.class, ManagedBroker.TYPE, virtualHostMBean.getRegistry(), virtualHostMBean.getVirtualHost());
        _virtualHostMBean = virtualHostMBean;
        register();
    }


    public String getObjectInstanceName()
    {
        return ObjectName.quote(_virtualHostMBean.getName());
    }

    @Override
    public ManagedObject getParentObject()
    {
        return _virtualHostMBean;
    }

    public String[] getExchangeTypes() throws IOException
    {
        Collection<String> exchangeTypes = _virtualHostMBean.getVirtualHost().getExchangeTypes();
        return exchangeTypes.toArray(new String[exchangeTypes.size()]);
    }

    public List<String> retrieveQueueAttributeNames() throws IOException
    {
        return ManagedQueue.QUEUE_ATTRIBUTES;
    }

    public List<List<Object>> retrieveQueueAttributeValues(
            @MBeanOperationParameter(name = "attributes", description = "Attributes to retrieve") String[] attributes)
            throws IOException
    {
        int attributesLength = attributes.length;

        List<List<Object>> queueAttributesList = new ArrayList<List<Object>>();

        for(QueueMBean queue : _virtualHostMBean.getQueues())
        {

            if(queue == null)
            {
                continue;
            }

            List<Object> attributeValues = new ArrayList<Object>(attributesLength);

            for(int i=0; i < attributesLength; i++)
            {
                try
                {
                    attributeValues.add(queue.getAttribute(attributes[i]));
                }
                catch (Exception e)
                {
                    attributeValues.add("-");
                }
            }

            queueAttributesList.add(attributeValues);
        }

        return queueAttributesList;

    }

    public void createNewExchange(String name, String type, boolean durable)
            throws IOException, JMException, MBeanException
    {
        getConfiguredObject().createExchange(name, State.ACTIVE, durable,
                                        LifetimePolicy.PERMANENT, 0l, type, Collections.EMPTY_MAP);

    }

    public void unregisterExchange(String exchangeName)
            throws IOException, JMException, MBeanException
    {
        Exchange theExchange = null;
        for(Exchange exchange : _virtualHostMBean.getVirtualHost().getExchanges())
        {
            if(exchange.getName().equals(exchangeName))
            {
                theExchange = exchange;
                break;
            }
        }
        if(theExchange != null)
        {
            theExchange.delete();
        }
    }

    public void createNewQueue(String queueName, String owner, boolean durable)
            throws IOException, JMException, MBeanException
    {
        createNewQueue(queueName, owner, durable, Collections.EMPTY_MAP);
    }

    public void createNewQueue(String queueName, String owner, boolean durable, Map<String, Object> arguments)
            throws IOException, JMException
    {
        // TODO - ignores owner (not sure that this isn't actually a good thing though)
        getConfiguredObject().createQueue(queueName, State.ACTIVE,durable, LifetimePolicy.PERMANENT,0l, arguments);
    }

    public void deleteQueue(
            @MBeanOperationParameter(name = ManagedQueue.TYPE, description = "Queue Name") String queueName)
            throws IOException, JMException, MBeanException
    {
        Queue theQueue = null;
        for(Queue queue : _virtualHostMBean.getVirtualHost().getQueues())
        {
            if(queue.getName().equals(queueName))
            {
                theQueue = queue;
                break;
            }
        }
        if(theQueue != null)
        {
            theQueue.delete();
        }
    }


    @Override
    public ObjectName getObjectName() throws MalformedObjectNameException
    {
        return getObjectNameForSingleInstanceMBean();
    }


    public synchronized boolean isStatisticsEnabled()
    {
        updateStats();
        return false;  //TODO - implement isStatisticsEnabled
    }

}
