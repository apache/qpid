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
package org.apache.qpid.nclient.jms;

import javax.jms.Topic;
import javax.jms.JMSException;

/**
 * Implementation of the javax.jms.Topic interface.
 */
public class TopicImpl extends DestinationImpl implements Topic
{
    //--- Constructor
    /**
     * Create a new TopicImpl with a given name.
     *
     * @param name The name of this topic
     * @param session The session used to create this queue.
     * @throws JMSException If the topic name is not valid
     */
    public TopicImpl(SessionImpl session, String name) throws JMSException
    {
        super(session, name);
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
