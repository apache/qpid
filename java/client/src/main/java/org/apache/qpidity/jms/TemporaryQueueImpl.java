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

import javax.jms.TemporaryQueue;
import javax.jms.JMSException;
import java.util.UUID;

/**
 * Implements TemporaryQueue
 */
public class TemporaryQueueImpl extends DestinationImpl implements TemporaryQueue, TemporaryDestination
{
    /**
     * Indicates whether this temporary queue is deleted.
     */
    private boolean _isDeleted = false;

    //--- constructor

    /**
     * Create a new TemporaryQueueImpl with a given name.
     *
     * @param session The session used to create this TemporaryQueueImpl.
     * @throws QpidException If creating the TemporaryQueueImpl fails due to some error.
     */
    protected TemporaryQueueImpl(SessionImpl session) throws QpidException
    {
        // temporary destinations do not have names
        super(session, "NAME_NOT_SET");
        _exchangeName = ExchangeDefaults.DIRECT_EXCHANGE_NAME;
        _exchangeClass = ExchangeDefaults.FANOUT_EXCHANGE_CLASS;
        _queueName = "TempQueue-" + UUID.randomUUID();
        // check that this queue exist on the server
        // As pasive is set the server will not create the queue.
        session.getQpidSession().queueDeclare(_queueName, null, null, Option.AUTO_DELETE);
        session.getQpidSession().queueBind(_queueName, _exchangeName, _queueName, null);
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

    //-- TemporaryTopic Interface
    /**
     * Delete this temporary destinaiton
     *
     * @throws JMSException If deleting this temporary queue fails due to some error.
     */
    public void delete() throws JMSException
    {
        // todo delete this temporary queue
        _isDeleted = true;
    }

    //---- Interface javax.jms.Queue
    /**
     * Gets the name of this queue.
     *
     * @return This queue's name.
     */
    public String getQueueName() throws JMSException
    {
        return super.getName();
    }
}
