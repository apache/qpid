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

import javax.jms.JMSException;
import javax.jms.TemporaryQueue;

/**
 * AMQ implementation of a TemporaryQueue.
 */
final class AMQTemporaryQueue extends AMQQueue implements TemporaryQueue
{

    private final AMQSession _session;

    /**
     * Create a new instance of an AMQTemporaryQueue
     */
    public AMQTemporaryQueue(AMQSession session)
    {
        super("TempQueue" + Long.toString(System.currentTimeMillis()), true);
        _session = session;
    }

    /**
     * @see javax.jms.TemporaryQueue#delete()
     */
    public synchronized void delete() throws JMSException
    {
        if(_session.hasConsumer(this))
        {
            throw new JMSException("Temporary Queue has consumers so cannot be deleted");
        }

        // Currently TemporaryQueue is set to be auto-delete which means that the queue will be deleted
        // by the server when there are no more subscriptions to that queue.  This is probably not
        // quite right for JMSCompliance.
    }

}
