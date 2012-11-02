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

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.management.JMException;

import org.apache.log4j.Logger;
import org.apache.qpid.server.jmx.mbeans.LoggingManagementMBean;
import org.apache.qpid.server.jmx.mbeans.UserManagementMBean;
import org.apache.qpid.server.jmx.mbeans.ServerInformationMBean;
import org.apache.qpid.server.jmx.mbeans.Shutdown;
import org.apache.qpid.server.jmx.mbeans.VirtualHostMBean;
import org.apache.qpid.server.logging.log4j.LoggingManagementFacade;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AbstractPluginAdapter;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class JMXManagement extends AbstractPluginAdapter implements ConfigurationChangeListener
{
    private static final Logger LOGGER = Logger.getLogger(JMXManagement.class);

    private static final String REGISTRY_PORT_NAME = "registry";
    private static final String CONNECTOR_PORT_NAME = "connector";

    private final Broker _broker;
    private JMXManagedObjectRegistry _objectRegistry;

    private final Map<ConfiguredObject, AMQManagedObject> _children = new HashMap<ConfiguredObject, AMQManagedObject>();

    private final JMXConfiguration _jmxConfiguration;

    public JMXManagement(UUID id, Broker broker, JMXConfiguration jmxConfiguration)
    {
        super(id);
        _broker = broker;
        _jmxConfiguration = jmxConfiguration;
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        if(desiredState == State.ACTIVE)
        {
            try
            {
                start();
            }
            catch (JMException e)
            {
                throw new RuntimeException("Couldn't start JMX management", e);
            }
            catch (IOException e)
            {
                throw new RuntimeException("Couldn't start JMX management", e);
            }
            return true;
        }
        else if(desiredState == State.STOPPED)
        {
            stop();
            return true;
        }
        return false;
    }

    private void start() throws JMException, IOException
    {
        Port connectorPort = null;
        Port registryPort = null;
        Collection<Port> ports = _broker.getPorts();
        for (Port port : ports)
        {
            if (port.getProtocols().contains(Protocol.JMX_RMI))
            {
                if(REGISTRY_PORT_NAME.equals(port.getName()))
                {
                    registryPort = port;
                }
                else if(CONNECTOR_PORT_NAME.equals(port.getName()))
                {
                    connectorPort = port;
                }
            }
        }
        if(connectorPort == null)
        {
            throw new IllegalStateException("No JMX port found with name " + CONNECTOR_PORT_NAME);
        }
        if(registryPort == null)
        {
            throw new IllegalStateException("No JMX port found with name " + REGISTRY_PORT_NAME);
        }

        _objectRegistry = new JMXManagedObjectRegistry(connectorPort, registryPort, _jmxConfiguration);

        _broker.addChangeListener(this);

        synchronized (_children)
        {
            for(VirtualHost virtualHost : _broker.getVirtualHosts())
            {
                if(!_children.containsKey(virtualHost))
                {
                    LOGGER.debug("Create MBean for virtual host:" + virtualHost.getName());
                    VirtualHostMBean mbean = new VirtualHostMBean(virtualHost, _objectRegistry);
                    LOGGER.debug("Check for additional MBeans for virtual host:" + virtualHost.getName());
                    createAdditionalMBeansFromProviders(virtualHost, mbean);
                }
            }
            Collection<AuthenticationProvider> authenticationProviders = _broker.getAuthenticationProviders();
            for (AuthenticationProvider authenticationProvider : authenticationProviders)
            {
                if(authenticationProvider instanceof PasswordCredentialManagingAuthenticationProvider)
                {
                    UserManagementMBean mbean = new UserManagementMBean(
                            (PasswordCredentialManagingAuthenticationProvider) authenticationProvider,
                            _objectRegistry);
                    _children.put(authenticationProvider, mbean);
                }
            }
        }
        new Shutdown(_objectRegistry);
        new ServerInformationMBean(_objectRegistry, _broker);
        new LoggingManagementMBean(LoggingManagementFacade.getCurrentInstance(), _objectRegistry);

        _objectRegistry.start();
    }

    private void stop()
    {
        synchronized (_children)
        {
            for(ConfiguredObject object : _children.keySet())
            {
                AMQManagedObject mbean = _children.get(object);
                if (mbean instanceof ConfigurationChangeListener)
                {
                    object.removeChangeListener((ConfigurationChangeListener)mbean);
                }
                try
                {
                    mbean.unregister();
                }
                catch (JMException e)
                {
                    LOGGER.error("Error unregistering mbean", e);
                }
            }
            _children.clear();
        }
        _broker.removeChangeListener(this);
        _objectRegistry.close();
    }

    @Override
    public void stateChanged(ConfiguredObject object, State oldState, State newState)
    {
        // no-op
    }

    @Override
    public void childAdded(ConfiguredObject object, ConfiguredObject child)
    {
        synchronized (_children)
        {
            try
            {
                AMQManagedObject mbean;
                if(child instanceof VirtualHost)
                {
                    VirtualHost vhostChild = (VirtualHost)child;
                    mbean = new VirtualHostMBean(vhostChild, _objectRegistry);
                }
                else if(child instanceof PasswordCredentialManagingAuthenticationProvider)
                {
                    mbean = new UserManagementMBean((PasswordCredentialManagingAuthenticationProvider) child, _objectRegistry);
                }
                else
                {
                    mbean = null;
                }

                if (mbean != null)
                {
                    createAdditionalMBeansFromProviders(child, mbean);
                }
            }
            catch(JMException e)
            {
                LOGGER.error("Error creating mbean", e);
                // TODO - Implement error reporting on mbean creation
            }
        }
    }

    @Override
    public void childRemoved(ConfiguredObject object, ConfiguredObject child)
    {
        // TODO - implement vhost removal (possibly just removing the instanceof check below)

        synchronized (_children)
        {
            if(child instanceof PasswordCredentialManagingAuthenticationProvider)
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
                        LOGGER.error("Error creating mbean", e);
                        //TODO - report error on removing child MBean
                    }
                }
            }

        }
    }

    private void createAdditionalMBeansFromProviders(ConfiguredObject child, AMQManagedObject mbean) throws JMException
    {
        _children.put(child, mbean);
        // XXX: MBeanProvider does not work at the moment
        QpidServiceLoader<MBeanProvider> qpidServiceLoader = new QpidServiceLoader<MBeanProvider>();
        for (MBeanProvider provider : qpidServiceLoader.instancesOf(MBeanProvider.class))
        {
            LOGGER.debug("Consulting mbean provider : " + provider + " for child : " + child);
            if (provider.isChildManageableByMBean(child))
            {
                LOGGER.debug("Provider will create mbean ");
                provider.createMBean(child, mbean);
                // TODO track the mbeans that have been created on behalf of a child in a map, then
                // if the child is ever removed, destroy these beans too.
            }
        }
    }

    /** Added for testing purposes */
    Broker getBroker()
    {
        return _broker;
    }

    @Override
    public String getName()
    {
        return "JMXManagement";
    }
}
