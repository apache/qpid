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
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.queue.AMQQueue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TopicConfiguration extends ConfigurationPlugin implements ExchangeConfigurationPlugin
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
    Map<String,  Map<String, TopicConfig>> _subscriptions = new HashMap<String,  Map<String, TopicConfig>>();

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

        int topics = _configuration.getList("topic.name").size() +
                     _configuration.getList("topic.subscriptionName").size();

        for (int index = 0; index < topics; index++)
        {
            Configuration topicSubset = _configuration.subset("topic(" + index + ")");

            // This will occur when we have a subscriptionName that is bound to a
            // topic.
            if (topicSubset.isEmpty())
            {
                break;
            }

            TopicConfig topic = new TopicConfig();

            topic.setConfiguration(VIRTUALHOSTS_VIRTUALHOST_TOPICS + ".topic", topicSubset );

            String name = _configuration.getString("topic(" + index + ").name");
            String subscriptionName = _configuration.getString("topic(" + index + ").subscriptionName");

            // Record config if subscriptionName is there
            if (subscriptionName != null)
            {
                processSubscription(subscriptionName, topic);
            }
            else
            {
                // Otherwise record config as topic if we have the name
                if (name != null)
                {
                    processTopic(name, topic);
                }
            }
        }
    }

    /**
     * @param name
     * @param topic
     *
     * @throws org.apache.commons.configuration.ConfigurationException
     *
     */
    private void processTopic(String name, TopicConfig topic) throws ConfigurationException
    {
        if (_topics.containsKey(name))
        {
            throw new ConfigurationException("Topics section cannot contain two entries for the same topic.");
        }
        else
        {
            _topics.put(name, topic);
        }
    }


    private void processSubscription(String name, TopicConfig topic) throws ConfigurationException
    {
        Map<String,TopicConfig> topics;
        if (_subscriptions.containsKey(name))
        {
            topics = _subscriptions.get(name);

            if (topics.containsKey(topic.getName()))
            {
                throw new ConfigurationException("Subcription cannot contain two entries for the same topic.");
            }
        }
        else
        {
            topics = new HashMap<String,TopicConfig>();
        }

        topics.put(topic.getName(),topic);
        _subscriptions.put(name, topics);

    }

    @Override
    public String formatToString()
    {
        return "Topics:" + _topics + ", Subscriptions:" + _subscriptions;
    }

    /**
     * This processes the given queue and apply configuration in the following
     * order:
     *
     * Global Topic Values -> Topic Values -> Subscription Values
     *
     * @param queue
     *
     * @return
     */
    public ConfigurationPlugin getConfiguration(AMQQueue queue)
    {
        //Create config with global topic configuration
        TopicConfig config = new TopicConfig();

        // Add global topic configuration
        config.addConfiguration(this);

        // Process Topic Bindings as these are more generic than subscriptions
        List<TopicConfig> boundToTopics = new LinkedList<TopicConfig>();

        //Merge the configuration in the order that they are bound
        for (Binding binding : queue.getBindings())
        {
            if (binding.getExchange().getType().equals(TopicExchange.TYPE))
            {
                // Identify topic for the binding key
                TopicConfig topicConfig = getTopicConfigForRoutingKey(binding.getBindingKey());
                if (topicConfig != null)
                {
                    boundToTopics.add(topicConfig);
                }
            }
        }

        // If the Queue is bound to a number of topics then only use the global
        // topic configuration.
        // todo - What does it mean in terms of configuration to be bound to a
        // number of topics? Do we try and merge?
        // YES - right thing to do would be to merge from generic to specific.
        // Means we need to be able to get an ordered list of topics for this
        // binding.
        if (boundToTopics.size() == 1)
        {
            config.addConfiguration(boundToTopics.get(0));
        }

        // If we have a subscription then attempt to look it up.
        String subscriptionName = queue.getName();

        // Apply subscription configurations
        if (_subscriptions.containsKey(subscriptionName))
        {

            //Get all the Configuration that this subscription is bound to.
            Map<String, TopicConfig> topics = _subscriptions.get(subscriptionName);

            TopicConfig subscriptionSpecificConfig = null;

            // See if we have a TopicConfig in topics for a topic we are bound to.
            for (Binding binding : queue.getBindings())
            {
                if (binding.getExchange().getType().equals(TopicExchange.TYPE))
                {
                    //todo - What does it mean to have multiple matches?
                    // Take the first match we get
                    if (subscriptionSpecificConfig == null)
                    {
                        // lookup the binding to see if we have a match in the subscription configs
                        subscriptionSpecificConfig = topics.get(binding.getBindingKey());
                    }
                }
            }

            //todo we don't account for wild cards here. only explicit matching and all subscriptions
            if (subscriptionSpecificConfig == null)
            {
                // lookup the binding to see if we have a match in the subscription configs
                subscriptionSpecificConfig = topics.get("#");
            }

            // Apply subscription specific config.
            if (subscriptionSpecificConfig != null)
            {
                config.addConfiguration(subscriptionSpecificConfig);
            }
        }
        return config;
    }

    /**
     * This method should perform the same heuristics as the TopicExchange
     * to attempt to identify a piece of configuration for the give routingKey.
     *
     * i.e. If we have 'stocks.*' defined in the config
     * and we bind 'stocks.appl' then we should return the 'stocks.*'
     * configuration.
     *
     * @param routingkey the key to lookup
     *
     * @return the TopicConfig if found.
     */
    private TopicConfig getTopicConfigForRoutingKey(String routingkey)
    {
        //todo actually perform TopicExchange style lookup not just straight
        // lookup as we are just now.
        return _topics.get(routingkey);
    }

}
