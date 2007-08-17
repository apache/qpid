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

import javax.jms.TemporaryTopic;
import javax.jms.JMSException;
import java.util.UUID;


/**
 * Implements TemporaryTopic
 */
public class TemporaryTopicImpl extends TopicImpl implements TemporaryTopic, TemporaryDestination
{
    /**
     * Indicates whether this temporary topic is deleted.
     */
    private boolean _isDeleted = false;

    /**
     * The session used to create this destination
     */
    private SessionImpl _session;

    //--- constructor
    /**
     * Create a new TemporaryTopicImpl with a given name.
     *
     * @param session The session used to create this TemporaryTopicImpl.
     * @throws QpidException If creating the TemporaryTopicImpl fails due to some error.
     */
    protected TemporaryTopicImpl(SessionImpl session) throws QpidException
    {
        // temporary destinations do not have names.
        super(session, "TemporayTopic-" + UUID.randomUUID());
        _session = session;
    }

    //-- TemporaryDestination Interface
    public boolean isdeleted()
    {
        return _isDeleted;
    }

    //-- TemporaryTopic Interface
    public void delete() throws JMSException
    {
        if (!_isDeleted)
        {
            _session.getQpidSession().queueDelete(_queueName);
        }
        _isDeleted = true;
    }
}
