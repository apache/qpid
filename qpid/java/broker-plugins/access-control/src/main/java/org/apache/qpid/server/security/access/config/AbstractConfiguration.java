/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */
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
