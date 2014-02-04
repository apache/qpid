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

import org.apache.qpid.AMQException;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.StateChangeListener;

public interface QueueEntry extends MessageInstance, Comparable<QueueEntry>
{

    AMQQueue getQueue();

    long getSize();

    boolean getDeliveredToConsumer();

    boolean expired() throws AMQException;

    boolean acquire(Subscription sub);

    boolean acquiredBySubscription();
    boolean isAcquiredBy(Subscription subscription);

    void setRedelivered();

    boolean isRedelivered();

    Subscription getDeliveredSubscription();

    void reject();

    boolean isRejectedBy(Subscription subscription);

    int routeToAlternate(final Action<QueueEntry> action, ServerTransaction txn);

    boolean isQueueDeleted();

    QueueEntry getNextNode();

    QueueEntry getNextValidEntry();

    void addStateChangeListener(StateChangeListener<QueueEntry, State> listener);
    boolean removeStateChangeListener(StateChangeListener<QueueEntry, State> listener);


    /**
     * Number of times this queue entry has been delivered.
     *
     * @return delivery count
     */
    int getDeliveryCount();

    void incrementDeliveryCount();

    void decrementDeliveryCount();

    Filterable asFilterable();

}
