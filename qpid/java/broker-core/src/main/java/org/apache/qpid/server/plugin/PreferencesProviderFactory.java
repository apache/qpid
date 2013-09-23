package org.apache.qpid.server.plugin;

import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.PreferencesProvider;

public interface PreferencesProviderFactory extends Pluggable
{
    PreferencesProvider createInstance(UUID id, Map<String, Object> attributes, AuthenticationProvider authenticationProvider);
}
