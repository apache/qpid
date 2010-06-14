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
package org.apache.qpid.server.configuration;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TopicConfiguration extends ConfigurationPlugin
{
    public static final ConfigurationPluginFactory FACTORY = new TopicConfigurationFactory();

    private static final String VIRTUALHOSTS_VIRTUALHOST_TOPICS = "virtualhosts.virtualhost.topics";

    public static class TopicConfigurationFactory implements ConfigurationPluginFactory
    {

        public ConfigurationPlugin newInstance(String path, Configuration config) throws ConfigurationException
        {
            TopicConfiguration topicsConfig = new TopicConfiguration();
            topicsConfig.setConfiguration(path, config);
            return topicsConfig;
        }

        public List<String> getParentPaths()
        {
            return Arrays.asList(VIRTUALHOSTS_VIRTUALHOST_TOPICS);
        }
    }

    Map<String, TopicConfig> _topics = new HashMap<String, TopicConfig>();

    public String[] getElementsProcessed()
    {
        return new String[]{"topic"};
    }

    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        if (_configuration.isEmpty())
        {
            throw new ConfigurationException("Topics section cannot be empty.");
        }

        int topics = _configuration.getList("topic.name").size();

        for(int index=0; index<topics;index++)
        {
            TopicConfig topic = new TopicConfig();
            topic.setConfiguration(VIRTUALHOSTS_VIRTUALHOST_TOPICS + ".topic", _configuration.subset("topic(" + index + ")"));

            String topicName = _configuration.getString("topic(" + index + ").name");
            if(_topics.containsKey(topicName))
            {
                throw new ConfigurationException("Topics section cannot contain two entries for the same topic.");                
            }
            else
            {
                _topics.put(topicName, topic);
            }
        }
    }

    public String toString()
    {
        return getClass().getName() + ": Defined Topics:" + _topics.size();
    }

    public static class TopicConfig extends ConfigurationPlugin
    {
        @Override
        public String[] getElementsProcessed()
        {
            return new String[]{"name"};
        }

        public String getName()
        {
            // If we don't specify a topic name then match all topics
            String configName = getStringValue("name");
            return configName == null ? "#" : configName;
        }


        public void validateConfiguration() throws ConfigurationException
        {
            if(_configuration.isEmpty())
            {
                throw new ConfigurationException("Topic section cannot be empty.");
            }
        }
    }

}
