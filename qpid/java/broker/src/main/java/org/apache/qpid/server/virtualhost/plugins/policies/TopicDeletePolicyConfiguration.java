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
package org.apache.qpid.server.virtualhost.plugins.policies;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;

public class TopicDeletePolicyConfiguration extends ConfigurationPlugin
{

    public static class TopicDeletePolicyConfigurationFactory
            implements ConfigurationPluginFactory
    {
        public ConfigurationPlugin newInstance(String path,
                                               Configuration config)
                throws ConfigurationException
        {
            TopicDeletePolicyConfiguration slowConsumerConfig =
                    new TopicDeletePolicyConfiguration();
            slowConsumerConfig.setConfiguration(path, config);
            return slowConsumerConfig;
        }

        public List<String> getParentPaths()
        {
            return Arrays.asList(
                    "virtualhosts.virtualhost.queues.slow-consumer-detection.policy.topicDelete",
                    "virtualhosts.virtualhost.queues.queue.slow-consumer-detection.policy.topicDelete",
                    "virtualhosts.virtualhost.topics.slow-consumer-detection.policy.topicDelete",
                    "virtualhosts.virtualhost.topics.topic.slow-consumer-detection.policy.topicDelete");
        }
    }

    public String[] getElementsProcessed()
    {
        return new String[]{"delete-persistent"};
    }

    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        // No validation required.
    }

    public boolean deletePersistent()
    {
        // If we don't have configuration then we don't deletePersistent Queues 
        return (hasConfiguration() && contains("delete-persistent"));
    }

    @Override
    public String formatToString()
    {
        return (deletePersistent()?"delete-durable":"");
    }


}
