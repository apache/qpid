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

import java.util.Arrays;
import java.util.List;

public class SlowConsumerDetectionPolicyConfiguration extends ConfigurationPlugin
{
    public static class SlowConsumerDetectionPolicyConfigurationFactory implements ConfigurationPluginFactory
    {
        public ConfigurationPlugin newInstance(String path, Configuration config) throws ConfigurationException
        {
            SlowConsumerDetectionPolicyConfiguration slowConsumerConfig = new SlowConsumerDetectionPolicyConfiguration();
            slowConsumerConfig.setConfiguration(path, config);
            return slowConsumerConfig;
        }

        public List<String> getParentPaths()
        {
            return Arrays.asList(
                    "virtualhosts.virtualhost.queues.slow-consumer-detection.policy",
                    "virtualhosts.virtualhost.queues.queue.slow-consumer-detection.policy",
                    "virtualhosts.virtualhost.topics.slow-consumer-detection.policy",
                    "virtualhosts.virtualhost.topics.topic.slow-consumer-detection.policy");
        }
    }

    public String[] getElementsProcessed()
    {
        return new String[]{"name"};
    }

    public String getPolicyName()
    {
        return getStringValue("name");
    }

    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        if (getPolicyName() == null)
        {
            throw new ConfigurationException("No Slow consumer policy defined.");
        }
    }

    @Override
    public String formatToString()
    {
        return "Policy:"+getPolicyName();
    }
}
