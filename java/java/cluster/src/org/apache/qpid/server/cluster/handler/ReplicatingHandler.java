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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.server.cluster.BroadcastPolicy;
import org.apache.qpid.server.cluster.ClusteredProtocolSession;
import org.apache.qpid.server.cluster.GroupManager;
import org.apache.qpid.server.cluster.GroupResponseHandler;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.server.cluster.Member;
import org.apache.qpid.server.cluster.SimpleSendable;
import org.apache.qpid.server.cluster.policy.StandardPolicies;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

import java.util.List;

/**
 * Basic template for handling methods that should be broadcast to the group and
 * processed locally after 'completion' of this broadcast.
 *
 */
class ReplicatingHandler<A extends AMQMethodBody> extends ClusterMethodHandler<A> implements StandardPolicies
{
    protected static final Logger _logger = Logger.getLogger(ReplicatingHandler.class);

    private final StateAwareMethodListener<A> _base;
    private final GroupManager _groupMgr;
    private final BroadcastPolicy _policy;

    ReplicatingHandler(GroupManager groupMgr, StateAwareMethodListener<A> base)
    {
        this(groupMgr, base, null);
    }

    ReplicatingHandler(GroupManager groupMgr, StateAwareMethodListener<A> base, BroadcastPolicy policy)
    {
        _groupMgr = groupMgr;
        _base = base;
        _policy = policy;
    }

    protected void peer(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges, AMQProtocolSession session, AMQMethodEvent<A> evt) throws AMQException
    {
        local(stateMgr, queues, exchanges, session, evt);
        _logger.debug(new LogMessage("Handled {0} locally", evt.getMethod()));
    }

    protected void client(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges, AMQProtocolSession session, AMQMethodEvent<A> evt) throws AMQException
    {
        replicate(stateMgr, queues, exchanges, session, evt);
    }

    protected void replicate(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges, AMQProtocolSession session, AMQMethodEvent<A> evt) throws AMQException
    {
        if (_policy == null)
        {
            //asynch delivery
            _groupMgr.broadcast(new SimpleSendable(evt.getMethod()));
            local(stateMgr, queues, exchanges, session, evt);
        }
        else
        {
            Callback callback = new Callback(stateMgr, queues, exchanges, session, evt);
            _groupMgr.broadcast(new SimpleSendable(evt.getMethod()), _policy, callback);
        }
        _logger.debug(new LogMessage("Replicated {0} to peers", evt.getMethod()));
    }

    protected void local(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges, AMQProtocolSession session, AMQMethodEvent<A> evt) throws AMQException
    {
        _base.methodReceived(stateMgr, queues, exchanges, session, evt);
    }

    private class Callback implements GroupResponseHandler
    {
        private final AMQStateManager _stateMgr;
        private final QueueRegistry _queues;
        private final ExchangeRegistry _exchanges;
        private final AMQProtocolSession _session;
        private final AMQMethodEvent<A> _evt;

        Callback(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges, AMQProtocolSession session, AMQMethodEvent<A> evt)
        {
            _stateMgr = stateMgr;
            _queues = queues;
            _exchanges = exchanges;
            _session = session;
            _evt = evt;
        }

        public void response(List<AMQMethodBody> responses, List<Member> members)
        {
            try
            {
                local(_stateMgr, _queues, _exchanges, _session, _evt);
                _logger.debug(new LogMessage("Handled {0} locally, in response to completion of replication", _evt.getMethod()));
            }
            catch (AMQException e)
            {
                _logger.error(new LogMessage("Error handling {0}:{1}", _evt.getMethod(), e), e);
            }
        }
    }
}
