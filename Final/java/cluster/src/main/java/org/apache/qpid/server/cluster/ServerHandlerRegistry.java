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
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.AMQStateManager;
//import org.apache.qpid.server.state.IllegalStateTransitionException;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * An extension of server.AMQStateManager that allows different handlers to be registered.
 *
 */
class ServerHandlerRegistry extends AMQStateManager
{
    private final Logger _logger = Logger.getLogger(ServerHandlerRegistry.class);
    private final Map<AMQState, MethodHandlerRegistry> _handlers = new HashMap<AMQState, MethodHandlerRegistry>();

    ServerHandlerRegistry(VirtualHostRegistry virtualHostRegistry, AMQProtocolSession protocolSession)
    {
        super(AMQState.CONNECTION_NOT_STARTED, false, virtualHostRegistry, protocolSession);
    }

    ServerHandlerRegistry(ServerHandlerRegistry s, VirtualHostRegistry virtualHostRegistry, AMQProtocolSession protocolSession)
    {
        this(virtualHostRegistry, protocolSession);
        _handlers.putAll(s._handlers);
    }

    ServerHandlerRegistry(MethodHandlerFactory factory, VirtualHostRegistry virtualHostRegistry, AMQProtocolSession protocolSession)
    {
        this(virtualHostRegistry, protocolSession);
        init(factory);
    }

    void setHandlers(AMQState state, MethodHandlerRegistry handlers)
    {
        _handlers.put(state, handlers);
    }

    void init(MethodHandlerFactory factory)
    {
        for (AMQState s : AMQState.values())
        {
            setHandlers(s, factory.register(s, new MethodHandlerRegistry()));
        }
    }

    protected <B extends AMQMethodBody> StateAwareMethodListener<B> findStateTransitionHandler(AMQState state, B frame) //throws IllegalStateTransitionException
    {
        MethodHandlerRegistry registry = _handlers.get(state);
        StateAwareMethodListener<B> handler = (registry == null) ? null : registry.getHandler(frame);
        if (handler == null)
        {
            _logger.warn(new LogMessage("No handler for {0}, {1}", state, frame));
        }
        return handler;
    }

    <A extends AMQMethodBody, B extends Class<A>> void addHandler(AMQState state, B type, StateAwareMethodListener<A> handler)
    {
        MethodHandlerRegistry registry = _handlers.get(state);
        if (registry == null)
        {
            registry = new MethodHandlerRegistry();
            _handlers.put(state, registry);
        }
        registry.addHandler(type, handler);
    }
}
