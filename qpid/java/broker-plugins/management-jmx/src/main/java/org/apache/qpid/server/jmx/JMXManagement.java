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
import java.util.UUID;

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
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AbstractPluginAdapter;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

@ManagedObject( category = false , type = "MANAGEMENT-JMX" )
public class JMXManagement extends AbstractPluginAdapter<JMXManagement> implements ConfigurationChangeListener
{
    private static final Logger LOGGER = Logger.getLogger(JMXManagement.class);

    public static final String PLUGIN_TYPE = "MANAGEMENT-JMX";

    // attributes
    public static final String USE_PLATFORM_MBEAN_SERVER = "usePlatformMBeanServer";
    public static final String NAME = "name";

    // default values
    public static final String DEFAULT_NAME = "JMXManagement";
    public static final boolean DEFAULT_USE_PLATFORM_MBEAN_SERVER = true;

    @SuppressWarnings("serial")
    private static final Map<String, Object> DEFAULTS = new HashMap<String, Object>(){{
        put(USE_PLATFORM_MBEAN_SERVER, DEFAULT_USE_PLATFORM_MBEAN_SERVER);
        put(NAME, DEFAULT_NAME);
        put(TYPE, PLUGIN_TYPE);
    }};

    @SuppressWarnings("serial")
    private static final Map<String, Type> ATTRIBUTE_TYPES = new HashMap<String, Type>(){{
        put(USE_PLATFORM_MBEAN_SERVER, Boolean.class);
        put(NAME, String.class);
        put(TYPE, String.class);
        put(TYPE, String.class);
    }};

    private JMXManagedObjectRegistry _objectRegistry;

    private final Map<ConfiguredObject, AMQManagedObject> _children = new HashMap<ConfiguredObject, AMQManagedObject>();

    public JMXManagement(UUID id, Broker broker, Map<String, Object> attributes)
    {
        super(id, DEFAULTS, MapValueConverter.convert(attributes, ATTRIBUTE_TYPES), broker);
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

        synchronized (_children)
        {
            for(VirtualHost<?,?,?> virtualHost : broker.getVirtualHosts())
            {
                if(!_children.containsKey(virtualHost))
                {
                    if(LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("Create MBean for virtual host:" + virtualHost.getName());
                    }
                    VirtualHostMBean mbean = new VirtualHostMBean(virtualHost, _objectRegistry);

                    if(LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("Check for additional MBeans for virtual host:" + virtualHost.getName());
                    }
                    createAdditionalMBeansFromProviders(virtualHost, mbean);
                }
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

    private boolean isConnectorPort(Port port)
    {
        return port.getProtocols().contains(Protocol.JMX_RMI);
    }

    private boolean isRegistryPort(Port port)
    {
        return port.getProtocols().contains(Protocol.RMI);
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
        synchronized (_children)
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

    private void createAdditionalMBeansFromProviders(ConfiguredObject child, AMQManagedObject mbean) throws JMException
    {
        _children.put(child, mbean);

        QpidServiceLoader<MBeanProvider> qpidServiceLoader = new QpidServiceLoader<MBeanProvider>();
        for (MBeanProvider provider : qpidServiceLoader.instancesOf(MBeanProvider.class))
        {
            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Consulting mbean provider : " + provider + " for child : " + child);
            }

            if (provider.isChildManageableByMBean(child))
            {
                if(LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Provider will create mbean");
                }
                provider.createMBean(child, mbean);
                // TODO track the mbeans that have been created on behalf of a child in a map, then
                // if the child is ever removed, destroy these beans too.
            }
        }
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(JMXManagement.class);
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        Map<String, Object> convertedAttributes = MapValueConverter.convert(attributes, ATTRIBUTE_TYPES);
        validateAttributes(convertedAttributes);

        super.changeAttributes(convertedAttributes);
    }

    private void validateAttributes(Map<String, Object> convertedAttributes)
    {
        if(convertedAttributes.containsKey(JMXManagement.NAME))
        {
            String newName = (String) convertedAttributes.get(JMXManagement.NAME);
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
    public String getPluginType()
    {
        return PLUGIN_TYPE;
    }

    @ManagedAttribute
    public boolean getUsePlatformMBeanServer()
    {
        return (Boolean)getAttribute(USE_PLATFORM_MBEAN_SERVER);
    }
}
