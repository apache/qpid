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

import org.apache.qpid.AMQException;
import org.apache.qpid.server.txn.TransactionalContext;

public interface UnacknowledgedMessageMap
{
    public interface Visitor
    {
        /**
         * @param message the message being iterated over
         * @return true to stop iteration, false to continue
         * @throws AMQException
         */
        boolean callback(UnacknowledgedMessage message) throws AMQException;

        void visitComplete();
    }

    void visit(Visitor visitor) throws AMQException;

    Object getLock();

    void add(long deliveryTag, UnacknowledgedMessage message);

    void collect(Long deliveryTag, boolean multiple, List<UnacknowledgedMessage> msgs);

    boolean contains(long deliveryTag) throws AMQException;

    void remove(List<UnacknowledgedMessage> msgs);

    UnacknowledgedMessage remove(long deliveryTag);

    void drainTo(Collection<UnacknowledgedMessage> destination, long deliveryTag) throws AMQException;

    Collection<UnacknowledgedMessage> cancelAllMessages();

    void acknowledgeMessage(long deliveryTag, boolean multiple, TransactionalContext txnContext) throws AMQException;

    int size();

    void clear();

    UnacknowledgedMessage get(long deliveryTag);

    /**
     * Get the set of delivery tags that are outstanding.
     *
     * @return a set of delivery tags
     */
    Set<Long> getDeliveryTags();

    public long getUnacknowledgeBytes();
}


