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

import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;

import java.util.List;
import java.util.ArrayList;

public class ChainedClusterMethodHandler <A extends AMQMethodBody> extends ClusterMethodHandler<A>
{
    private final List<ClusterMethodHandler<A>> _handlers;

    private ChainedClusterMethodHandler()
    {
        this(new ArrayList<ClusterMethodHandler<A>>());
    }

    public ChainedClusterMethodHandler(List<ClusterMethodHandler<A>> handlers)
    {
        _handlers = handlers;
    }

    public ChainedClusterMethodHandler(ClusterMethodHandler<A>... handlers)
    {
        this();
        for(ClusterMethodHandler<A>handler: handlers)
        {
            _handlers.add(handler);
        }
    }

    protected final void peer(AMQStateManager stateMgr, AMQMethodEvent<A> evt) throws AMQException
    {
        for(ClusterMethodHandler<A> handler : _handlers)
        {
            handler.peer(stateMgr, evt);
        }
    }

    protected final void client(AMQStateManager stateMgr,  AMQMethodEvent<A> evt) throws AMQException
    {
        for(ClusterMethodHandler<A> handler : _handlers)
        {
            handler.client(stateMgr, evt);
        }
    }
}
