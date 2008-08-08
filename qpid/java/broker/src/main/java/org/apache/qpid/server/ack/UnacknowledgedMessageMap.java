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
package org.apache.qpid.server.ack;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.StoreContext;

public interface UnacknowledgedMessageMap
{
    public interface Visitor
    {
        /**
         * @param deliveryTag
         *@param message the message being iterated over @return true to stop iteration, false to continue
         * @throws AMQException
         */
        boolean callback(final long deliveryTag, QueueEntry message) throws AMQException;

        void visitComplete();
    }

    void visit(Visitor visitor) throws AMQException;

    void add(long deliveryTag, QueueEntry message);

    void collect(long deliveryTag, boolean multiple, Map<Long, QueueEntry> msgs);

    boolean contains(long deliveryTag) throws AMQException;

    void remove(Map<Long,QueueEntry> msgs);

    QueueEntry remove(long deliveryTag);

    public void drainTo(long deliveryTag, StoreContext storeContext) throws AMQException;
    
    Collection<QueueEntry> cancelAllMessages();

    void acknowledgeMessage(long deliveryTag, boolean multiple, TransactionalContext txnContext) throws AMQException;

    int size();

    void clear();

    QueueEntry get(long deliveryTag);

    /**
     * Get the set of delivery tags that are outstanding.
     *
     * @return a set of delivery tags
     */
    Set<Long> getDeliveryTags();

    public long getUnacknowledgeBytes();
}


