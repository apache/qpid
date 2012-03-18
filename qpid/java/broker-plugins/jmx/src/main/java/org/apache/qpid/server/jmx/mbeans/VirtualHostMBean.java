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

import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.virtualhost.ManagedVirtualHost;

import javax.management.JMException;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class VirtualHostMBean extends AMQManagedObject implements ManagedVirtualHost, ConfigurationChangeListener
{
    private final VirtualHost _virtualHost;

    private final Map<ConfiguredObject, AMQManagedObject> _children =
            new HashMap<ConfiguredObject, AMQManagedObject>();
    private VirtualHostManagerMBean _managerMBean;

    public VirtualHostMBean(VirtualHost virtualHost, ManagedObjectRegistry registry) throws JMException
    {
        super(ManagedVirtualHost.class, ManagedVirtualHost.TYPE, registry);
        _virtualHost = virtualHost;
        //register();
        virtualHost.addChangeListener(this);

        initQueues();
        initExchanges();
        initConnections();

        _managerMBean = new VirtualHostManagerMBean(this);
    }

    private void initQueues() throws JMException
    {
        synchronized (_children)
        {
            for(Queue queue : _virtualHost.getQueues())
            {
                if(!_children.containsKey(queue))
                {
                    _children.put(queue, new QueueMBean(queue, this));
                }
            }
        }
    }

    private void initExchanges() throws JMException
    {
        synchronized (_children)
        {
            for(Exchange exchange : _virtualHost.getExchanges())
            {
                if(!_children.containsKey(exchange))
                {
                    _children.put(exchange, new ExchangeMBean(exchange, this));
                }
            }
        }
    }

    private void initConnections() throws JMException
    {
        synchronized (_children)
        {
            for(Connection conn : _virtualHost.getConnections())
            {
                if(!_children.containsKey(conn))
                {
                    _children.put(conn, new ConnectionMBean(conn, this));
                }
            }
        }
    }

    public String getObjectInstanceName()
    {
        return ObjectName.quote(_virtualHost.getName());
    }

    public String getName()
    {
        return _virtualHost.getName();
    }

    public void stateChanged(ConfiguredObject object, State oldState, State newState)
    {
        // ignore
    }

    public void childAdded(ConfiguredObject object, ConfiguredObject child)
    {
        synchronized (_children)
        {
            try
            {
                if(child instanceof Queue)
                {
                    QueueMBean queueMB = new QueueMBean((Queue)child, this);
                    _children.put(child, queueMB);

                }
                else if(child instanceof Exchange)
                {
                    ExchangeMBean exchangeMBean = new ExchangeMBean((Exchange)child, this);
                    _children.put(child, exchangeMBean);

                }
                if(child instanceof Connection)
                {
                    ConnectionMBean connectionMBean = new ConnectionMBean((Connection)child, this);
                    _children.put(child, connectionMBean);

                }
                else
                {

                }

            }
            catch(JMException e)
            {
                e.printStackTrace();  //TODO - report error on adding child MBean
            }
        }
    }

    public void childRemoved(ConfiguredObject object, ConfiguredObject child)
    {
        synchronized (_children)
        {
            AMQManagedObject mbean = _children.remove(child);
            if(mbean != null)
            {
                try
                {
                    mbean.unregister();
                }
                catch(JMException e)
                {
                    e.printStackTrace();  //TODO - report error on removing child MBean
                }
            }
        }
    }

    @Override
    public ManagedObject getParentObject()
    {
        return null;
    }

    protected VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public Collection<QueueMBean> getQueues()
    {
        Collection<AMQManagedObject> children;
        synchronized (_children)
        {
            children = new ArrayList<AMQManagedObject>(_children.values());
        }
        Collection<QueueMBean> queues = new ArrayList<QueueMBean>();

        for(AMQManagedObject child : children)
        {
            if(child instanceof QueueMBean)
            {
                queues.add((QueueMBean) child);
            }
        }

        return queues;
    }
}
