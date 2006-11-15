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
package org.apache.qpid.client;

import org.apache.qpid.url.BindingURL;
import org.apache.qpid.exchange.ExchangeDefaults;

import javax.jms.JMSException;
import javax.jms.Topic;

public class AMQTopic extends AMQDestination implements Topic
    {
    /**
    * Constructor for use in creating a topic using a BindingURL.
     *
     * @param binding The binding url object.
    */
    public AMQTopic(BindingURL binding)
    {
        super(binding);
    }

    public AMQTopic(String name)
    {
        super(ExchangeDefaults.TOPIC_EXCHANGE_NAME, ExchangeDefaults.TOPIC_EXCHANGE_CLASS, name, true, true, null);
        _isDurable = false;
    }

    /**
     * Constructor for use in creating a topic to represent a durable subscription
     * @param topic
     * @param clientId
     * @param subscriptionName
     */
    public AMQTopic(AMQTopic topic, String clientId, String subscriptionName)
    {
        super(ExchangeDefaults.TOPIC_EXCHANGE_NAME, ExchangeDefaults.TOPIC_EXCHANGE_CLASS, topic.getDestinationName(), true, false, clientId + ":" + subscriptionName);
        _isDurable = true;
    }

    public String getTopicName() throws JMSException
    {
        return super.getDestinationName();
    }

    public String getEncodedName()
    {
        return 'T' + getDestinationName();
    }

     public String getRoutingKey()
    {
        return getDestinationName();
    }

    public boolean isNameRequired()
    {
        // Topics always rely on a server generated queue name.
        return false;
    }

    /**
     * Override since the queue is always private and we must ensure it remains null. If not,
     * reuse of the topic when registering consumers will make all consumers listen on the same (private) queue rather
     * than getting their own (private) queue.
     *
     * This is relatively nasty but it is difficult to come up with a more elegant solution, given
     * the requirement in the case on AMQQueue and possibly other AMQDestination subclasses to
     * use the underlying queue name even where it is server generated.
     */
    public void setQueueName(String queueName)
    {
    }
}
