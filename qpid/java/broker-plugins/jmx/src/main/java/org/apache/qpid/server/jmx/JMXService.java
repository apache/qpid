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

package org.apache.qpid.server.jmx;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.jmx.mbeans.ConfigurationManagementMBean;
import org.apache.qpid.server.jmx.mbeans.VirtualHostMBean;
import org.apache.qpid.server.jmx.mbeans.Shutdown;

import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.AbstractActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.BrokerAdapter;

import javax.management.JMException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JMXService implements ConfigurationChangeListener
{
    private final Broker _broker;
    private final JMXManagedObjectRegistry _objectRegistry;
    private final Shutdown _shutdown;
    private final ConfigurationManagementMBean _configManagement;

    private final Map<ConfiguredObject, AMQManagedObject> _children =
            new HashMap<ConfiguredObject, AMQManagedObject>();

    public JMXService() throws AMQException, JMException
    {
        // TODO - logging actor
        CurrentActor.set(new AbstractActor(new SystemOutMessageLogger())
        {

            @Override
            public String getLogMessage()
            {
                return "[JMX Service]";
            }
        });

        _broker = BrokerAdapter.getInstance();
        _objectRegistry = new JMXManagedObjectRegistry();

        _broker.addChangeListener(this);
        synchronized (_children)
        {
            for(VirtualHost virtualHost : _broker.getVirtualHosts())
            {
                if(!_children.containsKey(virtualHost))
                {
                    _children.put(virtualHost, new VirtualHostMBean(virtualHost, _objectRegistry));
                }
            }
        }
        _shutdown = new Shutdown(_objectRegistry);
        _configManagement = new ConfigurationManagementMBean(_objectRegistry);
    }
    
    public void start() throws IOException, ConfigurationException
    {
        _objectRegistry.start();
    }
    
    public void close()
    {
        _objectRegistry.close();
    }

    public void stateChanged(ConfiguredObject object, State oldState, State newState)
    {

    }

    public void childAdded(ConfiguredObject object, ConfiguredObject child)
    {
        synchronized (_children)
        {
            if(child instanceof VirtualHost)
            {
                try
                {
                    _children.put(child, new VirtualHostMBean((VirtualHost)child,_objectRegistry));
                }
                catch(JMException e)
                {
                    e.printStackTrace();  // TODO - Implement error logging on vhost mbean creation
                }
            }
        }
    }

    public void childRemoved(ConfiguredObject object, ConfiguredObject child)
    {
        // TODO - implement vhost removal
    }
}
