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
package org.apache.qpid.server.cluster.handler;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.*;
import org.apache.qpid.server.cluster.ClusterCapability;
import org.apache.qpid.server.cluster.ClusteredProtocolSession;
import org.apache.qpid.server.cluster.GroupManager;
import org.apache.qpid.server.cluster.LoadTable;
import org.apache.qpid.server.cluster.MemberHandle;
import org.apache.qpid.server.cluster.MethodHandlerFactory;
import org.apache.qpid.server.cluster.MethodHandlerRegistry;
import org.apache.qpid.server.cluster.SimpleMemberHandle;
import org.apache.qpid.server.handler.ChannelCloseHandler;
import org.apache.qpid.server.handler.ChannelFlowHandler;
import org.apache.qpid.server.handler.ChannelOpenHandler;
import org.apache.qpid.server.handler.ConnectionCloseMethodHandler;
import org.apache.qpid.server.handler.ConnectionOpenMethodHandler;
import org.apache.qpid.server.handler.ConnectionSecureOkMethodHandler;
import org.apache.qpid.server.handler.ConnectionStartOkMethodHandler;
import org.apache.qpid.server.handler.ConnectionTuneOkMethodHandler;
import org.apache.qpid.server.handler.ExchangeDeclareHandler;
import org.apache.qpid.server.handler.ExchangeDeleteHandler;
import org.apache.qpid.server.handler.BasicCancelMethodHandler;
import org.apache.qpid.server.handler.BasicPublishMethodHandler;
import org.apache.qpid.server.handler.QueueBindHandler;
import org.apache.qpid.server.handler.QueueDeleteHandler;
import org.apache.qpid.server.handler.BasicQosHandler;
import org.apache.qpid.server.handler.TxSelectHandler;
import org.apache.qpid.server.handler.TxCommitHandler;
import org.apache.qpid.server.handler.TxRollbackHandler;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

public class ClusterMethodHandlerFactory implements MethodHandlerFactory
{
    private final GroupManager _groupMgr;
    private final LoadTable _loadTable;

    public ClusterMethodHandlerFactory(GroupManager groupMgr, LoadTable loadTable)
    {
        _groupMgr = groupMgr;
        _loadTable = loadTable;
    }

    public MethodHandlerRegistry register(AMQState state, MethodHandlerRegistry registry)
    {
        switch (state)
        {
            case CONNECTION_NOT_STARTED:
                return registry.addHandler(ConnectionStartOkBody.class, ConnectionStartOkMethodHandler.getInstance());
            case CONNECTION_NOT_AUTH:
                return registry.addHandler(ConnectionSecureOkBody.class, ConnectionSecureOkMethodHandler.getInstance());
            case CONNECTION_NOT_TUNED:
                return registry.addHandler(ConnectionTuneOkBody.class, ConnectionTuneOkMethodHandler.getInstance());
            case CONNECTION_NOT_OPENED:
                //connection.open override:
                return registry.addHandler(ConnectionOpenBody.class, new ConnectionOpenHandler());
            case CONNECTION_OPEN:
                return registerConnectionOpened(registry);
        }
        return registry;
    }

    private MethodHandlerRegistry registerConnectionOpened(MethodHandlerRegistry registry)
    {
        //new cluster method handlers:
        registry.addHandler(ClusterJoinBody.class, new JoinHandler());
        registry.addHandler(ClusterLeaveBody.class, new LeaveHandler());
        registry.addHandler(ClusterSuspectBody.class, new SuspectHandler());
        registry.addHandler(ClusterMembershipBody.class, new MembershipHandler());
        registry.addHandler(ClusterPingBody.class, new PingHandler());
        registry.addHandler(ClusterSynchBody.class, new SynchHandler());

        //connection.close override:
        registry.addHandler(ConnectionCloseBody.class, new ConnectionCloseHandler());

        //replicated handlers:
        registry.addHandler(ExchangeDeclareBody.class, replicated(ExchangeDeclareHandler.getInstance()));
        registry.addHandler(ExchangeDeleteBody.class, replicated(ExchangeDeleteHandler.getInstance()));

        ChannelQueueManager channelQueueMgr = new ChannelQueueManager();


        LocalQueueDeclareHandler handler = new LocalQueueDeclareHandler(_groupMgr);
        registry.addHandler(QueueDeclareBody.class,
                            chain(new QueueNameGenerator(handler),
                                  channelQueueMgr.createQueueDeclareHandler(),
                                  new ReplicatingHandler<QueueDeclareBody>(_groupMgr, handler)));

        registry.addHandler(QueueBindBody.class, chain(channelQueueMgr.createQueueBindHandler(), replicated(QueueBindHandler.getInstance())));
        registry.addHandler(QueueDeleteBody.class, chain(channelQueueMgr.createQueueDeleteHandler(), replicated(alternate(new QueueDeleteHandler(false), new QueueDeleteHandler(true)))));
        registry.addHandler(BasicConsumeBody.class, chain(channelQueueMgr.createBasicConsumeHandler(), new ReplicatingConsumeHandler(_groupMgr)));

        //other modified handlers:
        registry.addHandler(BasicCancelBody.class, alternate(new RemoteCancelHandler(), BasicCancelMethodHandler.getInstance()));

        //other unaffected handlers:
        registry.addHandler(BasicPublishBody.class, BasicPublishMethodHandler.getInstance());
        registry.addHandler(BasicQosBody.class, BasicQosHandler.getInstance());
        registry.addHandler(ChannelOpenBody.class, ChannelOpenHandler.getInstance());
        registry.addHandler(ChannelCloseBody.class, ChannelCloseHandler.getInstance());
        registry.addHandler(ChannelFlowBody.class, ChannelFlowHandler.getInstance());
        registry.addHandler(TxSelectBody.class, TxSelectHandler.getInstance());
        registry.addHandler(TxCommitBody.class, TxCommitHandler.getInstance());
        registry.addHandler(TxRollbackBody.class, TxRollbackHandler.getInstance());


        return registry;
    }

    private class SynchHandler implements StateAwareMethodListener<ClusterSynchBody>
    {
        public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ClusterSynchBody> evt) throws AMQException
        {
            _groupMgr.handleSynch(ClusteredProtocolSession.getSessionPeer(stateManager.getProtocolSession()));
        }
    }

    private class JoinHandler implements StateAwareMethodListener<ClusterJoinBody>
    {
        public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ClusterJoinBody> evt) throws AMQException
        {
            _groupMgr.handleJoin(new SimpleMemberHandle(evt.getMethod().broker));
        }
    }

    private class LeaveHandler implements StateAwareMethodListener<ClusterLeaveBody>
    {
        public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ClusterLeaveBody> evt) throws AMQException
        {
            _groupMgr.handleLeave(new SimpleMemberHandle(evt.getMethod().broker));
        }
    }

    private class SuspectHandler implements StateAwareMethodListener<ClusterSuspectBody>
    {
        public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ClusterSuspectBody> evt) throws AMQException
        {
            _groupMgr.handleSuspect(new SimpleMemberHandle(evt.getMethod().broker));
        }
    }

    private class MembershipHandler implements StateAwareMethodListener<ClusterMembershipBody>
    {
        public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ClusterMembershipBody> evt) throws AMQException
        {
            ClusterMembershipBody body = evt.getMethod();
            _groupMgr.handleMembershipAnnouncement(new String(body.members));
        }
    }

    private class PingHandler implements StateAwareMethodListener<ClusterPingBody>
    {
        public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ClusterPingBody> evt) throws AMQException
        {
            MemberHandle peer = new SimpleMemberHandle(evt.getMethod().broker);
            _groupMgr.handlePing(peer, evt.getMethod().load);
            if (evt.getMethod().responseRequired)
            {
                evt.getMethod().load = _loadTable.getLocalLoad();
                stateManager.getProtocolSession().writeFrame(new AMQFrame(evt.getChannelId(), evt.getMethod()));
            }
        }
    }

    private class ConnectionOpenHandler extends ExtendedHandler<ConnectionOpenBody>
    {
        ConnectionOpenHandler()
        {
            super(ConnectionOpenMethodHandler.getInstance());
        }

        void postHandle(AMQStateManager stateMgr, AMQMethodEvent<ConnectionOpenBody> evt)
        {
            AMQShortString capabilities = evt.getMethod().capabilities;
            if (ClusterCapability.contains(capabilities))
            {
                ClusteredProtocolSession.setSessionPeer(stateMgr.getProtocolSession(), ClusterCapability.getPeer(capabilities));
            }
            else
            {
                _loadTable.incrementLocalLoad();
            }
        }
    }

    private class ConnectionCloseHandler extends ExtendedHandler<ConnectionCloseBody>
    {
        ConnectionCloseHandler()
        {
            super(ConnectionCloseMethodHandler.getInstance());
        }

        void postHandle(AMQStateManager stateMgr, AMQMethodEvent<ConnectionCloseBody> evt)
        {
            if (!ClusteredProtocolSession.isPeerSession(stateMgr.getProtocolSession()))
            {
                _loadTable.decrementLocalLoad();
            }
        }
    }

    private <B extends AMQMethodBody> ReplicatingHandler<B> replicated(StateAwareMethodListener<B> handler)
    {
        return new ReplicatingHandler<B>(_groupMgr, handler);
    }

    private <B extends AMQMethodBody> StateAwareMethodListener<B> alternate(StateAwareMethodListener<B> peer, StateAwareMethodListener<B> client)
    {
        return new PeerHandler<B>(peer, client);
    }

    private <B extends AMQMethodBody> StateAwareMethodListener<B> chain(ClusterMethodHandler<B>... h)
    {
        return new ChainedClusterMethodHandler<B>(h);
    }
}
