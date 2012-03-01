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

import javax.jms.Session;
import javax.naming.NamingException;
import java.io.IOException;

/**
 * QPID-1447 : Add slow consumer detection and disconnection.
 *
 * Slow consumers should on a topic should expect to receive a
 * 506 : Resource Error if the hit a predefined threshold.
 */
public class GlobalQueuesTest extends TestingBaseCase
{

    protected String CONFIG_SECTION = ".queues";

    /**
     *  Queue Configuration

     <slow-consumer-detection>
     <!-- The depth before which the policy will be applied-->
     <depth>4235264</depth>

     <!-- The message age before which the policy will be applied-->
     <messageAge>600000</messageAge>

     <!-- The number of message before which the policy will be applied-->
     <messageCount>50</messageCount>

     <!-- Policies configuration -->
     <policy>
     <name>TopicDelete</name>
     <topicDelete>
     <delete-persistent/>
     </topicDelete>
     </policy>
     </slow-consumer-detection>

     */


    /**
     *  VirtualHost Plugin Configuration

     <slow-consumer-detection>
     <delay>1</delay>
     <timeunit>MINUTES</timeunit>
     </slow-consumer-detection>

     */

    public void setConfig(String property, String value, boolean deleteDurable) throws NamingException, IOException, ConfigurationException
    {
        setProperty(CONFIG_SECTION + ".slow-consumer-detection." +
                    "policy.name", "TopicDelete");

        setProperty(CONFIG_SECTION + ".slow-consumer-detection." +
                    property, value);

        if (deleteDurable)
        {
            setProperty(CONFIG_SECTION + ".slow-consumer-detection." +
                        "policy.topicdelete.delete-persistent", "");
        }
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

        setConfig("messageCount", String.valueOf(MAX_QUEUE_MESSAGE_COUNT - 1), false);

        //Start the broker
        startBroker();

        topicConsumer(Session.AUTO_ACKNOWLEDGE, false);
    }

    /**
     * Test that setting depth has an effect on topics
     *
     * Sets the message size for the test
     * Sets the depth to be 9 * the depth
     * Ensure that sending 10 messages causes the disconnection
     *
     * @throws Exception
     */
    public void testTopicConsumerMessageSize() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        setConfig("depth", String.valueOf(MESSAGE_SIZE * 9), false);

        //Start the broker
        startBroker();

        setMessageSize(MESSAGE_SIZE);

        topicConsumer(Session.AUTO_ACKNOWLEDGE, false);
    }

    /**
     * Test that setting messageAge has an effect on topics
     *
     * Sets the messageAge to be half the disconnection wait timeout
     * Send 10 messages and then ensure that we get disconnected as we will
     * wait for the full timeout.
     *
     * @throws Exception
     */
    public void testTopicConsumerMessageAge() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        setConfig("messageAge", String.valueOf(DISCONNECTION_WAIT / 2), false);

        //Start the broker
        startBroker();

        topicConsumer(Session.AUTO_ACKNOWLEDGE, false);
    }

    /**
     * Test that setting messageCount takes affect on a durable Consumer
     *
     * Ensure we set the delete-persistent option
     *
     * We send 10 messages and disconnect at 9
     *
     * @throws Exception
     */

    public void testTopicDurableConsumerMessageCount() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        setConfig("messageCount", String.valueOf(MAX_QUEUE_MESSAGE_COUNT - 1), true);

        //Start the broker
        startBroker();

        topicConsumer(Session.AUTO_ACKNOWLEDGE, true);
    }

    /**
     * Test that setting depth has an effect on durable consumer topics
     *
     * Ensure we set the delete-persistent option
     *
     * Sets the message size for the test
     * Sets the depth to be 9 * the depth
     * Ensure that sending 10 messages causes the disconnection
     *
     * @throws Exception
     */
    public void testTopicDurableConsumerMessageSize() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        setConfig("depth", String.valueOf(MESSAGE_SIZE * 9), true);

        //Start the broker
        startBroker();

        setMessageSize(MESSAGE_SIZE);

        topicConsumer(Session.AUTO_ACKNOWLEDGE, true);
    }

    /**
     * Test that setting messageAge has an effect on topics
     *
     * Ensure we set the delete-persistent option
     *
     * Sets the messageAge to be 1/5 the disconnection wait timeout (or 1sec)
     * Send 10 messages and then ensure that we get disconnected as we will
     * wait for the full timeout.
     *
     * @throws Exception
     */
    public void testTopicDurableConsumerMessageAge() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        setConfig("messageAge", String.valueOf(DISCONNECTION_WAIT / 5), true);

        //Start the broker
        startBroker();

        topicConsumer(Session.AUTO_ACKNOWLEDGE, true);
    }

}
