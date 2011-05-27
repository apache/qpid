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
package org.apache.qpid.server.security.access.plugins;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.security.access.config.ConfigurationFile;
import org.apache.qpid.server.security.access.config.PlainConfiguration;
import org.apache.qpid.server.security.access.config.RuleSet;

public class AccessControlConfiguration extends ConfigurationPlugin
{
    public static final ConfigurationPluginFactory FACTORY = new ConfigurationPluginFactory() 
    {
        public ConfigurationPlugin newInstance(String path, Configuration config) throws ConfigurationException
        {
            ConfigurationPlugin instance = new AccessControlConfiguration();
            instance.setConfiguration(path, config);
            return instance;
        }

        public List<String> getParentPaths()
        {
            return Arrays.asList("security.aclv2", "virtualhosts.virtualhost.security.aclv2");
        }
    };

    private RuleSet _ruleSet;

    public String[] getElementsProcessed()
    {
        return new String[] { "" };
    }

    public String getFileName()
    {
        return _configuration.getString("");
    }

    public void validateConfiguration() throws ConfigurationException
    {
        String filename = getFileName();
        if (filename == null)
        {
            throw new ConfigurationException("No ACL file name specified");
        }

        File aclFile = new File(filename);
        
        ConfigurationFile configFile = new PlainConfiguration(aclFile);
        _ruleSet = configFile.load();
    }

    public RuleSet getRuleSet()
    {
        return _ruleSet;
    }

}
