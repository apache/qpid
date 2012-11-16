package org.apache.qpid.server.configuration.startup;

import java.util.Collection;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.adapter.AuthenticationProviderAdapter;
import org.apache.qpid.server.model.adapter.AuthenticationProviderFactory;
import org.apache.qpid.server.model.adapter.BrokerAdapter;
import org.apache.qpid.server.model.adapter.PortFactory;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.group.GroupPrincipalAccessor;

public class BrokerRecoverer implements ConfiguredObjectRecoverer<Broker>
{
    private static final Logger LOGGER = Logger.getLogger(BrokerRecoverer.class);

    private final IApplicationRegistry _registry;
    private final PortFactory _portFactory;
    private final AuthenticationProviderFactory _authenticationProviderFactory;

    public BrokerRecoverer(AuthenticationProviderFactory authenticationProviderFactory, PortFactory portFactory, IApplicationRegistry registry)
    {
        _registry = registry;
        _portFactory = portFactory;
        _authenticationProviderFactory = authenticationProviderFactory;
    }

    @Override
    public Broker create(RecovererProvider recovererProvider, ConfigurationEntry entry, ConfiguredObject... parents)
    {
        BrokerAdapter broker = new BrokerAdapter(entry.getId(), entry.getAttributes(), _registry, _authenticationProviderFactory, _portFactory);
        Map<String, Collection<ConfigurationEntry>> childEntries = entry.getChildren();
        for (String type : childEntries.keySet())
        {
            ConfiguredObjectRecoverer<?> recoverer = recovererProvider.getRecoverer(type);
            if (recoverer == null)
            {
                throw new IllegalConfigurationException("Cannot recover entry for the type '" + type + "' from broker");
            }
            Collection<ConfigurationEntry> entries = childEntries.get(type);
            for (ConfigurationEntry childEntry : entries)
            {
                ConfiguredObject object = recoverer.create(recovererProvider, childEntry, broker);
                if (object == null)
                {
                    throw new IllegalConfigurationException("Cannot create configured object for the entry " + childEntry);
                }
                broker.recoverChild(object);
            }
        }
        wireUpConfiguredObjects(broker, entry.getAttributes());

        return broker;
    }

    // XXX unit test this
    private void wireUpConfiguredObjects(BrokerAdapter broker, Map<String,Object> brokerAttributes)
    {
        AuthenticationProvider defaultAuthenticationProvider = null;
        Collection<AuthenticationProvider> authenticationProviders = broker.getAuthenticationProviders();
        int numberOfAuthenticationProviders = authenticationProviders.size();
        if (numberOfAuthenticationProviders == 0)
        {
        	throw new IllegalConfigurationException("No authentication provider was onfigured");
        }
        else if (numberOfAuthenticationProviders == 1)
        {
            defaultAuthenticationProvider = authenticationProviders.iterator().next();
        }
        else
        {
            String name = (String) brokerAttributes.get(Broker.DEFAULT_AUTHENTICATION_PROVIDER);
            if (name == null)
            {
            	throw new IllegalConfigurationException("Multiple authentication providers defined, but no default was configured.");
            }
            
            defaultAuthenticationProvider = getAuthenticationProviderByName(broker, name);
        }
        broker.setDefaultAuthenticationProvider(defaultAuthenticationProvider);

        GroupPrincipalAccessor groupPrincipalAccessor = new GroupPrincipalAccessor(broker.getGroupProviders());
        for (AuthenticationProvider authenticationProvider : authenticationProviders)
        {
            authenticationProvider.setGroupAccessor(groupPrincipalAccessor);
        }

        Collection<Port> ports = broker.getPorts();
        for (Port port : ports)
        {
            String authenticationProviderName = port.getAuthenticationManagerName();
            AuthenticationProvider provider = null;
            if (authenticationProviderName != null)
            {
                provider = getAuthenticationProviderByName(broker, authenticationProviderName);
            }
            else
            {
                provider = defaultAuthenticationProvider;
            }
            port.setAuthenticationProvider(provider);
        }
    }

    private AuthenticationProvider getAuthenticationProviderByName(BrokerAdapter broker, String authenticationProviderName)
    {
        AuthenticationProvider provider = broker.getAuthenticationProviderByName(authenticationProviderName);
        if (provider == null)
        {
            throw new IllegalConfigurationException("Cannot find the authentication provider with name: "
                    + authenticationProviderName);
        }
        return provider;
    }

}
