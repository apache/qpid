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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInternalException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

/**
 * Test of the new Topic configuration processing
 */
public class TopicConfigurationTest extends InternalBrokerBaseCase
{

    @Override
    public void configure()
    {
        getConfigXml().addProperty("virtualhosts.virtualhost.test.topics.topic.name", "stocks.nyse.appl");

        getConfigXml().addProperty("virtualhosts.virtualhost.test.topics.topic(1).subscriptionName", getName()+":stockSubscription");

        getConfigXml().addProperty("virtualhosts.virtualhost.test.topics.topic(2).name", "stocks.nyse.orcl");
        getConfigXml().addProperty("virtualhosts.virtualhost.test.topics.topic(2).subscriptionName", getName()+":stockSubscription");
   }

    /**
     * Test that a TopicConfig object is created and attached to the queue when it is bound to the topic exchange.
     *

     * @throws ConfigurationException
     * @throws AMQSecurityException
     */
    public void testTopicCreation() throws ConfigurationException, AMQSecurityException, AMQInternalException
    {
        Exchange topicExchange = getVirtualHost().getExchangeRegistry().getExchange(ExchangeDefaults.TOPIC_EXCHANGE_NAME);
        getVirtualHost().getBindingFactory().addBinding("stocks.nyse.appl", getQueue(), topicExchange, null);

        TopicConfig config = getQueue().getConfiguration().getConfiguration(TopicConfig.class.getName());

        assertNotNull("Queue should have topic configuration bound to it.", config);
        assertEquals("Configuration name not correct", "stocks.nyse.appl", config.getName());
    }

    /**
     * Test that a queue created for a subscription correctly has topic
     * configuration selected based on the subscription and topic name.
     *
     * @throws ConfigurationException
     * @throws AMQException
     */
    public void testSubscriptionWithTopicCreation() throws ConfigurationException, AMQException
    {

        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString(getName()+":stockSubscription"), false, new AMQShortString("testowner"),
                                                    false, false, getVirtualHost(), null);

        getVirtualHost().getQueueRegistry().registerQueue(queue);
        Exchange defaultExchange = getVirtualHost().getExchangeRegistry().getDefaultExchange();
        getVirtualHost().getBindingFactory().addBinding(getName(), queue, defaultExchange, null);


        Exchange topicExchange = getVirtualHost().getExchangeRegistry().getExchange(ExchangeDefaults.TOPIC_EXCHANGE_NAME);
        getVirtualHost().getBindingFactory().addBinding("stocks.nyse.orcl", queue, topicExchange, null);

        TopicConfig config = queue.getConfiguration().getConfiguration(TopicConfig.class.getName());

        assertNotNull("Queue should have topic configuration bound to it.", config);
        assertEquals("Configuration subscription name not correct", getName() + ":stockSubscription", config.getSubscriptionName());
        assertEquals("Configuration name not correct", "stocks.nyse.orcl", config.getName());

    }

    /**
     * Test that a queue created for a subscription correctly has topic
     * configuration attached here this should be the generic topic section
     * with just the subscriptionName
     *
     * @throws ConfigurationException
     * @throws AMQException
     */
    public void testSubscriptionCreation() throws ConfigurationException, AMQException
    {

        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString(getName()+":stockSubscription"), false, new AMQShortString("testowner"),
                                                    false, false, getVirtualHost(), null);

        getVirtualHost().getQueueRegistry().registerQueue(queue);
        Exchange defaultExchange = getVirtualHost().getExchangeRegistry().getDefaultExchange();
        getVirtualHost().getBindingFactory().addBinding(getName(), queue, defaultExchange, null);


        Exchange topicExchange = getVirtualHost().getExchangeRegistry().getExchange(ExchangeDefaults.TOPIC_EXCHANGE_NAME);
        getVirtualHost().getBindingFactory().addBinding("stocks.nyse.ibm", queue, topicExchange, null);

        TopicConfig config = queue.getConfiguration().getConfiguration(TopicConfig.class.getName());

        assertNotNull("Queue should have topic configuration bound to it.", config);        
        assertEquals("Configuration subscription name not correct", getName() + ":stockSubscription", config.getSubscriptionName());
        assertEquals("Configuration name not correct", "#", config.getName());

    }



}
