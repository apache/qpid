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

import java.util.List;

public class SlowConsumerDetectionPolicyConfiguration extends ConfigurationPlugin
{

    public static class SlowConsumerDetectionPolicyConfigurationFactory
            implements ConfigurationPluginFactory
    {
        public ConfigurationPlugin newInstance(String path,
                                               Configuration config)
                throws ConfigurationException
        {
            SlowConsumerDetectionPolicyConfiguration slowConsumerConfig =
                    new SlowConsumerDetectionPolicyConfiguration();
            slowConsumerConfig.setConfiguration(path, config);
            return slowConsumerConfig;
        }

        public String[] getParentPaths()
        {
            return new String[]{
                    "virtualhosts.virtualhost.queues.slow-consumer-detection.policy",
                    "virtualhosts.virtualhost.queues.queue.slow-consumer-detection.policy",
                    "virtualhosts.virtualhost.topics.slow-consumer-detection.policy",
                    "virtualhosts.virtualhost.queues.topics.topic.slow-consumer-detection.policy"};
        }
    }

    public String[] getElementsProcessed()
    {
        // NOTE: the use of '@name]' rather than '[@name]' this appears to be
        // a bug in commons configuration.
        //fixme - Simple test case needs raised and JIRA raised on Commons
        return new String[]{"@name]", "options"};
    }

    public String getPolicyName()
    {
        return _configuration.getString("[@name]");
    }

    public String getOption(String option)
    {
        List options = _configuration.getList("options.option[@name]");

        if (options != null && options.contains(option))
        {
            return _configuration.getString("options.option[@value]" +
                                            "(" + options.indexOf(option) + ")");
        }

        return null;
    }
}
