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

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.security.AbstractPlugin;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.SecurityPluginFactory;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.config.FirewallException;
import org.apache.qpid.server.security.access.config.FirewallRule;

public class Firewall extends AbstractPlugin
{
    public static final SecurityPluginFactory<Firewall> FACTORY = new SecurityPluginFactory<Firewall>()
    {
        public Firewall newInstance(ConfigurationPlugin config) throws ConfigurationException
        {
            FirewallConfiguration configuration = config.getConfiguration(FirewallConfiguration.class.getName());

            // If there is no configuration for this plugin then don't load it.
            if (configuration == null)
            {
                return null;
            }
            
            Firewall plugin = new Firewall();
            plugin.configure(configuration);
            return plugin;
        }
        
        public Class<Firewall> getPluginClass()
        {
            return Firewall.class;
        }

        public String getPluginName()
        {
            return Firewall.class.getName();
        }
    };
	
    private Result _default = Result.ABSTAIN;
    private FirewallRule[] _rules;
    
	public Result getDefault()
	{
		return _default;
	}

    public Result authorise(Operation operation, ObjectType objectType, ObjectProperties properties)
    {
        return Result.ABSTAIN; // We only deal with access requests
    }

    public Result access(ObjectType objectType, Object instance)
    {
        if (objectType != ObjectType.VIRTUALHOST)
        {
            return Result.ABSTAIN; // We are only interested in access to virtualhosts
        }

        if (!(instance instanceof InetSocketAddress))
        {
            return Result.ABSTAIN; // We need an internet address
        }

        InetAddress address = ((InetSocketAddress) instance).getAddress();
        
        try
        {
            for (FirewallRule rule : _rules)
            {
                boolean match = rule.match(address);
                if (match)
                {
                    return rule.getAccess();
                }
            }
            return getDefault();
        }
        catch (FirewallException fe)
        {
            return Result.DENIED;
        }
    }
    

    public void configure(ConfigurationPlugin config)
    {
        super.configure(config);
        FirewallConfiguration firewallConfiguration = (FirewallConfiguration) _config;

        // Get default action
        _default = firewallConfiguration.getDefaultAction();

        Configuration finalConfig = firewallConfiguration.getConfiguration();

        // all rules must have an access attribute
        int numRules = finalConfig.getList("rule[@access]").size();
        _rules = new FirewallRule[numRules];
        for (int i = 0; i < numRules; i++)
        {
            FirewallRule rule = new FirewallRule(finalConfig.getString("rule(" + i + ")[@access]"),
                                                 finalConfig.getList("rule(" + i + ")[@network]"),
                                                 finalConfig.getList("rule(" + i + ")[@hostname]"));
            _rules[i] = rule;
        }

    }
}
