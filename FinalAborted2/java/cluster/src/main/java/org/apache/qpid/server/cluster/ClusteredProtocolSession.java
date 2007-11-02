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
package org.apache.qpid.server.cluster;

import org.apache.mina.common.IoSession;
import org.apache.qpid.AMQException;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMinaProtocolSession;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.state.AMQStateManager;

public class ClusteredProtocolSession extends AMQMinaProtocolSession
{
    private MemberHandle _peer;

    public ClusteredProtocolSession(IoSession session, VirtualHostRegistry virtualHostRegistry, AMQCodecFactory codecFactory, AMQStateManager stateManager) throws AMQException
//    public ClusteredProtocolSession(IoSession session, QueueRegistry queueRegistry,
//        ExchangeRegistry exchangeRegistry, AMQCodecFactory codecFactory) throws AMQException
    {
        super(session, virtualHostRegistry, codecFactory, stateManager);
//        super(session, queueRegistry, exchangeRegistry, codecFactory);
    }

    public boolean isPeerSession()
    {
        return _peer != null;
    }

    public void setSessionPeer(MemberHandle peer)
    {
        _peer = peer;
    }

    public MemberHandle getSessionPeer()
    {
        return _peer;
    }

    public AMQChannel getChannel(int channelId)
        throws AMQException
    {
        AMQChannel channel = super.getChannel(channelId);
        if (isPeerSession() && channel == null)
        {
            channel = new OneUseChannel(channelId, getVirtualHost());
            addChannel(channel);
        }
        return channel;
    }

    public static boolean isPeerSession(IoSession session)
    {
        return isPeerSession(getAMQProtocolSession(session));
    }

    public static boolean isPeerSession(AMQProtocolSession session)
    {
        return session instanceof ClusteredProtocolSession && ((ClusteredProtocolSession) session).isPeerSession();
    }

    public static void setSessionPeer(AMQProtocolSession session, MemberHandle peer)
    {
        ((ClusteredProtocolSession) session).setSessionPeer(peer);
    }

    public static MemberHandle getSessionPeer(AMQProtocolSession session)
    {
        return ((ClusteredProtocolSession) session).getSessionPeer();
    }

    public static MemberHandle getSessionPeer(IoSession session)
    {
        return getSessionPeer(getAMQProtocolSession(session));
    }

    /**
     * Cleans itself up after delivery of a message (publish frame, header and optional body frame(s))
     */
    private class OneUseChannel extends AMQChannel
    {
        public OneUseChannel(int channelId, VirtualHost virtualHost)
            throws AMQException
        {
            super(ClusteredProtocolSession.this,channelId,
                  virtualHost.getMessageStore(),
                  virtualHost.getExchangeRegistry());
        }

        protected void routeCurrentMessage() throws AMQException
        {
            super.routeCurrentMessage();
            removeChannel(getChannelId());
        }
    }

    public static boolean isPayloadFromPeer(AMQMessage payload)
    {
        return isPeerSession(payload.getPublisher());
    }

    public static boolean canRelay(AMQMessage payload, MemberHandle target)
    {
        //can only relay client messages that have not already been relayed to the given target
        return !isPayloadFromPeer(payload) && !payload.checkToken(target);
    }

}
