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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;

public interface Subscription
{


    public static enum State
    {
        ACTIVE,
        SUSPENDED,
        CLOSED
    }

    public static interface StateListener
    {
        public void stateChange(Subscription sub, State oldState, State newState);
    }

    AMQQueue getQueue();

    QueueEntry.SubscriptionAcquiredState getOwningState();

    void setQueue(AMQQueue queue);

    AMQChannel getChannel();

    AMQShortString getConsumerTag();

    boolean isSuspended();

    boolean hasInterest(QueueEntry msg);

    boolean isAutoClose();

    boolean isClosed();

    boolean isBrowser();

    void close();

    boolean filtersMessages();

    void send(QueueEntry msg) throws AMQException;

    void queueDeleted(AMQQueue queue); 


    boolean wouldSuspend(QueueEntry msg);

    void getSendLock();
    void releaseSendLock();

    void resend(final QueueEntry entry) throws AMQException;

    void restoreCredit(final QueueEntry queueEntry);

    void setStateListener(final StateListener listener);

    public State getState();

    QueueEntry getLastSeenEntry();

    boolean setLastSeenEntry(QueueEntry expected, QueueEntry newValue);


    boolean isActive();



}
