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
package org.apache.qpid.server.message;


import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.StateChangeListener;

public interface MessageInstance
{


    /**
     * Number of times this queue entry has been delivered.
     *
     * @return delivery count
     */
    int getDeliveryCount();

    void incrementDeliveryCount();

    void decrementDeliveryCount();

    void addStateChangeListener(StateChangeListener<? super MessageInstance,State> listener);

    boolean removeStateChangeListener(StateChangeListener<? super MessageInstance, State> listener);

    boolean acquiredByConsumer();

    boolean isAcquiredBy(ConsumerImpl consumer);

    boolean removeAcquisitionFromConsumer(ConsumerImpl consumer);

    void setRedelivered();

    boolean isRedelivered();

    ConsumerImpl getDeliveredConsumer();

    void reject();

    boolean isRejectedBy(ConsumerImpl consumer);

    boolean getDeliveredToConsumer();

    boolean expired();

    boolean acquire(ConsumerImpl sub);

    boolean lockAcquisition();

    boolean unlockAcquisition();

    int getMaximumDeliveryCount();

    int routeToAlternate(Action<? super MessageInstance> action, ServerTransaction txn);

    Filterable asFilterable();

    public static enum State
    {
        AVAILABLE,
        ACQUIRED,
        DEQUEUED,
        DELETED
    }

    public abstract class EntryState
    {
        private EntryState()
        {
        }

        public abstract State getState();

        /**
         * Returns true if state is either DEQUEUED or DELETED.
         *
         * @return true if state is either DEQUEUED or DELETED.
         */
        public boolean isDispensed()
        {
            State currentState = getState();
            return currentState == State.DEQUEUED || currentState == State.DELETED;
        }

    }


    public final class AvailableState extends EntryState
    {

        public State getState()
        {
            return State.AVAILABLE;
        }

        public String toString()
        {
            return getState().name();
        }
    }


    public final class DequeuedState extends EntryState
    {

        public State getState()
        {
            return State.DEQUEUED;
        }

        public String toString()
        {
            return getState().name();
        }
    }


    public final class DeletedState extends EntryState
    {

        public State getState()
        {
            return State.DELETED;
        }

        public String toString()
        {
            return getState().name();
        }
    }

    public final class NonConsumerAcquiredState extends EntryState
    {
        public State getState()
        {
            return State.ACQUIRED;
        }

        public String toString()
        {
            return getState().name();
        }
    }

    public final class ConsumerAcquiredState<C extends ConsumerImpl> extends EntryState
    {
        private final C _consumer;
        private final LockedAcquiredState<C> _lockedState;

        public ConsumerAcquiredState(C consumer)
        {
            _consumer = consumer;
            _lockedState = new LockedAcquiredState<>(this);
        }


        public State getState()
        {
            return State.ACQUIRED;
        }

        public C getConsumer()
        {
            return _consumer;
        }

        public String toString()
        {
            return "{" + getState().name() + " : " + _consumer +"}";
        }

        public LockedAcquiredState<C> getLockedState()
        {
            return _lockedState;
        }

    }

    public final class LockedAcquiredState<C extends ConsumerImpl> extends EntryState
    {
        private final ConsumerAcquiredState<C> _acquiredState;

        public LockedAcquiredState(final ConsumerAcquiredState<C> acquiredState)
        {
            _acquiredState = acquiredState;
        }

        @Override
        public State getState()
        {
            return State.ACQUIRED;
        }

        public C getConsumer()
        {
            return _acquiredState.getConsumer();
        }

        public String toString()
        {
            return "{" + getState().name() + " : " + _acquiredState.getConsumer() +"}";
        }

        public ConsumerAcquiredState<C> getUnlockedState()
        {
            return _acquiredState;
        }
    }


    final static EntryState AVAILABLE_STATE = new AvailableState();
    final static EntryState DELETED_STATE = new DeletedState();
    final static EntryState DEQUEUED_STATE = new DequeuedState();
    final static EntryState NON_CONSUMER_ACQUIRED_STATE = new NonConsumerAcquiredState();

    boolean isAvailable();

    boolean acquire();

    boolean isAcquired();

    void release();

    boolean resend();

    void delete();

    boolean isDeleted();

    ServerMessage getMessage();

    InstanceProperties getInstanceProperties();

    TransactionLogResource getOwningResource();
}
