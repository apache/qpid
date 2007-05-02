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
package org.apache.qpid.server.messageStore;

import org.apache.qpid.framing.AMQShortString;

/**
 * A storable queue can store storable messages and can be persisted in the store.
 * Created by Arnaud Simon
 * Date: 03-Apr-2007
 * Time: 08:52:18
 */
public interface StorableQueue
{
    /**
     * Get This queue unique id.
     *
     * @return The queue ID
     */
    public int getQueueID();

    /**
     * Set the queue ID.
     *
     * @param id This queue ID
     */
    public void setQueueID(int id);

    /**
     * Get this queue owner.
     *
     * @return This queue owner
     */
    public AMQShortString getOwner();

    /**
     * Get this queue name.
     *
     * @return the name of this queue
     */
    public AMQShortString getName();

    /**
     * Signifies to this queue that a message is dequeued.
     * This operation is called by the store.
     *
     * @param m The dequeued message
     */
    public void dequeue(StorableMessage m);

    /**
     * Signifies to this queue that a message is enqueued.
     * This operation is called by the store.
     *
     * @param m The enqueued message
     */
    public void enqueue(StorableMessage m);
        
}
