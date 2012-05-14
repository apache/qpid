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
package org.apache.qpid.jms;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Topic;

import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.address.AddressPolicy;
import org.apache.qpid.messaging.address.Link;
import org.apache.qpid.messaging.address.Node;
import org.apache.qpid.messaging.address.NodeType;
import org.apache.qpid.messaging.address.Reliability;

public class QpidTopic extends QpidDestination implements Topic 
{
    public QpidTopic()
    {
    }

    public QpidTopic(String str) throws JMSException
    {
        setDestinationString(str);
    }

    public QpidTopic(Address addr)
    {
        super(addr);
    }

    @Override
    public DestinationType getType()
    {
        return DestinationType.TOPIC;
    }

    @Override
    public String getTopicName()
    {
        return getAddress().getSubject() == null ? "" : getAddress().getSubject();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if(obj.getClass() != getClass())
        {
            return false;
        }

        QpidTopic topic = (QpidTopic)obj;

        if (!getAddress().getName().equals(topic.getAddress().getName()))
        {
            return false;
        }

        // The subject being the topic name
        if (!getAddress().getSubject().equals(topic.getAddress().getSubject()))
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        String name = getAddress() == null ? "" : getAddress().getName();
        String subject = getAddress() == null ? "" : getAddress().getSubject();
        int result = 17;
        result = 37*result + name.hashCode();
        result = 37*result + subject.hashCode();
        return result;
    }

    public QpidTopic createDurableTopic(String durableTopicQueueName)
    {
        String name = getAddress().getName();
        String subject = getAddress().getSubject();
        Link link = new Link(durableTopicQueueName,
                            true, // durable
                            Reliability.AT_LEAST_ONCE,
                            0, // producer capacity
                            0, // consumer capacity
                            Collections.EMPTY_MAP,
                            Collections.EMPTY_LIST,
                            Collections.EMPTY_MAP);

        Node node = new Node(name,
                            NodeType.TOPIC,
                            false,
                            AddressPolicy.NEVER,
                            AddressPolicy.NEVER,
                            AddressPolicy.NEVER,
                            Collections.EMPTY_MAP,
                            Collections.EMPTY_LIST);

        Address address = new Address(name, subject, node,link);
        return new QpidTopic(address);
    }
}
