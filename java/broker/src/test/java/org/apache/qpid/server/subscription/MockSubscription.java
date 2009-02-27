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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.QueueEntry.SubscriptionAcquiredState;
import org.apache.log4j.Logger;

public class MockSubscription implements Subscription
{
    private static final Logger _logger = Logger.getLogger(MockSubscription.class);

    private boolean _closed = false;
    private AMQShortString tag = new AMQShortString("mocktag");
    private AMQQueue queue = null;
    private StateListener _listener = null;
    private QueueEntry lastSeen = null;
    private State _state = State.ACTIVE;
    private ArrayList<QueueEntry> _queueEntries = new ArrayList<QueueEntry>();
    private final Lock _stateChangeLock = new ReentrantLock();
    private ArrayList<AMQMessage> _messages = new ArrayList<AMQMessage>();




    public void close()
    {
        _closed = true;
        if (_listener != null)
        {
            _listener.stateChange(this, _state, State.CLOSED);
        }
        _state = State.CLOSED;
    }

    public boolean filtersMessages()
    {
        return false;
    }

    public AMQChannel getChannel()
    {
        return null;
    }

    public AMQShortString getConsumerTag()
    {
        return tag ;
    }

    public QueueEntry getLastSeenEntry()
    {
        return lastSeen;
    }

    public SubscriptionAcquiredState getOwningState()
    {
        return new QueueEntry.SubscriptionAcquiredState(this);
    }

    public AMQQueue getQueue()
    {
        return queue;
    }

    public void getSendLock()
    {
        _stateChangeLock.lock();
    }

    public boolean hasInterest(QueueEntry msg)
    {
        return true;
    }

    public boolean isActive()
    {
        return true;
    }

    public boolean isAutoClose()
    {
        return false;
    }

    public boolean isBrowser()
    {
        return false;
    }

    public boolean isClosed()
    {
        return _closed;
    }

    public boolean isSuspended()
    {
        return false;
    }

    public void queueDeleted(AMQQueue queue)
    {
    }

    public void releaseSendLock()
    {
        _stateChangeLock.unlock();
    }

    public void resend(QueueEntry entry) throws AMQException
    {
    }

    public void restoreCredit(QueueEntry queueEntry)
    {
    }

    public void send(QueueEntry entry) throws AMQException
    {
        _logger.info("Sending Message(" + entry.debugIdentity() + ")  to subscription:" + this);

        lastSeen = entry;
        _queueEntries.add(entry);
        _messages.add(entry.getMessage());
        entry.setDeliveredToSubscription();        
    }

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

    public void setQueue(AMQQueue queue)
    {
        this.queue = queue;
    }

    public void setStateListener(StateListener listener)
    {
        this._listener = listener;
    }

    public State getState()
    {
        return _state;
    }

    public boolean wouldSuspend(QueueEntry msg)
    {
        return false;
    }

    public ArrayList<QueueEntry> getQueueEntries()
    {
        return _queueEntries;
    }

    public ArrayList<AMQMessage> getMessages()
    {
        return _messages;
    }

}
