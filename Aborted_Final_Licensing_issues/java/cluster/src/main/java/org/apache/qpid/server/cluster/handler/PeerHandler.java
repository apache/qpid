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
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.server.cluster.ClusteredProtocolSession;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

/**
 * Base for implementing handlers that carry out different actions based on whether the method they
 * are handling was sent by a peer (i.e. another broker in the cluster) or a client (i.e. an end-user
 * application).
 *
 */
public class PeerHandler<A extends AMQMethodBody> extends ClusterMethodHandler<A>
{
    private final StateAwareMethodListener<A> _peer;
    private final StateAwareMethodListener<A> _client;

    PeerHandler(StateAwareMethodListener<A> peer, StateAwareMethodListener<A> client)
    {
        _peer = peer;
        _client = client;
    }

    protected void peer(AMQStateManager stateMgr, AMQMethodEvent<A> evt) throws AMQException
    {
        _peer.methodReceived(stateMgr, evt);
    }

    protected void client(AMQStateManager stateMgr, AMQMethodEvent<A> evt) throws AMQException
    {
        _client.methodReceived(stateMgr, evt);
    }

}
