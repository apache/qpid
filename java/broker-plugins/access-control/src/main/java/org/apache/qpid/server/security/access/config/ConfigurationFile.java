package org.apache.qpid.server.security.access.config;

import java.io.File;

import org.apache.commons.configuration.ConfigurationException;

public interface ConfigurationFile
{
    /**
     * Return the actual {@link File}  object containing the configuration.
     */
    File getFile();
    
    /**
     * Load this configuration file's contents into a {@link RuleSet}.
     * 
     * @throws ConfigurationException if the configuration file has errors.
     * @throws IllegalArgumentException if individual tokens cannot be parsed.
     */
    RuleSet load() throws ConfigurationException;
    
    /**
     * Reload this configuration file's contents.
     * 
     * @throws ConfigurationException if the configuration file has errors.
     * @throws IllegalArgumentException if individual tokens cannot be parsed.
     */
    RuleSet reload() throws ConfigurationException;
        
    RuleSet getConfiguration();
    
    /**
     * TODO document me.
     */
    boolean save(RuleSet configuration);
}
