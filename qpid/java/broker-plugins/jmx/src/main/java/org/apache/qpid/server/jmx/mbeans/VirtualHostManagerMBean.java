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

import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.management.common.mbeans.annotations.MBeanConstructor;
import org.apache.qpid.management.common.mbeans.annotations.MBeanDescription;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;
import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.model.Queue;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@MBeanDescription("This MBean exposes the broker level management features")
public class VirtualHostManagerMBean extends AMQManagedObject implements ManagedBroker
{

    private final VirtualHostMBean _virtualHostMBean;

    @MBeanConstructor("Creates the Broker Manager MBean")
    public VirtualHostManagerMBean(VirtualHostMBean virtualHostMBean) throws JMException
    {
        super(ManagedBroker.class, ManagedBroker.TYPE, virtualHostMBean.getRegistry());
        _virtualHostMBean = virtualHostMBean;
        register();
    }
    
    public String getObjectInstanceName()
    {
        return _virtualHostMBean.getName();
    }

    @Override
    public ManagedObject getParentObject()
    {
        return _virtualHostMBean;
    }

    public String[] getExchangeTypes() throws IOException
    {
        return new String[0];  //TODO
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

    public void createNewExchange(
            @MBeanOperationParameter(name = "name", description = "Name of the new exchange") String name,
            @MBeanOperationParameter(name = "ExchangeType", description = "Type of the exchange") String type,
            @MBeanOperationParameter(name = "durable",
                                     description = "true if the Exchang should be durable") boolean durable)
            throws IOException, JMException, MBeanException
    {
        //TODO
    }

    public void unregisterExchange(
            @MBeanOperationParameter(name = ManagedExchange.TYPE, description = "Exchange Name") String exchange)
            throws IOException, JMException, MBeanException
    {
        //TODO
    }

    public void createNewQueue(
            @MBeanOperationParameter(name = "queue name", description = "Name of the new queue") String queueName,
            @MBeanOperationParameter(name = "owner", description = "Owner name") String owner,
            @MBeanOperationParameter(name = "durable",
                                     description = "true if the queue should be durable") boolean durable)
            throws IOException, JMException, MBeanException
    {
        //TODO
    }

    public void createNewQueue(
            @MBeanOperationParameter(name = "queue name", description = "Name of the new queue") String queueName,
            @MBeanOperationParameter(name = "owner", description = "Owner name") String owner,
            @MBeanOperationParameter(name = "durable",
                                     description = "true if the queue should be durable") boolean durable,
            @MBeanOperationParameter(name = "arguments",
                                     description = "Map of arguments") Map<String, Object> arguments)
            throws IOException, JMException
    {
        //TODO
    }

    public void deleteQueue(
            @MBeanOperationParameter(name = ManagedQueue.TYPE, description = "Queue Name") String queueName)
            throws IOException, JMException, MBeanException
    {
        //TODO
    }

    public void resetStatistics() throws Exception
    {
        //TODO
    }

    public double getPeakMessageDeliveryRate()
    {
        return 0;  //TODO
    }

    public double getPeakDataDeliveryRate()
    {
        return 0;  //TODO
    }

    public double getMessageDeliveryRate()
    {
        return 0;  //TODO
    }

    public double getDataDeliveryRate()
    {
        return 0;  //TODO
    }

    public long getTotalMessagesDelivered()
    {
        return 0;  //TODO
    }

    public long getTotalDataDelivered()
    {
        return 0;  //TODO
    }

    public double getPeakMessageReceiptRate()
    {
        return 0;  //TODO
    }

    public double getPeakDataReceiptRate()
    {
        return 0;  //TODO
    }

    public double getMessageReceiptRate()
    {
        return 0;  //TODO
    }

    public double getDataReceiptRate()
    {
        return 0;  //TODO
    }

    public long getTotalMessagesReceived()
    {
        return 0;  //TODO
    }

    public long getTotalDataReceived()
    {
        return 0;  //TODO
    }

    public boolean isStatisticsEnabled()
    {
        return false;  //TODO
    }


    @Override
    public ObjectName getObjectName() throws MalformedObjectNameException
    {
        return getObjectNameForSingleInstanceMBean();
    }

}
