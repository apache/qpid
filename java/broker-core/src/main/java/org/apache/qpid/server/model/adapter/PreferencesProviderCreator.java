package org.apache.qpid.server.model.adapter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.plugin.PreferencesProviderFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class PreferencesProviderCreator
{
    private final Map<String, PreferencesProviderFactory> _factories;
    private Collection<String> _supportedPreferencesProviders;

    public PreferencesProviderCreator()
    {
        QpidServiceLoader<PreferencesProviderFactory> preferencesProviderFactoriess = new QpidServiceLoader<PreferencesProviderFactory>();

        Iterable<PreferencesProviderFactory> factories = preferencesProviderFactoriess
                .instancesOf(PreferencesProviderFactory.class);

        Map<String, PreferencesProviderFactory> registeredPreferencesProviderFactories = new HashMap<String, PreferencesProviderFactory>();
        for (PreferencesProviderFactory factory : factories)
        {
            PreferencesProviderFactory existingFactory = registeredPreferencesProviderFactories.put(factory.getType(),
                    factory);
            if (existingFactory != null)
            {
                throw new IllegalConfigurationException("Preferences provider factory of the same type '"
                        + factory.getType() + "' is already registered using class '" + existingFactory.getClass().getName()
                        + "', can not register class '" + factory.getClass().getName() + "'");
            }
        }
        _factories = registeredPreferencesProviderFactories;
        _supportedPreferencesProviders = Collections.unmodifiableCollection(registeredPreferencesProviderFactories.keySet());
    }

    public Collection<String> getSupportedPreferencesProviders()
    {
        return _supportedPreferencesProviders;
    }

    public PreferencesProvider create(UUID id, Map<String, Object> attributes, AuthenticationProvider authenticationProvider)
    {
        return createPreferencesProvider(id, attributes, authenticationProvider);
    }

    public PreferencesProvider recover(UUID id, Map<String, Object> attributes, AuthenticationProvider authenticationProviderr)
    {
        return createPreferencesProvider(id, attributes, authenticationProviderr);
    }

    private PreferencesProvider createPreferencesProvider(UUID id, Map<String, Object> attributes, AuthenticationProvider authenticationProvider)
    {
        for (PreferencesProviderFactory factory : _factories.values())
        {
            return factory.createInstance(id, attributes, authenticationProvider);
        }
        throw new IllegalConfigurationException("No group provider factory found for configuration attributes " + attributes);
    }
}
