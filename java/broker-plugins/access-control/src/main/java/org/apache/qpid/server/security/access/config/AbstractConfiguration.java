package org.apache.qpid.server.security.access.config;

import java.io.File;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;

public abstract class AbstractConfiguration implements ConfigurationFile
{
    protected static final Logger _logger = Logger.getLogger(ConfigurationFile.class);
    
    protected File _file;
    protected RuleSet _config;
    
    public AbstractConfiguration(File file)
    {
        _file = file;
    }
    
    public File getFile()
    {
        return _file;
    }
    
    public RuleSet load() throws ConfigurationException
    {
        _config = new RuleSet();
        return _config;
    }
    
    public RuleSet getConfiguration()
    {
        return _config;
    }
        
    public boolean save(RuleSet configuration)
    {
        return true;
    }
    
    public RuleSet reload()
    {
        RuleSet oldRules = _config;
        
        try
        {
            RuleSet newRules = load();
            _config = newRules;
        }
        catch (Exception e)
        {
            _config = oldRules;
        }
        
        return _config;
    }
}
