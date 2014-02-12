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
package org.apache.qpid.server.queue;

public interface MessageGroupManager<E extends QueueEntryImpl<E,Q,L>, Q extends SimpleAMQQueue<E,Q,L>, L extends SimpleQueueEntryList<E,Q,L>>
{
    public interface ConsumerResetHelper<E extends QueueEntryImpl<E,Q,L>, Q extends SimpleAMQQueue<E,Q,L>, L extends SimpleQueueEntryList<E,Q,L>>
    {
        public void resetSubPointersForGroups(QueueConsumer<?,E,Q,L> consumer, boolean clearAssignments);

        boolean isEntryAheadOfConsumer(E entry, QueueConsumer<?,E,Q,L> sub);
    }

    QueueConsumer getAssignedConsumer(E entry);

    boolean acceptMessage(QueueConsumer<?,E,Q,L> sub, E entry);

    E findEarliestAssignedAvailableEntry(QueueConsumer<?,E,Q,L> sub);

    void clearAssignments(QueueConsumer sub);
}
