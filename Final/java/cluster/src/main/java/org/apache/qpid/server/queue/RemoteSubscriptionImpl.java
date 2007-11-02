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

import org.apache.qpid.server.cluster.MemberHandle;
import org.apache.qpid.server.cluster.GroupManager;
import org.apache.qpid.server.cluster.SimpleSendable;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.AMQException;

import java.util.Queue;
import java.util.List;

class RemoteSubscriptionImpl implements Subscription, WeightedSubscriptionManager
{
    private final GroupManager _groupMgr;
    private final MemberHandle _peer;
    private boolean _suspended;
    private int _count;

    RemoteSubscriptionImpl(GroupManager groupMgr, MemberHandle peer)
    {
        _groupMgr = groupMgr;
        _peer = peer;
    }

    synchronized void increment()
    {
        _count++;
    }

    synchronized boolean decrement()
    {
        return --_count <= 0;
    }

    public void send(AMQMessage msg, AMQQueue queue)
    {
        try
        {
            _groupMgr.send(_peer, new SimpleSendable(msg));
        }
        catch (AMQException e)
        {
            //TODO: handle exceptions properly...
            e.printStackTrace();
        }
    }

    public synchronized void setSuspended(boolean suspended)
    {
        _suspended = suspended;
    }

    public synchronized boolean isSuspended()
    {
        return _suspended;
    }

    public synchronized int getWeight()
    {
        return _count;
    }

    public List<Subscription> getSubscriptions()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean hasActiveSubscribers()
    {
        return getWeight() == 0;
    }

    public Subscription nextSubscriber(AMQMessage msg)
    {
        return this;
    }

    public void queueDeleted(AMQQueue queue)
    {
        if (queue instanceof ClusteredQueue)
        {
            ((ClusteredQueue) queue).removeAllRemoteSubscriber(_peer);
        }
    }

    public boolean filtersMessages()
    {
        return false;
    }

    public boolean hasInterest(AMQMessage msg)
    {
        return true;
    }

    public Queue<AMQMessage> getPreDeliveryQueue()
    {
        return null;
    }

    public Queue<AMQMessage> getResendQueue()
    {
        return null;
    }

    public Queue<AMQMessage> getNextQueue(Queue<AMQMessage> messages)
    {
        return messages;
    }

    public void enqueueForPreDelivery(AMQMessage msg, boolean deliverFirst)
    {
        //no-op -- if selectors are implemented on RemoteSubscriptions then look at SubscriptionImpl
    }

    public boolean isAutoClose()
    {
        return false;
    }

    public void close()
    {
        //no-op
    }

    public boolean isClosed()
    {
        return false;
    }

    public boolean isBrowser()
    {
        return false;
    }

    public boolean wouldSuspend(AMQMessage msg)
    {
        return _suspended;
    }

    public void addToResendQueue(AMQMessage msg)
    {
        //no-op
    }

    public Object getSendLock()
    {
        return new Object();
    }

    public AMQChannel getChannel()
    {
        return null;
    }

}
