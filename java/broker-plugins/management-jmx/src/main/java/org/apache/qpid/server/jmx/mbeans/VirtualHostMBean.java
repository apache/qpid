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

import org.apache.log4j.Logger;
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
    private static final Logger LOGGER = Logger.getLogger(VirtualHostMBean.class);

    private final VirtualHost _virtualHost;

    private final Map<ConfiguredObject, AMQManagedObject> _children =
            new HashMap<ConfiguredObject, AMQManagedObject>();
    private VirtualHostManagerMBean _managerMBean;

    public VirtualHostMBean(VirtualHost virtualHost, ManagedObjectRegistry registry) throws JMException
    {
        super(ManagedVirtualHost.class, ManagedVirtualHost.TYPE, registry);
        _virtualHost = virtualHost;
        virtualHost.addChangeListener(this);

        initQueues();
        initExchanges();
        initConnections();

        //This is the actual JMX bean for this 'VirtualHostMBean', leave it alone.
        _managerMBean = new VirtualHostManagerMBean(this);
    }

    private void initQueues()
    {
        synchronized (_children)
        {
            for(Queue queue : _virtualHost.getQueues())
            {
                if(!_children.containsKey(queue))
                {
                    try
                    {
                        _children.put(queue, new QueueMBean(queue, this));
                    }
                    catch(Exception e)
                    {
                        LOGGER.error("Cannot create queue mbean for queue " + queue.getName(), e);
                    }
                }
            }
        }
    }

    private void initExchanges()
    {
        synchronized (_children)
        {
            for(Exchange exchange : _virtualHost.getExchanges())
            {
                if(!_children.containsKey(exchange))
                {
                    try
                    {
                        _children.put(exchange, new ExchangeMBean(exchange, this));
                    }
                    catch(Exception e)
                    {
                        LOGGER.error("Cannot create exchange mbean for exchange " + exchange.getName(), e);
                    }
                }
            }
        }
    }

    private void initConnections()
    {
        synchronized (_children)
        {
            for(Connection conn : _virtualHost.getConnections())
            {
                if(!_children.containsKey(conn))
                {
                    try
                    {
                        _children.put(conn, new ConnectionMBean(conn, this));
                    }
                    catch(Exception e)
                    {
                        LOGGER.error("Cannot create connection mbean for connection " + conn.getName(), e);
                    }
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
        // no-op
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
                else if(child instanceof Connection)
                {
                    ConnectionMBean connectionMBean = new ConnectionMBean((Connection)child, this);
                    _children.put(child, connectionMBean);

                }
                else
                {
                    LOGGER.debug("Unsupported child : " + child.getName() + " type : " + child.getClass());
                }

            }
            catch(JMException e)
            {
                LOGGER.error("Failed to add mbean for child : " + child.getName(), e);
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
                    LOGGER.error("Failed to remove mbean for child : " + child.getName(), e);
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

    @Override
    public void unregister() throws JMException
    {
        synchronized (_children)
        {
            for (AMQManagedObject mbean : _children.values())
            {
                if(mbean != null)
                {
                    try
                    {
                        mbean.unregister();
                    }
                    catch(JMException e)
                    {
                        LOGGER.error("Failed to remove mbean for child : " + mbean, e);
                    }
                }
            }
            _children.clear();
        }
        _managerMBean.unregister();
    }

    @Override
    public void attributeSet(ConfiguredObject object, String attributeName, Object oldAttributeValue, Object newAttributeValue)
    {
        // no-op
    }

}
