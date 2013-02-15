package org.apache.qpid.server.configuration.startup;

import java.util.Collection;
import java.util.Map;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.adapter.AuthenticationProviderFactory;
import org.apache.qpid.server.model.adapter.BrokerAdapter;
import org.apache.qpid.server.model.adapter.PortFactory;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.security.group.GroupPrincipalAccessor;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class BrokerRecoverer implements ConfiguredObjectRecoverer<Broker>
{
    private final StatisticsGatherer _statisticsGatherer;
    private final VirtualHostRegistry _virtualHostRegistry;
    private final LogRecorder _logRecorder;
    private final RootMessageLogger _rootMessageLogger;
    private final AuthenticationProviderFactory _authenticationProviderFactory;
    private final PortFactory _portFactory;
    private final TaskExecutor _taskExecutor;

    public BrokerRecoverer(AuthenticationProviderFactory authenticationProviderFactory, PortFactory portFactory,
            StatisticsGatherer statisticsGatherer, VirtualHostRegistry virtualHostRegistry, LogRecorder logRecorder,
            RootMessageLogger rootMessageLogger, TaskExecutor taskExecutor)
    {
        _portFactory = portFactory;
        _authenticationProviderFactory = authenticationProviderFactory;
        _statisticsGatherer = statisticsGatherer;
        _virtualHostRegistry = virtualHostRegistry;
        _logRecorder = logRecorder;
        _rootMessageLogger = rootMessageLogger;
        _taskExecutor = taskExecutor;
    }

    @Override
    public Broker create(RecovererProvider recovererProvider, ConfigurationEntry entry, ConfiguredObject... parents)
    {
        StoreConfigurationChangeListener storeChangeListener = new StoreConfigurationChangeListener(entry.getStore());
        BrokerAdapter broker = new BrokerAdapter(entry.getId(), entry.getAttributes(), _statisticsGatherer, _virtualHostRegistry,
                _logRecorder, _rootMessageLogger, _authenticationProviderFactory, _portFactory, _taskExecutor);
        broker.addChangeListener(storeChangeListener);
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
                object.addChangeListener(storeChangeListener);
            }
        }
        wireUpConfiguredObjects(broker, entry.getAttributes());

        return broker;
    }

    private void wireUpConfiguredObjects(BrokerAdapter broker, Map<String, Object> brokerAttributes)
    {
        AuthenticationProvider defaultAuthenticationProvider = null;
        Collection<AuthenticationProvider> authenticationProviders = broker.getAuthenticationProviders();
        int numberOfAuthenticationProviders = authenticationProviders.size();
        if (numberOfAuthenticationProviders == 0)
        {
            throw new IllegalConfigurationException("No authentication provider was configured");
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
            String authenticationProviderName = (String) port.getAttribute(Port.AUTHENTICATION_MANAGER);
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
