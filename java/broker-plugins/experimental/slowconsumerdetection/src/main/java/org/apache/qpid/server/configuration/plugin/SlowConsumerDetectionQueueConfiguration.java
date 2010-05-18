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
package org.apache.qpid.server.configuration.plugin;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.slowconsumerdetection.policies.SlowConsumerPolicyPlugin;
import org.apache.qpid.slowconsumerdetection.policies.SlowConsumerPolicyPluginFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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

        public String[] getParentPaths()
        {
            return new String[]{"virtualhosts.virtualhost.queues.slow-consumer-detection",
                                "virtualhosts.virtualhost.queues.queue.slow-consumer-detection"};
        }

    }

    public String[] getElementsProcessed()
    {
        return new String[]{"messageAge",
                            "depth",
                            "messageCount"};
    }

    public int getMessageAge()
    {
        return (int) getConfigurationValue("messageAge");
    }

    public long getDepth()
    {
        return getConfigurationValue("depth");
    }

    public long getMessageCount()
    {
        return getConfigurationValue("messageCount");
    }

    public SlowConsumerPolicyPlugin getPolicy()
    {
        return _policyPlugin;
    }

    @Override
    public void setConfiguration(String path, Configuration configuration) throws ConfigurationException
    {
        super.setConfiguration(path, configuration);

        SlowConsumerDetectionPolicyConfiguration policyConfig = getConfiguration(SlowConsumerDetectionPolicyConfiguration.class);

        PluginManager pluginManager = ApplicationRegistry.getInstance().getPluginManager();
        Map<String, SlowConsumerPolicyPluginFactory> factories =
                pluginManager.getPlugins(SlowConsumerPolicyPluginFactory.class);

        Iterator<?> keys = policyConfig.getConfig().getKeys();

        while (keys.hasNext())
        {
            String key = (String) keys.next();

            _logger.debug("Policy Keys:" + key);

        }

        if (policyConfig == null)
        {
            throw new ConfigurationException("No Slow Consumer Policy specified at:" + path + ". Known Policies:" + factories.keySet());
        }        

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Configured SCDQC");
            _logger.debug("Age:" + getMessageAge());
            _logger.debug("Depth:" + getDepth());
            _logger.debug("Count:" + getMessageCount());
            _logger.debug("Policy:" + policyConfig.getPolicyName());
            _logger.debug("Available factories:" + factories);
        }

        SlowConsumerPolicyPluginFactory pluginFactory = factories.get(policyConfig.getPolicyName().toLowerCase());

        if (pluginFactory == null)
        {
            throw new ConfigurationException("Unknown Slow Consumer Policy specified:" + policyConfig.getPolicyName() + " Known Policies:" + factories.keySet());
        }

        _policyPlugin = pluginFactory.newInstance(policyConfig);
    }

    private long getConfigurationValue(String property)
    {
        // The _configuration we are given is a munged configurated
        // so the queue will already have queue-queues munging

        // we then need to ensure that the TopicsConfiguration
        // and TopicConfiguration classes correctly munge their configuration:
        // queue-queues -> topic-topics

        return _configuration.getLong(property, 0);
    }

}
