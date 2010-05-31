package org.apache.qpid.server.security.access.plugins;

import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.security.SecurityPluginActivator;
import org.apache.qpid.server.security.SecurityPluginFactory;
import org.osgi.framework.BundleActivator;

/**
 * The OSGi {@link BundleActivator} for {@link Firewall}.
 */
public class FirewallActivator extends SecurityPluginActivator
{
    public SecurityPluginFactory getFactory()
    {
        return Firewall.FACTORY;
    }

    public ConfigurationPluginFactory getConfigurationFactory()
    {
        return FirewallConfiguration.FACTORY;
    }
}
