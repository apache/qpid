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

import org.apache.qpid.amqp_1_0.jms.Queue;

import java.util.WeakHashMap;

public class QueueImpl extends DestinationImpl implements Queue
{
    private static final WeakHashMap<String, QueueImpl> QUEUE_CACHE =
        new WeakHashMap<String, QueueImpl>();

    public QueueImpl(String address)
    {
        super(address);
    }

    public String getQueueName()
    {
        return getAddress();
    }

    public static synchronized QueueImpl createQueue(final String address)
    {
        QueueImpl queue = QUEUE_CACHE.get(address);
        if(queue == null)
        {
            queue = new QueueImpl(address);
            QUEUE_CACHE.put(address, queue);
        }
        return queue;
    }

}
