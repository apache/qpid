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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.management.JMException;
import javax.management.ObjectName;

import org.apache.log4j.Logger;
import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.virtualhost.ManagedVirtualHost;

public class VirtualHostMBean extends AMQManagedObject implements ManagedVirtualHost, ConfigurationChangeListener
{
    private static final Logger LOGGER = Logger.getLogger(VirtualHostMBean.class);

    private final VirtualHost _virtualHost;

    private final Object _childrenLock = new Object();

    private final Map<ConfiguredObject, AMQManagedObject> _children =
            new HashMap<ConfiguredObject, AMQManagedObject>();

    private final Map<ConfiguredObject, Collection<ManagedObject>> _additionalChildren =
            new HashMap<ConfiguredObject, Collection<ManagedObject>>();

    private VirtualHostManagerMBean _managerMBean;

    public VirtualHostMBean(VirtualHost virtualHost, ManagedObjectRegistry registry) throws JMException
    {
        super(ManagedVirtualHost.class, ManagedVirtualHost.TYPE, registry);
        _virtualHost = virtualHost;
        virtualHost.addChangeListener(this);

        initQueues();
        initExchanges();
        initConnections();

        initAdditionalMbeansForAllChildren();

        //This is the actual JMX bean for this 'VirtualHostMBean', leave it alone.
        _managerMBean = new VirtualHostManagerMBean(this);
    }

    private void initAdditionalMbeansForAllChildren()
    {
        synchronized (_childrenLock)
        {
            Collection<Class<? extends ConfiguredObject>> childrenTypes = Model.getInstance().getChildTypes(VirtualHost.class);
            for (Class<? extends ConfiguredObject> childType : childrenTypes)
            {
                Collection<? extends ConfiguredObject> children = _virtualHost.getChildren(childType);
                for (ConfiguredObject child : children)
                {
                    createAdditionalMBeans(child);
                }
            }
        }
    }

    private void createAdditionalMBeans(ConfiguredObject child)
    {
        try
        {
            Collection<ManagedObject> mbeans = MBeanUtils.createAdditionalMBeansFromProviders(child, this);
            if (!mbeans.isEmpty())
            {
                _additionalChildren.put(child, mbeans);
            }
        }
        catch(Exception e)
        {
            LOGGER.error("Cannot create mbeans for the child " + child.getName(), e);
        }
    }


    private void initQueues()
    {
        synchronized (_childrenLock)
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
        synchronized (_childrenLock)
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
        synchronized (_childrenLock)
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
        synchronized (_childrenLock)
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
            }
            catch(Exception e)
            {
                LOGGER.error("Exception while creating mbean for " + child.getClass().getSimpleName() + " " + child.getName(), e);
            }
            createAdditionalMBeans(child);
        }
    }

    public void childRemoved(ConfiguredObject object, ConfiguredObject child)
    {
        synchronized (_childrenLock)
        {
            AMQManagedObject mbean = _children.remove(child);
            if(mbean != null)
            {
                try
                {
                    mbean.unregister();
                }
                catch(Exception e)
                {
                    LOGGER.error("Exception while unregistering mbean for " + child.getClass().getSimpleName() + " " + child.getName(), e);
                }
            }
            unregisterAdditionalMBeansIfPresent(child);
        }
    }

    private void unregisterAdditionalMBeansIfPresent(ConfiguredObject child)
    {
        Collection<ManagedObject> additionalMBeans = _additionalChildren.remove(child);
        if (additionalMBeans != null)
        {
            for (ManagedObject mbean : additionalMBeans)
            {
                try
                {
                    mbean.unregister();
                }
                catch(Exception e)
                {
                    LOGGER.error("Exception while unregistering mbean for " + child.getClass().getSimpleName() + " " + child.getName(), e);
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
        synchronized (_childrenLock)
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
        _virtualHost.removeChangeListener(this);

        synchronized (_childrenLock)
        {
            for (AMQManagedObject mbean : _children.values())
            {
                if(mbean != null)
                {
                    try
                    {
                        mbean.unregister();
                    }
                    catch(Exception e)
                    {
                        LOGGER.error("Exception while unregistering mbean : " + mbean, e);
                    }
                }
            }
            _children.clear();

            for (ConfiguredObject child : new ArrayList<ConfiguredObject>(_additionalChildren.keySet()))
            {
                unregisterAdditionalMBeansIfPresent(child);
            }
        }
        _managerMBean.unregister();
    }

    @Override
    public void attributeSet(ConfiguredObject object, String attributeName, Object oldAttributeValue, Object newAttributeValue)
    {
        // no-op
    }

}
