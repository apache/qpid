package org.apache.qpid.server.model.adapter;

import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.plugin.PreferencesProviderFactory;

public class FileSystemPreferencesProviderFactory implements PreferencesProviderFactory
{

    @Override
    public String getType()
    {
        return FileSystemPreferencesProvider.PROVIDER_TYPE;
    }

    @Override
    public PreferencesProvider createInstance(UUID id, Map<String, Object> attributes,
            AuthenticationProvider authenticationProvider)
    {
        Broker broker = authenticationProvider.getParent(Broker.class);
        FileSystemPreferencesProvider provider = new FileSystemPreferencesProvider(id, attributes, authenticationProvider, broker.getTaskExecutor());

        // create store if such does not exist
        provider.createStoreIfNotExist();
        return provider;
    }

}
