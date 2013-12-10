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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.OperationsException;

import org.apache.log4j.Logger;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
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
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.QueueArgumentsConverter;

@MBeanDescription("This MBean exposes the broker level management features")
public class VirtualHostManagerMBean extends AbstractStatisticsGatheringMBean<VirtualHost> implements ManagedBroker
{
    private static final Logger LOGGER = Logger.getLogger(VirtualHostManagerMBean.class);

    private static final boolean _moveNonExclusiveQueueOwnerToDescription = Boolean.parseBoolean(System.getProperty("qpid.move_non_exclusive_queue_owner_to_description", Boolean.TRUE.toString()));

    private final VirtualHostMBean _virtualHostMBean;

    @MBeanConstructor("Creates the Broker Manager MBean")
    public VirtualHostManagerMBean(VirtualHostMBean virtualHostMBean) throws JMException
    {
        super(ManagedBroker.class, ManagedBroker.TYPE, virtualHostMBean.getRegistry(), virtualHostMBean.getVirtualHost());
        _virtualHostMBean = virtualHostMBean;
        register();
    }


    @Override
    public String getObjectInstanceName()
    {
        return ObjectName.quote(_virtualHostMBean.getName());
    }

    @Override
    public ManagedObject getParentObject()
    {
        return _virtualHostMBean;
    }

    @Override
    public String[] getExchangeTypes() throws IOException
    {
        Collection<String> exchangeTypes = _virtualHostMBean.getVirtualHost().getExchangeTypes();
        return exchangeTypes.toArray(new String[exchangeTypes.size()]);
    }

    @Override
    public List<String> retrieveQueueAttributeNames() throws IOException
    {
        return ManagedQueue.QUEUE_ATTRIBUTES;
    }

    @Override
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

    @Override
    public void createNewExchange(String name, String type, boolean durable)
            throws IOException, JMException, MBeanException
    {
        if (!getConfiguredObject().getExchangeTypes().contains(type))
        {
            throw new OperationsException("No such exchange type \""+type+"\"");
        }

        try
        {
            getConfiguredObject().createExchange(name, State.ACTIVE, durable,
                                            LifetimePolicy.PERMANENT, 0l, type, Collections.EMPTY_MAP);
        }
        catch (IllegalArgumentException iae)
        {
            JMException jme = new JMException(iae.toString());
            throw new MBeanException(jme, "Error in creating exchange " + name);
        }

    }

    @Override
    public void unregisterExchange(String exchangeName)
            throws IOException, JMException, MBeanException
    {
        Exchange theExchange = MBeanUtils.findExchangeFromExchangeName(_virtualHostMBean.getVirtualHost(), exchangeName);
        try
        {
            theExchange.delete();
        }
        catch (IllegalStateException ex)
        {
            final JMException jme = new JMException(ex.toString());
            throw new MBeanException(jme, "Error in unregistering exchange " + exchangeName);
        }
    }

    @Override
    public void createNewQueue(String queueName, String owner, boolean durable)
            throws IOException, JMException, MBeanException
    {
        createNewQueue(queueName, owner, durable, Collections.EMPTY_MAP);
    }

    @Override
    public void createNewQueue(String queueName, String owner, boolean durable, Map<String, Object> originalArguments)
            throws IOException, JMException
    {
        final Map<String, Object> createArgs = processNewQueueArguments(queueName, owner, originalArguments);
        getConfiguredObject().createQueue(queueName, State.ACTIVE, durable, false, LifetimePolicy.PERMANENT, 0l,
                QueueArgumentsConverter.convertWireArgsToModel(createArgs));
    }


    /**
     * Some users have been abusing the owner field to store a queue description.  As the owner field
     * only makes sense if exclusive=true, and it is currently impossible to create an exclusive queue via
     * the JMX interface, if the user specifies a owner, then we assume that they actually mean to pass a description.
     */
    private Map<String, Object> processNewQueueArguments(String queueName,
            String owner, Map<String, Object> arguments)
    {
        final Map<String, Object> argumentsCopy;
        if (_moveNonExclusiveQueueOwnerToDescription && owner != null)
        {
            argumentsCopy = new HashMap<String, Object>(arguments == null ? new HashMap<String, Object>() : arguments);
            if (!argumentsCopy.containsKey(QueueArgumentsConverter.X_QPID_DESCRIPTION))
            {
                LOGGER.warn("Non-exclusive owner " + owner + " for new queue " + queueName + " moved to " + QueueArgumentsConverter.X_QPID_DESCRIPTION);

                argumentsCopy.put(QueueArgumentsConverter.X_QPID_DESCRIPTION, owner);
            }
            else
            {
                LOGGER.warn("Non-exclusive owner " + owner + " for new queue " + queueName + " ignored.");
            }
        }
        else
        {
            argumentsCopy = arguments;
        }
        return argumentsCopy;
    }

    @Override
    public void deleteQueue(
            @MBeanOperationParameter(name = ManagedQueue.TYPE, description = "Queue Name") String queueName)
            throws IOException, JMException, MBeanException
    {
        Queue theQueue = MBeanUtils.findQueueFromQueueName(_virtualHostMBean.getVirtualHost(), queueName);
        theQueue.delete();
    }

    @Override
    public ObjectName getObjectName() throws MalformedObjectNameException
    {
        return getObjectNameForSingleInstanceMBean();
    }

    public boolean isStatisticsEnabled()
    {
        return true;
    }

}
