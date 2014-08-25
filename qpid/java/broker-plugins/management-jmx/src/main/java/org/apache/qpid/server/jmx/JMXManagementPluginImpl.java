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
import java.util.HashSet;
import java.util.Iterator;
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
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.model.adapter.AbstractPluginAdapter;
import org.apache.qpid.server.model.port.JmxPort;
import org.apache.qpid.server.model.port.PortManager;
import org.apache.qpid.server.model.port.RmiPort;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class JMXManagementPluginImpl
        extends AbstractPluginAdapter<JMXManagementPluginImpl> implements JMXManagementPlugin<JMXManagementPluginImpl>, PortManager
{
    private static final Logger LOGGER = Logger.getLogger(JMXManagementPluginImpl.class);

    public static final String NAME = "name";

    // default values
    public static final String DEFAULT_NAME = "JMXManagement";

    private JMXManagedObjectRegistry _objectRegistry;

    private final Object _childrenLock = new Object();
    private final Map<ConfiguredObject<?>, Map<MBeanProvider, ManagedObject>> _children = new HashMap<ConfiguredObject<?>, Map<MBeanProvider,ManagedObject>>();

    @ManagedAttributeField
    private boolean _usePlatformMBeanServer;

    private boolean _allowPortActivation;

    private final Set<MBeanProvider> _mBeanProviders;
    private final ChangeListener _changeListener;
    private final PluginMBeansProvider _pluginMBeanProvider;

    @ManagedObjectFactoryConstructor
    public JMXManagementPluginImpl(Map<String, Object> attributes, Broker<?> broker)
    {
        super(attributes, broker);
        _changeListener = new ChangeListener();
        _pluginMBeanProvider = new PluginMBeansProvider();
        _mBeanProviders = new HashSet<MBeanProvider>();
        QpidServiceLoader qpidServiceLoader = new QpidServiceLoader();
        for (MBeanProvider provider : qpidServiceLoader.instancesOf(MBeanProvider.class))
        {
            _mBeanProviders.add(provider);
        }
    }

    @Override
    public boolean getUsePlatformMBeanServer()
    {
        return _usePlatformMBeanServer;
    }

    @StateTransition(currentState = State.UNINITIALIZED, desiredState = State.ACTIVE)
    private void doStart() throws JMException, IOException
    {
        _allowPortActivation = true;
        Broker<?> broker = getBroker();
        JmxPort<?> connectorPort = null;
        RmiPort registryPort = null;
        Collection<Port<?>> ports = broker.getPorts();
        for (Port<?> port : ports)
        {
            if (port.getDesiredState() != State.ACTIVE)
            {
                continue;
            }

            if(isRegistryPort(port))
            {
                registryPort = (RmiPort) port;
                registryPort.setPortManager(this);
                if(port.getState() != State.ACTIVE)
                {
                    port.start();
                }

            }
            else if(isConnectorPort(port))
            {
                connectorPort = (JmxPort<?>) port;
                connectorPort.setPortManager(this);
                if(port.getState() != State.ACTIVE)
                {
                    port.start();
                }

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

        broker.addChangeListener(_changeListener);

        synchronized (_childrenLock)
        {
            for(VirtualHostNode<?> virtualHostNode : broker.getVirtualHostNodes())
            {
                createObjectMBeans(virtualHostNode);
            }

            Collection<AuthenticationProvider<?>> authenticationProviders = broker.getAuthenticationProviders();
            for (AuthenticationProvider<?> authenticationProvider : authenticationProviders)
            {
                createObjectMBeans(authenticationProvider);
            }
        }
        new Shutdown(_objectRegistry);
        new ServerInformationMBean(_objectRegistry, broker);
        if (LoggingManagementFacade.getCurrentInstance() != null)
        {
            new LoggingManagementMBean(LoggingManagementFacade.getCurrentInstance(), _objectRegistry);
        }
        _objectRegistry.start();
        setCurrentState(State.ACTIVE);
        _allowPortActivation = false;
    }

    @Override
    public boolean isActivationAllowed(final Port<?> port)
    {
        return _allowPortActivation;
    }

    private boolean isConnectorPort(Port<?> port)
    {
        return port.getProtocols().contains(Protocol.JMX_RMI);
    }

    private boolean isRegistryPort(Port<?> port)
    {
        return port.getProtocols().contains(Protocol.RMI);
    }

    @Override
    protected void onClose()
    {
        synchronized (_childrenLock)
        {
            for(ConfiguredObject<?> object : _children.keySet())
            {
                unregisterObjectMBeans(object);
            }
            _children.clear();
        }
        getBroker().removeChangeListener(_changeListener);
        closeObjectRegistry();
    }

    private void unregisterObjectMBeans(ConfiguredObject<?> object)
    {
        Map<?, ManagedObject> mbeans = _children.get(object);
        if (mbeans != null)
        {
            for (ManagedObject mbean : mbeans.values())
            {
                if (mbean instanceof ConfigurationChangeListener)
                {
                    object.removeChangeListener((ConfigurationChangeListener)mbean);
                }

                if (LOGGER.isDebugEnabled())
                {
                    String mbeanName = null;
                    try
                    {
                        mbeanName = mbean.getObjectName().toString();
                    }
                    catch(Exception e)
                    {
                        // ignore
                    }
                    LOGGER.debug("Unregistering MBean " + mbeanName + " for configured object " + object);
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
        }
    }

    private void createAdditionalMBeansFromProvidersIfNecessary(ConfiguredObject<?> child, ManagedObjectRegistry registry) throws JMException
    {
        for (MBeanProvider provider : _mBeanProviders)
        {
            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Consulting mbean provider : " + provider + " for child : " + child);
            }

            ManagedObject mBean = null;
            if (provider.isChildManageableByMBean(child) && !providerMBeanExists(child, provider))
            {
                if(LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Provider of type " + provider.getType() + " will create mbean for " + child);
                }

                mBean = provider.createMBean(child, registry);
                if (mBean != null)
                {
                    registerMBean(child, provider, mBean);
                }
            }

            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Provider " + provider + (mBean == null ? " did not create mBean" : " created mBean " + mBean)
                        + " for child " + child);
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

    private ManagedObject createObjectMBeansIfNecessary(ConfiguredObject<?> object) throws JMException
    {
        ManagedObject mbean = null;
        if (supportedConfiguredObject(object))
        {
            synchronized (_childrenLock)
            {
                if (!providerMBeanExists(object, _pluginMBeanProvider))
                {
                    if (object instanceof VirtualHostNode)
                    {
                        object.addChangeListener(_changeListener);
                        VirtualHostNode<?> virtualHostNode = (VirtualHostNode<?>) object;

                        // Virtual host nodes may or may not have a virtual host at this point.
                        // In the HA case, JE may spontaneously make the node a master causing it to create a virtual host.
                        // Creation of the virtual host uses the task executor (same thread that executes this code
                        // so there is no potential for a race here).
                        VirtualHost<?, ?, ?> host = virtualHostNode.getVirtualHost();
                        if (host != null)
                        {
                            createVirtualHostMBeanIfNecessary(host, _objectRegistry);
                        }
                    }
                    else if (object instanceof VirtualHost)
                    {
                        mbean = createVirtualHostMBeanIfNecessary((VirtualHost<?, ?, ?>) object, _objectRegistry);
                    }
                    else if (object instanceof PasswordCredentialManagingAuthenticationProvider)
                    {
                        object.addChangeListener(_changeListener);
                        mbean = new UserManagementMBean((PasswordCredentialManagingAuthenticationProvider<?>) object, _objectRegistry);
                        registerMBean(object, _pluginMBeanProvider, mbean);
                    }
                    createAdditionalMBeansFromProvidersIfNecessary(object, _objectRegistry);
                }
            }
        }
        return mbean;
    }

    private VirtualHostMBean createVirtualHostMBeanIfNecessary(VirtualHost<?, ?, ?> host, ManagedObjectRegistry _objectRegistry) throws JMException
    {
        if (!providerMBeanExists(host, _pluginMBeanProvider))
        {
            VirtualHostMBean mbean = new VirtualHostMBean(host, _objectRegistry);
            registerMBean(host, _pluginMBeanProvider, mbean);
            host.addChangeListener(_changeListener);
            return mbean;
        }
        return null;
    }

    private void registerMBean(ConfiguredObject<?> configuredObject, MBeanProvider mBeanProvider, ManagedObject mbean)
    {
        Map<MBeanProvider, ManagedObject> mbeans = _children.get(configuredObject);
        if (mbeans == null)
        {
            mbeans = new HashMap<MBeanProvider, ManagedObject>();
            _children.put(configuredObject, mbeans);
        }
        mbeans.put(mBeanProvider, mbean);
    }

    private boolean providerMBeanExists(ConfiguredObject<?> configuredObject, MBeanProvider mBeanProvider)
    {
        Map<MBeanProvider, ManagedObject> mbeans = _children.get(configuredObject);
        if (mbeans == null)
        {
            return false;
        }
        return mbeans.containsKey(mBeanProvider);
    }

    private void destroyObjectMBeans(ConfiguredObject<?> object, boolean removeListener)
    {
        if (supportedConfiguredObject(object))
        {
            synchronized (_childrenLock)
            {
                if (removeListener)
                {
                    object.removeChangeListener(_changeListener);
                }
                unregisterObjectMBeans(object);
                _children.remove(object);
                destroyChildrenMBeansIfVirtualHostNode(object);
            }
        }
    }

    private void destroyChildrenMBeansIfVirtualHostNode(ConfiguredObject<?> child)
    {
        if (child instanceof VirtualHostNode)
        {
            for (Iterator<ConfiguredObject<?>> iterator = _children.keySet().iterator(); iterator.hasNext();)
            {
                ConfiguredObject<?> registeredObject = iterator.next();
                ConfiguredObject<?> parent = registeredObject.getParent(VirtualHostNode.class);
                if (parent == child)
                {
                    registeredObject.removeChangeListener(_changeListener);
                    unregisterObjectMBeans(registeredObject);
                }
                iterator.remove();
            }
        }
    }

    private void createObjectMBeans(ConfiguredObject<?> object)
    {
        try
        {
            createObjectMBeansIfNecessary(object);
        }
        catch (JMException e)
        {
            LOGGER.error("Cannot create MBean for " + object, e);
        }
    }

    private boolean supportedConfiguredObject(ConfiguredObject<?> object)
    {
        return object instanceof VirtualHostNode || object instanceof VirtualHost || object instanceof PasswordCredentialManagingAuthenticationProvider;
    }

    private class PluginMBeansProvider implements MBeanProvider
    {
        @Override
        public boolean isChildManageableByMBean(ConfiguredObject<?> object)
        {
            return supportedConfiguredObject(object);
        }

        @Override
        public ManagedObject createMBean(ConfiguredObject<?> object, ManagedObjectRegistry registry) throws JMException
        {
            return createObjectMBeansIfNecessary(object);
        }

        @Override
        public String getType()
        {
            return "INTERNAL";
        }

        @Override
        public String toString()
        {
            return DEFAULT_NAME;
        }
    }

    private class ChangeListener implements ConfigurationChangeListener
    {
        @Override
        public void stateChanged(ConfiguredObject<?> object, State oldState, State newState)
        {
            if (newState == State.DELETED || newState == State.STOPPED || newState == State.ERRORED)
            {
                destroyObjectMBeans(object, newState == State.DELETED);
            }
            else if (newState == State.ACTIVE)
            {
                createObjectMBeans(object);
            }
        }

        @Override
        public void childAdded(ConfiguredObject<?> object, ConfiguredObject<?> child)
        {
            createObjectMBeans(child);
        }

        @Override
        public void childRemoved(ConfiguredObject<?> object, ConfiguredObject<?> child)
        {
            destroyObjectMBeans(child, true);
        }

        @Override
        public void attributeSet(ConfiguredObject<?> object, String attributeName, Object oldAttributeValue, Object newAttributeValue)
        {
            // VH can be created after attribute change,
            // for instance, on role change in BDB HA VHN a VH could is recovered/created.
            // A call to createObjectMBeans is safe as it checks the existence of MBean before its creation.

            if (ConfiguredObject.DESIRED_STATE.equals(attributeName))
            {
                stateChanged(object, State.valueOf(String.valueOf(oldAttributeValue)), State.valueOf(String.valueOf(newAttributeValue)));
            }
            else
            {
                createObjectMBeans(object);
            }
        }
    }

}
