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
package org.apache.qpid.server.subscription;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.util.StateChangeListener;

public interface Subscription
{
    AtomicLong SUB_ID_GENERATOR = new AtomicLong(0);

    LogActor getLogActor();

    boolean isTransient();

    long getBytesOut();

    long getMessagesOut();

    long getUnacknowledgedBytes();

    long getUnacknowledgedMessages();

    public static enum State
    {
        ACTIVE,
        SUSPENDED,
        CLOSED
    }

    AMQQueue getQueue();
    AMQSessionModel getSessionModel();

    QueueEntry.SubscriptionAcquiredState getOwningState();

    void setQueue(AMQQueue queue, boolean exclusive);

    void setNoLocal(boolean noLocal);

    long getSubscriptionID();

    boolean isSuspended();

    boolean hasInterest(QueueEntry msg);

    boolean isClosed();

    boolean acquires();

    boolean seesRequeues();

    void close();

    void send(QueueEntry entry, boolean batch) throws AMQException;

    void flushBatched();

    void queueDeleted();


    boolean wouldSuspend(QueueEntry msg);

    boolean trySendLock();


    void getSendLock();

    void releaseSendLock();

    void restoreCredit(final QueueEntry queueEntry);

    void setStateListener(final StateChangeListener<Subscription, State> listener);

    public State getState();

    AMQQueue.Context getQueueContext();

    void setQueueContext(AMQQueue.Context queueContext);


    boolean isActive();

    public void set(String key, Object value);

    public Object get(String key);

    void queueEmpty() throws AMQException;

    String getName();

    void flush() throws AMQException;
}
