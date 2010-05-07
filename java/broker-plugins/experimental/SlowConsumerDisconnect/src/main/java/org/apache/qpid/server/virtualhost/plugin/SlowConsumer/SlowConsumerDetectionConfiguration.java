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
package org.apache.qpid.server.virtualhost.plugin.SlowConsumer;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.plugin.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugin.ConfigurationPluginFactory;

import java.util.concurrent.TimeUnit;

public class SlowConsumerDetectionConfiguration extends ConfigurationPlugin
{
    public static class SlowConsumerDetectionConfigurationFactory implements ConfigurationPluginFactory
    {
        public ConfigurationPlugin newInstance(String path, Configuration config) throws ConfigurationException
        {
            SlowConsumerDetectionConfiguration slowConsumerConfig = new SlowConsumerDetectionConfiguration();
            slowConsumerConfig.setConfiguration(path, config);
            return slowConsumerConfig;
        }

        public String[] getParentPaths()
        {
            return new String[]{"virtualhosts.virtualhost.slow-consumer-detection"};
        }
    }

    public String[] getElementsProcessed()
    {
        return new String[]{"delay",
                            "timeunit"};
    }

    public long getDelay()
    {
        return _configuration.getLong("delay", 10);
    }

    public String getTimeUnit()
    {
        return _configuration.getString("timeunit", TimeUnit.SECONDS.toString());
    }

    @Override
    public void setConfiguration(String path, Configuration configuration) throws ConfigurationException
    {
        super.setConfiguration(path, configuration);

        System.out.println("Configured SCDC");
        System.out.println("Delay:" + getDelay());
        System.out.println("TimeUnit:" + getTimeUnit());
    }
}
