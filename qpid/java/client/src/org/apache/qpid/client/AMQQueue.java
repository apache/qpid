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

import org.apache.qpid.url.BindingURL;
import org.apache.qpid.exchange.ExchangeDefaults;

import javax.jms.Queue;

public class AMQQueue extends AMQDestination implements Queue
{

    /**
     * Create a reference to a non temporary queue using a BindingURL object.
     * Note this does not actually imply the queue exists.
     * @param binding a BindingURL object
     */
    public AMQQueue(BindingURL binding)
    {
         super(binding);
    }

    /**
     * Create a reference to a non temporary queue. Note this does not actually imply the queue exists.
     * @param name the name of the queue
     */
    public AMQQueue(String name)
    {
        this(name, false);
    }

    /**
     * Create a queue with a specified name.
     *
     * @param name the destination name (used in the routing key)
     * @param temporary if true the broker will generate a queue name, also if true then the queue is autodeleted
     * and exclusive
     */
    public AMQQueue(String name, boolean temporary)
    {
        // queue name is set to null indicating that the broker assigns a name in the case of temporary queues
        // temporary queues are typically used as response queues
        this(name, temporary?null:name, temporary, temporary);
        _isDurable = !temporary;
    }

    /**
     * Create a reference to a queue. Note this does not actually imply the queue exists.
     * @param destinationName the queue name
     * @param queueName the queue name
     * @param exclusive true if the queue should only permit a single consumer
     * @param autoDelete true if the queue should be deleted automatically when the last consumers detaches
     */
    public AMQQueue(String destinationName, String queueName, boolean exclusive, boolean autoDelete)
    {
        super(ExchangeDefaults.DIRECT_EXCHANGE_NAME, ExchangeDefaults.DIRECT_EXCHANGE_CLASS, destinationName, exclusive,
              autoDelete, queueName);
    }

    public String getEncodedName()
    {
        return 'Q' + getQueueName();
    }

    public String getRoutingKey()
    {
        return getQueueName();
    }

    public boolean isNameRequired()
    {
        //If the name is null, we require one to be generated by the client so that it will#
        //remain valid if we failover (see BLZ-24)
        return getQueueName() == null;
    }
}
