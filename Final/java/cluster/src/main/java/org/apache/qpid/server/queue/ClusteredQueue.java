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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicCancelBody;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.cluster.*;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a shared queue in a cluster. The key difference is that as well as any
 * local consumers, there may be consumers for this queue on other members of the
 * cluster.
 *
 */
public class ClusteredQueue extends AMQQueue
{
    private static final Logger _logger = Logger.getLogger(ClusteredQueue.class);
    private final ConcurrentHashMap<SimpleMemberHandle, RemoteSubscriptionImpl> _peers = new ConcurrentHashMap<SimpleMemberHandle, RemoteSubscriptionImpl>();
    private final GroupManager _groupMgr;
    private final NestedSubscriptionManager _subscriptions;

    public ClusteredQueue(GroupManager groupMgr, AMQShortString name, boolean durable, AMQShortString owner, boolean autoDelete, VirtualHost virtualHost)
            throws AMQException
    {
        super(name, durable, owner, autoDelete, virtualHost, new ClusteredSubscriptionManager());
        _groupMgr = groupMgr;
        _subscriptions = ((ClusteredSubscriptionManager) getSubscribers()).getAllSubscribers();
    }


    public void process(StoreContext storeContext, AMQMessage msg, boolean deliverFirst) throws AMQException
    {
        _logger.info(new LogMessage("{0} delivered to clustered queue {1}", msg, this));
        super.process(storeContext, msg, deliverFirst);
    }

    protected void autodelete() throws AMQException
    {
        if(!_subscriptions.hasActiveSubscribers())
        {
            //delete locally:
            delete();

            //send deletion request to all other members:
        	// AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        	// TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            QueueDeleteBody request = new QueueDeleteBody((byte)8,
                                                          (byte)0,
                                                          QueueDeleteBody.getClazz((byte)8,(byte)0),
                                                          QueueDeleteBody.getMethod((byte)8,(byte)0),
                                                          false,
                                                          false,
                                                          false,
                                                          getName(),
                                                          0);

            _groupMgr.broadcast(new SimpleBodySendable(request));
        }
    }

    public void unregisterProtocolSession(AMQProtocolSession ps, int channel, AMQShortString consumerTag) throws AMQException
    {
        //handle locally:
        super.unregisterProtocolSession(ps, channel, consumerTag);

        //signal other members:
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        BasicCancelBody request = new BasicCancelBody((byte)8,
                                                      (byte)0,
                                                      BasicCancelBody.getClazz((byte)8, (byte)0),
                                                      BasicCancelBody.getMethod((byte)8, (byte)0),
                                                      getName(),
                                                      false);
        
        _groupMgr.broadcast(new SimpleBodySendable(request));
    }

    public void addRemoteSubcriber(MemberHandle peer)
    {
        _logger.info(new LogMessage("Added remote subscriber for {0} to clustered queue {1}", peer, this));
        //find (or create) a matching subscriber for the peer then increment the count
        getSubscriber(key(peer), true).increment();
    }

    public void removeRemoteSubscriber(MemberHandle peer)
    {
        //find a matching subscriber for the peer then decrement the count
        //if count is now zero, remove the subscriber
        SimpleMemberHandle key = key(peer);
        RemoteSubscriptionImpl s = getSubscriber(key, true);
        if (s == null)
        {
            throw new RuntimeException("No subscriber for " + peer);
        }
        if (s.decrement())
        {
            _peers.remove(key);
            _subscriptions.removeSubscription(s);
        }
    }

    public void removeAllRemoteSubscriber(MemberHandle peer)
    {
        SimpleMemberHandle key = key(peer);
        RemoteSubscriptionImpl s = getSubscriber(key, true);
        _peers.remove(key);
        _subscriptions.removeSubscription(s);
    }

    private RemoteSubscriptionImpl getSubscriber(SimpleMemberHandle key, boolean create)
    {
        RemoteSubscriptionImpl s = _peers.get(key);
        if (s == null && create)
        {
            return addSubscriber(key, new RemoteSubscriptionImpl(_groupMgr, key));
        }
        else
        {
            return s;
        }
    }

    private RemoteSubscriptionImpl addSubscriber(SimpleMemberHandle key, RemoteSubscriptionImpl s)
    {
        RemoteSubscriptionImpl other = _peers.putIfAbsent(key, s);
        if (other == null)
        {
            _subscriptions.addSubscription(s);
            new SubscriberCleanup(key, this, _groupMgr);
            return s;
        }
        else
        {
            return other;
        }
    }

    private SimpleMemberHandle key(MemberHandle peer)
    {
        return peer instanceof SimpleMemberHandle ? (SimpleMemberHandle) peer : (SimpleMemberHandle) SimpleMemberHandle.resolve(peer);
    }

    static boolean isFromBroker(AMQMessage msg)
    {
        return ClusteredProtocolSession.isPayloadFromPeer(msg);
    }
}
