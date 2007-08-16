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
import org.apache.qpidity.Option;
import org.apache.qpidity.exchange.ExchangeDefaults;
import org.apache.qpidity.url.BindingURL;

import javax.jms.Topic;
import java.util.UUID;

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
        super(session);
        _queueName = "Topic-" + UUID.randomUUID();
        _destinationName = name;
        _exchangeName = ExchangeDefaults.TOPIC_EXCHANGE_NAME;
        _exchangeType = ExchangeDefaults.TOPIC_EXCHANGE_CLASS;
        _isAutoDelete = true;
        _isDurable = false;
        _isExclusive = true;
        checkTopicExists();
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
        checkTopicExists();
    }

    //--- javax.jsm.Topic Interface
    /**
     * Gets the name of this topic.
     *
     * @return This topic's name.
     */
    public String getTopicName()
    {
        return _destinationName;
    }
    /**
      * Check that this topic exchange
      *
      * @throws QpidException If this queue does not exists on the broker.
      */
     protected void checkTopicExists() throws QpidException
     {
         // test if this exchange exist on the broker
         _session.getQpidSession().exchangeDeclare(_exchangeName, _exchangeType, null, null, Option.PASSIVE);
         // wait for the broker response
         _session.getQpidSession().sync();
         // todo get the exception
     }

}
