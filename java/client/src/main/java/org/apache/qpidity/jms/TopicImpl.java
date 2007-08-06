/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpidity.jms;

import org.apache.qpidity.QpidException;
import org.apache.qpidity.exchange.ExchangeDefaults;
import org.apache.qpidity.url.BindingURL;

import javax.jms.Topic;

/**
 * Implementation of the javax.jms.Topic interface.
 */
public class TopicImpl extends DestinationImpl implements Topic
{
    //--- Constructor
    /**
     * Create a new TopicImpl with a given name.
     *
     * @param name    The name of this topic
     * @param session The session used to create this queue.
     * @throws QpidException If the topic name is not valid
     */
    public TopicImpl(SessionImpl session, String name) throws QpidException
    {
        super(session, name);
        _exchangeName = ExchangeDefaults.TOPIC_EXCHANGE_NAME;
        _exchangeClass = ExchangeDefaults.TOPIC_EXCHANGE_CLASS;
    }

    /**
     * Create a TopicImpl from a binding URL
     *
     * @param session The session used to create this Topic.
     * @param binding The URL
     * @throws QpidException If the URL is not valid
     */
    protected TopicImpl(SessionImpl session, BindingURL binding) throws QpidException
    {
        super(session, binding);
    }

    //--- javax.jsm.Topic Interface
    /**
     * Gets the name of this topic.
     *
     * @return This topic's name.
     */
    public String getTopicName()
    {
        return super.getName();
    }

}
