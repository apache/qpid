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
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.util.StateChangeListener;

public interface Subscription
{
    AtomicLong SUB_ID_GENERATOR = new AtomicLong(0);

    void externalStateChange();

    enum Option
    {
        ACQUIRES,
        SEES_REQUEUES,
        TRANSIENT,
        EXCLUSIVE,
        NO_LOCAL
    }

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

    AMQSessionModel getSessionModel();

    MessageInstance.SubscriptionAcquiredState getOwningState();

    void setNoLocal(boolean noLocal);

    long getSubscriptionID();

    boolean isSuspended();

    boolean hasInterest(MessageInstance msg);

    boolean isClosed();

    boolean acquires();

    boolean seesRequeues();

    void close() throws AMQException;

    void send(MessageInstance entry, boolean batch) throws AMQException;

    boolean resend(MessageInstance entry) throws AMQException;

    void flushBatched();

    void queueDeleted();


    boolean wouldSuspend(MessageInstance msg);

    boolean trySendLock();


    void getSendLock();

    void releaseSendLock();

    void restoreCredit(final MessageInstance queueEntry);

    void setStateListener(final StateChangeListener<? extends Subscription, State> listener);

    public State getState();

    boolean isActive();

    void queueEmpty() throws AMQException;

    String getName();

    void flush() throws AMQException;
}
