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
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.JMException;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.jmx.mbeans.LoggingManagementMBean;
import org.apache.qpid.server.jmx.mbeans.ServerInformationMBean;
import org.apache.qpid.server.jmx.mbeans.Shutdown;
import org.apache.qpid.server.jmx.mbeans.UserManagementMBean;
import org.apache.qpid.server.jmx.mbeans.VirtualHostMBean;
import org.apache.qpid.server.logging.log4j.LoggingManagementFacade;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.model.adapter.AbstractPluginAdapter;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class JMXManagementPluginImpl
        extends AbstractPluginAdapter<JMXManagementPluginImpl> implements ConfigurationChangeListener,
                                                                          JMXManagementPlugin<JMXManagementPluginImpl>
{
    private static final Logger LOGGER = Logger.getLogger(JMXManagementPluginImpl.class);

    public static final String NAME = "name";

    // default values
    public static final String DEFAULT_NAME = "JMXManagement";

    @SuppressWarnings("serial")
    private static final Map<String, Type> ATTRIBUTE_TYPES = new HashMap<String, Type>(){{
        put(USE_PLATFORM_MBEAN_SERVER, Boolean.class);
        put(NAME, String.class);
        put(TYPE, String.class);
        put(TYPE, String.class);
    }};

    private JMXManagedObjectRegistry _objectRegistry;

    private final Object _childrenLock = new Object();
    private final Map<ConfiguredObject, AMQManagedObject> _children = new HashMap<ConfiguredObject, AMQManagedObject>();

    @ManagedAttributeField
    private boolean _usePlatformMBeanServer;

    public JMXManagementPluginImpl(Map<String, Object> attributes, Broker broker)
    {
        super(attributes, broker);
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
            catch (Exception e)
            {
                throw new ServerScopedRuntimeException("Couldn't start JMX management", e);
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
        Broker<?> broker = getBroker();
        Port connectorPort = null;
        Port registryPort = null;
        Collection<Port<?>> ports = broker.getPorts();
        for (Port port : ports)
        {
            if (State.QUIESCED.equals(port.getState()))
            {
                continue;
            }

            if(isRegistryPort(port))
            {
                registryPort = port;
            }
            else if(isConnectorPort(port))
            {
                connectorPort = port;
            }
        }
        if(connectorPort == null)
        {
            throw new IllegalStateException("No JMX connector port found supporting protocol " + Protocol.JMX_RMI);
        }
        if(registryPort == null)
        {
            throw new IllegalStateException("No JMX RMI port found supporting protocol " + Protocol.RMI);
        }

        _objectRegistry = new JMXManagedObjectRegistry(broker, connectorPort, registryPort, this);

        broker.addChangeListener(this);

        synchronized (_childrenLock)
        {
            for(VirtualHostNode<?> virtualHostNode : broker.getVirtualHostNodes())
            {
                virtualHostNode.addChangeListener(this);

                // Virtualhostnodes may or may not have a virtualhost at this point.  In the HA
                // case, JE may spontaneously make the node a master causing it to create a virtualhost.
                // Creation of the vhost uses the task executor (same thread that executes this code
                // so there is no potential for a race here).
                VirtualHost host = virtualHostNode.getVirtualHost();
                if (host != null)
                {
                    VirtualHostMBean mbean = new VirtualHostMBean(host, _objectRegistry);
                    _children.put(host, mbean);
                }
                createAdditionalMBeansFromProviders(virtualHostNode, _objectRegistry);
            }

            Collection<AuthenticationProvider<?>> authenticationProviders = broker.getAuthenticationProviders();
            for (AuthenticationProvider<?> authenticationProvider : authenticationProviders)
            {
                if(authenticationProvider instanceof PasswordCredentialManagingAuthenticationProvider)
                {
                    UserManagementMBean mbean = new UserManagementMBean(
                            (PasswordCredentialManagingAuthenticationProvider) authenticationProvider,
                            _objectRegistry);
                    _children.put(authenticationProvider, mbean);
                }
                createAdditionalMBeansFromProviders(authenticationProvider, _objectRegistry);
            }
        }
        new Shutdown(_objectRegistry);
        new ServerInformationMBean(_objectRegistry, broker);
        if (LoggingManagementFacade.getCurrentInstance() != null)
        {
            new LoggingManagementMBean(LoggingManagementFacade.getCurrentInstance(), _objectRegistry);
        }
        _objectRegistry.start();
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();

    }

    private boolean isConnectorPort(Port port)
    {
        return port.getAvailableProtocols().contains(Protocol.JMX_RMI);
    }

    private boolean isRegistryPort(Port port)
    {
        return port.getAvailableProtocols().contains(Protocol.RMI);
    }

    private void stop()
    {
        synchronized (_childrenLock)
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
                catch (Exception e)
                {
                    LOGGER.error("Exception while unregistering mbean for " + object.getClass().getSimpleName() + " " + object.getName(), e);
                }
            }
            _children.clear();
        }
        getBroker().removeChangeListener(this);
        closeObjectRegistry();
    }

    @Override
    public void stateChanged(ConfiguredObject object, State oldState, State newState)
    {
        // no-op
    }

    @Override
    public void childAdded(ConfiguredObject object, ConfiguredObject child)
    {

        synchronized (_childrenLock)
        {
            try
            {
                AMQManagedObject mbean;
                if (child instanceof VirtualHostNode)
                {
                    child.addChangeListener(this);
                }
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
                    _children.put(child, mbean);
                }
                createAdditionalMBeansFromProviders(child, _objectRegistry);
            }
            catch(Exception e)
            {
                LOGGER.error("Exception while creating mbean for " + child.getClass().getSimpleName() + " " + child.getName(), e);
                // TODO - Implement error reporting on mbean creation
            }
        }
    }

    @Override
    public void childRemoved(ConfiguredObject object, ConfiguredObject child)
    {
        synchronized (_childrenLock)
        {
            child.removeChangeListener(this);

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
                    //TODO - report error on removing child MBean
                }
            }
        }
    }

    @Override
    public void attributeSet(ConfiguredObject object, String attributeName, Object oldAttributeValue, Object newAttributeValue)
    {
        // no-op
    }

    private void createAdditionalMBeansFromProviders(ConfiguredObject child, ManagedObjectRegistry registry) throws JMException
    {
        QpidServiceLoader<MBeanProvider> qpidServiceLoader = new QpidServiceLoader<MBeanProvider>();
        for (MBeanProvider provider : qpidServiceLoader.instancesOf(MBeanProvider.class))
        {
            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Consulting mbean provider : " + provider + " for child : " + child);
            }

            ManagedObject mBean = null;
            if (provider.isChildManageableByMBean(child))
            {
                if(LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Provider will create mbean");
                }
                mBean = provider.createMBean(child, registry);
                // TODO track the mbeans that have been created on behalf of a child in a map, then
                // if the child is ever removed, destroy these beans too.
            }

            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Provider " + provider + " mBean for child " + child + " " + mBean);
            }
        }
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        if(changedAttributes.contains(NAME))
        {
            String newName = proxyForValidation.getName();
            if(!getName().equals(newName))
            {
                throw new IllegalConfigurationException("Changing the name of jmx management plugin is not allowed");
            }
        }
    }

    private void closeObjectRegistry()
    {
        if (_objectRegistry != null)
        {
            try
            {
                _objectRegistry.close();
            }
            finally
            {
                _objectRegistry = null;
            }
        }
    }

    @Override
    public boolean getUsePlatformMBeanServer()
    {
        return _usePlatformMBeanServer;
    }
}
