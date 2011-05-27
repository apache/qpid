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
package org.apache.qpid.systest;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.AMQChannelClosedException;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQSession_0_10;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MergeConfigurationTest extends TestingBaseCase
{

    protected int topicCount = 0;


    public void configureTopic(String topic, int msgCount) throws NamingException, IOException, ConfigurationException
    {

        setProperty(".topics.topic("+topicCount+").name", topic);
        setProperty(".topics.topic("+topicCount+").slow-consumer-detection.messageCount", String.valueOf(msgCount));
        setProperty(".topics.topic("+topicCount+").slow-consumer-detection.policy.name", "TopicDelete");
        topicCount++;
    }


    /**
     * Test that setting messageCount takes affect on topics
     *
     * We send 10 messages and disconnect at 9
     *
     * @throws Exception
     */
    public void testTopicConsumerMessageCount() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        configureTopic(getName(), (MAX_QUEUE_MESSAGE_COUNT * 4) - 1);

        //Configure topic as a subscription
        setProperty(".topics.topic("+topicCount+").subscriptionName", "clientid:"+getTestQueueName());
        configureTopic(getName(), (MAX_QUEUE_MESSAGE_COUNT - 1));



        //Start the broker
        startBroker();

        topicConsumer(Session.AUTO_ACKNOWLEDGE, true);
    }


//
//    public void testMerge() throws ConfigurationException, AMQException
//    {
//
//        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString(getName()+":stockSubscription"), false, new AMQShortString("testowner"),
//                                                    false, false, _virtualHost, null);
//
//        _virtualHost.getQueueRegistry().registerQueue(queue);
//        Exchange defaultExchange = _virtualHost.getExchangeRegistry().getDefaultExchange();
//        _virtualHost.getBindingFactory().addBinding(getName(), queue, defaultExchange, null);
//
//
//        Exchange topicExchange = _virtualHost.getExchangeRegistry().getExchange(ExchangeDefaults.TOPIC_EXCHANGE_NAME);
//        _virtualHost.getBindingFactory().addBinding("stocks.nyse.orcl", queue, topicExchange, null);
//
//        TopicConfig config = queue.getConfiguration().getConfiguration(TopicConfig.class.getName());
//
//        assertNotNull("Queue should have topic configuration bound to it.", config);
//        assertEquals("Configuration name not correct", getName() + ":stockSubscription", config.getSubscriptionName());
//
//        ConfigurationPlugin scdConfig = queue.getConfiguration().getConfiguration(SlowConsumerDetectionQueueConfiguration.class.getName());
//        if (scdConfig instanceof org.apache.qpid.server.configuration.plugin.SlowConsumerDetectionQueueConfiguration)
//        {
//            System.err.println("********************** scd is a SlowConsumerDetectionQueueConfiguration.");
//        }
//        else
//        {
//            System.err.println("********************** Test SCD "+SlowConsumerDetectionQueueConfiguration.class.getClassLoader());
//            System.err.println("********************** Broker SCD "+scdConfig.getClass().getClassLoader());
//                 System.err.println("********************** Broker SCD "+scdConfig.getClass().isAssignableFrom(SlowConsumerDetectionQueueConfiguration.class));
//            System.err.println("********************** is a "+scdConfig.getClass());
//        }
//
//        assertNotNull("Queue should have scd configuration bound to it.", scdConfig);
//        assertEquals("MessageCount is not correct", 10 , ((SlowConsumerDetectionQueueConfiguration)scdConfig).getMessageCount());
//        assertEquals("Policy is not correct", TopicDeletePolicy.class.getName() , ((SlowConsumerDetectionQueueConfiguration)scdConfig).getPolicy().getClass().getName());
//    }

}
