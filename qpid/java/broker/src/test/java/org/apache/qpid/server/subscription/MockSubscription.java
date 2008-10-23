package org.apache.qpid.server.subscription;

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

import java.util.ArrayList;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.QueueEntry.SubscriptionAcquiredState;

public class MockSubscription implements Subscription
{

    private boolean _closed = false;
    private AMQShortString tag = new AMQShortString("mocktag");
    private AMQQueue queue = null;
    private StateListener _listener = null;
    private QueueEntry lastSeen = null;
    private State _state = State.ACTIVE;
    private ArrayList<QueueEntry> messages = new ArrayList<QueueEntry>();

    @Override
    public void close()
    {
        _closed = true;
        if (_listener != null)
        {
            _listener.stateChange(this, _state, State.CLOSED);
        }
        _state = State.CLOSED;
    }

    @Override
    public boolean filtersMessages()
    {
        return false;
    }

    @Override
    public AMQChannel getChannel()
    {
        return null;
    }

    @Override
    public AMQShortString getConsumerTag()
    {
        return tag ;
    }

    @Override
    public QueueEntry getLastSeenEntry()
    {
        return lastSeen;
    }

    @Override
    public SubscriptionAcquiredState getOwningState()
    {
        return new QueueEntry.SubscriptionAcquiredState(this);
    }

    @Override
    public AMQQueue getQueue()
    {
        return queue;
    }

    @Override
    public void getSendLock()
    {
    }

    @Override
    public boolean hasInterest(QueueEntry msg)
    {
        return true;
    }

    @Override
    public boolean isActive()
    {
        return true;
    }

    @Override
    public boolean isAutoClose()
    {
        return false;
    }

    @Override
    public boolean isBrowser()
    {
        return false;
    }

    @Override
    public boolean isClosed()
    {
        return _closed;
    }

    @Override
    public boolean isSuspended()
    {
        return false;
    }

    @Override
    public void queueDeleted(AMQQueue queue)
    {
    }

    @Override
    public void releaseSendLock()
    {
    }

    @Override
    public void resend(QueueEntry entry) throws AMQException
    {
    }

    @Override
    public void restoreCredit(QueueEntry queueEntry)
    {
    }

    @Override
    public void send(QueueEntry msg) throws AMQException
    {
        lastSeen = msg;
        messages.add(msg);
    }

    @Override
    public boolean setLastSeenEntry(QueueEntry expected, QueueEntry newValue)
    {
        boolean result = false;
        if (expected != null)
        {
            result = (expected.equals(lastSeen));
        }
        lastSeen = newValue;
        return result;
    }

    @Override
    public void setQueue(AMQQueue queue)
    {
        this.queue = queue;
    }

    @Override
    public void setStateListener(StateListener listener)
    {
        this._listener = listener;
    }

    public State getState()
    {
        return _state;
    }

    @Override
    public boolean wouldSuspend(QueueEntry msg)
    {
        return false;
    }

    public ArrayList<QueueEntry> getMessages()
    {
        return messages;
    }
}
