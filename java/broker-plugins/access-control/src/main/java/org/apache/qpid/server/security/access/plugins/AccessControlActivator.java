package org.apache.qpid.server.security.access.plugins;

import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.security.SecurityPluginActivator;
import org.apache.qpid.server.security.SecurityPluginFactory;
import org.osgi.framework.BundleActivator;

/**
 * The OSGi {@link BundleActivator} for {@link AccessControl}.
 */
public class AccessControlActivator extends SecurityPluginActivator
{
	public SecurityPluginFactory getFactory()
	{
	    return AccessControl.FACTORY;
	}

    public ConfigurationPluginFactory getConfigurationFactory()
    {
        return AccessControlConfiguration.FACTORY;
    }
}
