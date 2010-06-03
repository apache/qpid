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

import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.access.config.FirewallRule;

public class FirewallConfiguration extends ConfigurationPlugin
{
    CompositeConfiguration _finalConfig;

    public static final ConfigurationPluginFactory FACTORY = new ConfigurationPluginFactory()
    {
        public ConfigurationPlugin newInstance(String path, Configuration config) throws ConfigurationException
        {
            ConfigurationPlugin instance = new FirewallConfiguration();
            instance.setConfiguration(path, config);
            return instance;
        }

        public List<String> getParentPaths()
        {
            return Arrays.asList("security.firewall", "virtualhosts.virtualhost.security.firewall");
        }
    };

    public String[] getElementsProcessed()
    {
        return new String[] { "" };
    }

    public Configuration getConfiguration()
    {
        return _finalConfig;
    }

    public Result getDefaultAction()
    {
        String defaultAction = _configuration.getString("[@default-action]");
        if (defaultAction == null)
        {
            return Result.ABSTAIN;
        }
        else if (defaultAction.equalsIgnoreCase(FirewallRule.ALLOW))
        {
            return Result.ALLOWED;
        }
        else
        {
            return Result.DENIED;
        }
    }



    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        // Valid Configuration either has xml links to new files
        _finalConfig = new CompositeConfiguration(_configuration);
        List subFiles = _configuration.getList("xml[@fileName]");
        for (Object subFile : subFiles)
        {
            _finalConfig.addConfiguration(new XMLConfiguration((String) subFile));
        }

        // all rules must have an access attribute or a default value
        if (_finalConfig.getList("rule[@access]").size() == 0 &&
            _configuration.getString("[@default-action]") == null)
        {
            throw new ConfigurationException("No rules or default-action found in firewall configuration.");
        }
    }

}
