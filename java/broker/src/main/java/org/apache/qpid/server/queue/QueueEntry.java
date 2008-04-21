package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.subscription.Subscription;

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
public interface QueueEntry extends Comparable<QueueEntry>
{
    AMQQueue getQueue();

    AMQMessage getMessage();

    long getSize();

    boolean getDeliveredToConsumer();

    boolean expired() throws AMQException;

    boolean isAcquired();

    boolean acquire(Subscription sub);

    boolean acquiredBySubscription();

    void setDeliveredToSubscription();

    void release();

    String debugIdentity();

    boolean immediateAndNotDelivered();

    void setRedelivered(boolean b);

    Subscription getDeliveredSubscription();

    void reject();

    void reject(Subscription subscription);

    boolean isRejectedBy(Subscription subscription);

    void requeue(StoreContext storeContext) throws AMQException;

    void dequeue(final StoreContext storeContext) throws FailedDequeueException;

    void dispose(final StoreContext storeContext) throws MessageCleanupException;

    void restoreCredit();

    void discard(StoreContext storeContext) throws AMQException;

    boolean isQueueDeleted();
}
