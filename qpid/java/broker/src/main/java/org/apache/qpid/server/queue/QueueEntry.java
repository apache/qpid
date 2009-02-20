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
public interface QueueEntry extends Comparable<QueueEntry>, Filterable<AMQException>
{
    public static enum State
    {
        AVAILABLE,
        ACQUIRED,
        EXPIRED,
        DEQUEUED,
        DELETED
    }

    public static interface StateChangeListener
    {
        public void stateChanged(QueueEntry entry, State oldSate, State newState);
    }

    public abstract class EntryState
    {
        private EntryState()
        {
        }

        public abstract State getState();
    }

    public final class AvailableState extends EntryState
    {

        public State getState()
        {
            return State.AVAILABLE;
        }
    }

    public final class DequeuedState extends EntryState
    {

        public State getState()
        {
            return State.DEQUEUED;
        }
    }

    public final class DeletedState extends EntryState
    {

        public State getState()
        {
            return State.DELETED;
        }
    }

    public final class ExpiredState extends EntryState
    {

        public State getState()
        {
            return State.EXPIRED;
        }
    }

    public final class NonSubscriptionAcquiredState extends EntryState
    {
        public State getState()
        {
            return State.ACQUIRED;
        }
    }

    public final class SubscriptionAcquiredState extends EntryState
    {
        private final Subscription _subscription;

        public SubscriptionAcquiredState(Subscription subscription)
        {
            _subscription = subscription;
        }

        public State getState()
        {
            return State.ACQUIRED;
        }

        public Subscription getSubscription()
        {
            return _subscription;
        }
    }

    final static EntryState AVAILABLE_STATE = new AvailableState();
    final static EntryState DELETED_STATE = new DeletedState();
    final static EntryState DEQUEUED_STATE = new DequeuedState();
    final static EntryState EXPIRED_STATE = new ExpiredState();
    final static EntryState NON_SUBSCRIPTION_ACQUIRED_STATE = new NonSubscriptionAcquiredState();

    AMQQueue getQueue();

    AMQMessage getMessage();

    long getSize();

    boolean getDeliveredToConsumer();

    boolean expired() throws AMQException;

    boolean isAcquired();

    boolean acquire();

    boolean acquire(Subscription sub);

    boolean delete();

    boolean isDeleted();

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

    /**
     * Message has been ack so dequeueAndDelete it.
     * If the message is persistent and this is the last QueueEntry that uses it then the data will be removed
     * from the transaciton log
     *
     * @param storeContext the transactional Context in which to perform the deletion
     *
     * @throws FailedDequeueException
     * @throws MessageCleanupException
     */
    void dequeueAndDelete(StoreContext storeContext) throws FailedDequeueException;

    boolean isQueueDeleted();

    void addStateChangeListener(StateChangeListener listener);

    boolean removeStateChangeListener(StateChangeListener listener);
}
