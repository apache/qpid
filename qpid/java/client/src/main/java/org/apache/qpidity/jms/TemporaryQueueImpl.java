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

import javax.jms.TemporaryQueue;
import javax.jms.JMSException;
import java.util.UUID;

/**
 * Implements TemporaryQueue
 */
public class TemporaryQueueImpl extends QueueImpl implements TemporaryQueue, TemporaryDestination
{
    /**
     * Indicates whether this temporary queue is deleted.
     */
    private boolean _isDeleted;

    /**
     * The session used to create this destination
     */
    private SessionImpl _session;

    //--- constructor
    /**
     * Create a new TemporaryQueueImpl.
     *
     * @param session The session used to create this TemporaryQueueImpl.
     * @throws QpidException If creating the TemporaryQueueImpl fails due to some error.
     */
    protected TemporaryQueueImpl(SessionImpl session) throws QpidException
    {
        super("TempQueue-" + UUID.randomUUID());
        // temporary destinations do not have names
        _isAutoDelete = false;
        _isDurable = false;
        _isExclusive = false;
        _isDeleted = false;
        _session = session;
        // we must create this queue
        registerQueue(session, true);
    }

    //-- TemporaryDestination Interface
    /**
     * Specify whether this temporary destination is deleted.
     *
     * @return true is this temporary destination is deleted.
     */
    public boolean isdeleted()
    {
        return _isDeleted;
    }

    //-- TemporaryQueue Interface
    /**
     * Delete this temporary destinaiton
     *
     * @throws JMSException If deleting this temporary queue fails due to some error.
     */
    public void delete() throws JMSException
    {
        if (!_isDeleted)
        {
            _session.getQpidSession().queueDelete(_queueName);
        }
        _isDeleted = true;
    }

}

