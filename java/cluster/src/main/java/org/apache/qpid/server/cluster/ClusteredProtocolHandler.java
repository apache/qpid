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

import org.apache.log4j.Logger;
import org.apache.mina.common.IoSession;
import org.apache.qpid.AMQException;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ConnectionOpenBody;
import org.apache.qpid.framing.ConnectionSecureOkBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.ConnectionTuneOkBody;
import org.apache.qpid.framing.ClusterMembershipBody;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQPFastProtocolHandler;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

import java.net.InetSocketAddress;

public class ClusteredProtocolHandler extends AMQPFastProtocolHandler implements InductionBuffer.MessageHandler
{
    private static final Logger _logger = Logger.getLogger(ClusteredProtocolHandler.class);
    private final InductionBuffer _peerBuffer = new InductionBuffer(this);
    private final InductionBuffer _clientBuffer = new InductionBuffer(this);
    private final GroupManager _groupMgr;
    private final ServerHandlerRegistry _handlers;

    public ClusteredProtocolHandler(InetSocketAddress address)
    {
        this(ApplicationRegistry.getInstance(), address);
    }

    public ClusteredProtocolHandler(IApplicationRegistry registry, InetSocketAddress address)
    {        
        super(registry);
        ClusterBuilder builder = new ClusterBuilder(address);
        _groupMgr = builder.getGroupManager();
        _handlers = builder.getHandlerRegistry();
    }

    public ClusteredProtocolHandler(ClusteredProtocolHandler handler)
    {
        super(handler);
        _groupMgr = handler._groupMgr;
        _handlers = handler._handlers;
    }

    protected void createSession(IoSession session, VirtualHostRegistry virtualHostRegistry, AMQProtocolSession protocolSession, AMQCodecFactory codec) throws AMQException
    {
        new ClusteredProtocolSession(session, virtualHostRegistry, codec, new ServerHandlerRegistry(_handlers, virtualHostRegistry, protocolSession));
    }

    void connect(String join) throws Exception
    {
        if (join == null)
        {
            _groupMgr.establish();
        }
        else
        {
            _groupMgr.join(new SimpleMemberHandle(join));
        }
    }

    private boolean inState(JoinState state)
    {
        return _groupMgr.getState().equals(state);
    }

    public void messageReceived(IoSession session, Object msg) throws Exception
    {
        JoinState state = _groupMgr.getState();
        switch (state)
        {
            case JOINED:
                _logger.debug(new LogMessage("Received {0}", msg));
                super.messageReceived(session, msg);
                break;
            case JOINING:
            case INITIATION:
            case INDUCTION:
                buffer(session, msg);
                break;
            default:
                throw new AMQException("Received message while in state: " + state);
        }
        JoinState latest = _groupMgr.getState();
        if (!latest.equals(state))
        {
            switch (latest)
            {
                case INDUCTION:
                    _logger.info("Reached induction, delivering buffered message from peers");
                    _peerBuffer.deliver();
                    break;
                case JOINED:
                    _logger.info("Reached joined, delivering buffered message from clients");
                    _clientBuffer.deliver();
                    break;
            }
        }
    }

    private void buffer(IoSession session, Object msg) throws Exception
    {
        if (isBufferable(msg))
        {
            MemberHandle peer = ClusteredProtocolSession.getSessionPeer(session);
            if (peer == null)
            {
                _logger.debug(new LogMessage("Buffering {0} for client", msg));
                _clientBuffer.receive(session, msg);
            }
            else if (inState(JoinState.JOINING) && isMembershipAnnouncement(msg))
            {
                _logger.debug(new LogMessage("Initial membership [{0}] received from {1}", msg, peer));
                super.messageReceived(session, msg);
            }
            else if (inState(JoinState.INITIATION) && _groupMgr.isLeader(peer))
            {
                _logger.debug(new LogMessage("Replaying {0} from leader ", msg));
                super.messageReceived(session, msg);
            }
            else if (inState(JoinState.INDUCTION))
            {
                _logger.debug(new LogMessage("Replaying {0} from peer {1}", msg, peer));
                super.messageReceived(session, msg);
            }
            else
            {
                _logger.debug(new LogMessage("Buffering {0} for peer {1}", msg, peer));
                _peerBuffer.receive(session, msg);
            }
        }
        else
        {
            _logger.debug(new LogMessage("Received {0}", msg));
            super.messageReceived(session, msg);
        }
    }

    public void deliver(IoSession session, Object msg) throws Exception
    {
        _logger.debug(new LogMessage("Delivering {0}", msg));
        super.messageReceived(session, msg);
    }

    private boolean isMembershipAnnouncement(Object msg)
    {
        return msg instanceof AMQFrame && (((AMQFrame) msg).getBodyFrame() instanceof ClusterMembershipBody);
    }

    private boolean isBufferable(Object msg)
    {
        return msg instanceof AMQFrame && isBuffereable(((AMQFrame) msg).getBodyFrame());
    }

    private boolean isBuffereable(AMQBody body)
    {
        return !(body instanceof ConnectionStartOkBody ||
                body instanceof ConnectionTuneOkBody ||
                body instanceof ConnectionSecureOkBody ||
                body instanceof ConnectionOpenBody);
    }
}
