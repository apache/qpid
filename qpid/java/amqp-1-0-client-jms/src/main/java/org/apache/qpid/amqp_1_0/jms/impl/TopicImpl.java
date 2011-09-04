/*
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
 */
package org.apache.qpid.amqp_1_0.jms.impl;

import org.apache.qpid.amqp_1_0.jms.Topic;

import java.util.WeakHashMap;

public class TopicImpl extends DestinationImpl implements Topic
{
    private static final WeakHashMap<String, TopicImpl> TOPIC_CACHE =
        new WeakHashMap<String, TopicImpl>();


    public TopicImpl(String address)
    {
        super(address);
    }

    public String getTopicName()
    {
        return getAddress();
    }

    public static synchronized TopicImpl createTopic(final String address)
    {
        TopicImpl topic = TOPIC_CACHE.get(address);
        if(topic == null)
        {
            topic = new TopicImpl(address);
            TOPIC_CACHE.put(address, topic);
        }
        return topic;
    }

    public static TopicImpl valueOf(String address)
    {
        return address == null ? null : createTopic(address);
    }
}
