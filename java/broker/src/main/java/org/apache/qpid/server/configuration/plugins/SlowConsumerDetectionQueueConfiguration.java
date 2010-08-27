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
package org.apache.qpid.server.configuration.plugins;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.slowconsumerdetection.policies.SlowConsumerPolicyPlugin;
import org.apache.qpid.slowconsumerdetection.policies.SlowConsumerPolicyPluginFactory;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SlowConsumerDetectionQueueConfiguration extends ConfigurationPlugin
{
    private SlowConsumerPolicyPlugin _policyPlugin;

    public static class SlowConsumerDetectionQueueConfigurationFactory implements ConfigurationPluginFactory
    {
        public ConfigurationPlugin newInstance(String path, Configuration config) throws ConfigurationException
        {
            SlowConsumerDetectionQueueConfiguration slowConsumerConfig = new SlowConsumerDetectionQueueConfiguration();
            slowConsumerConfig.setConfiguration(path, config);
            return slowConsumerConfig;
        }

        public List<String> getParentPaths()
        {
            return Arrays.asList(
                    "virtualhosts.virtualhost.queues.slow-consumer-detection",
                    "virtualhosts.virtualhost.queues.queue.slow-consumer-detection",
                    "virtualhosts.virtualhost.topics.slow-consumer-detection",
                    "virtualhosts.virtualhost.topics.topic.slow-consumer-detection");
        }
    }

    public String[] getElementsProcessed()
    {
        return new String[]{"messageAge",
                            "depth",
                            "messageCount"};
    }

    public long getMessageAge()
    {
        return getLongValue("messageAge");
    }

    public long getDepth()
    {
        return getLongValue("depth");
    }

    public long getMessageCount()
    {
        return getLongValue("messageCount");
    }

    public SlowConsumerPolicyPlugin getPolicy()
    {
        return _policyPlugin;
    }

    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        if (!containsPositiveLong("messageAge") &&
            !containsPositiveLong("depth") &&
            !containsPositiveLong("messageCount"))
        {
            throw new ConfigurationException("At least one configuration property" +
                                             "('messageAge','depth' or 'messageCount') must be specified.");
        }

        SlowConsumerDetectionPolicyConfiguration policyConfig = getConfiguration(SlowConsumerDetectionPolicyConfiguration.class.getName());

        PluginManager pluginManager = ApplicationRegistry.getInstance().getPluginManager();
        Map<String, SlowConsumerPolicyPluginFactory> factories = pluginManager.getSlowConsumerPlugins();

        if (policyConfig == null)
        {
            throw new ConfigurationException("No Slow Consumer Policy specified. Known Policies:" + factories.keySet());
        }

        if (_logger.isDebugEnabled())
        {
            Iterator<?> keys = policyConfig.getConfig().getKeys();

            while (keys.hasNext())
            {
                String key = (String) keys.next();

                _logger.debug("Policy Keys:" + key);
            }

        }

        SlowConsumerPolicyPluginFactory<SlowConsumerPolicyPlugin> pluginFactory = factories.get(policyConfig.getPolicyName().toLowerCase());

        if (pluginFactory == null)
        {
            throw new ConfigurationException("Unknown Slow Consumer Policy specified:" + policyConfig.getPolicyName() + " Known Policies:" + factories.keySet());
        }

        _policyPlugin = pluginFactory.newInstance(policyConfig);

        // Debug the creation of this Config
        _logger.debug(this);
    }

    public String formatToString()
    {
        StringBuilder sb = new StringBuilder();
        if (getMessageAge() > 0)
        {
            sb.append("Age:").append(getMessageAge()).append(":");
        }
        if (getDepth() > 0)
        {
            sb.append("Depth:").append(getDepth()).append(":");
        }
        if (getMessageCount() > 0)
        {
            sb.append("Count:").append(getMessageCount()).append(":");
        }

        sb.append("Policy[").append(getPolicy()).append("]");
        return sb.toString();
    }
}
