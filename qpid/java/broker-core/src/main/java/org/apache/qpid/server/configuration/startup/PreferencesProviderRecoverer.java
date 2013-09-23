package org.apache.qpid.server.configuration.startup;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.adapter.PreferencesProviderCreator;

public class PreferencesProviderRecoverer implements ConfiguredObjectRecoverer<PreferencesProvider>
{

    private PreferencesProviderCreator _preferencesProviderCreator;

    public PreferencesProviderRecoverer(PreferencesProviderCreator preferencesProviderCreator)
    {
        _preferencesProviderCreator = preferencesProviderCreator;
    }

    @Override
    public PreferencesProvider create(RecovererProvider recovererProvider, ConfigurationEntry entry,
            ConfiguredObject... parents)
    {
        if (parents == null || parents.length == 0)
        {
            throw new IllegalArgumentException("AuthenticationProvider parent is not passed!");
        }
        if (parents.length != 1)
        {
            throw new IllegalArgumentException("Only one parent is expected!");
        }
        if (!(parents[0] instanceof AuthenticationProvider))
        {
            throw new IllegalArgumentException("Parent is not a AuthenticationProvider");
        }
        AuthenticationProvider authenticationProvider = (AuthenticationProvider)parents[0];
        return _preferencesProviderCreator.recover(entry.getId(), entry.getAttributes(), authenticationProvider);
    }

}
