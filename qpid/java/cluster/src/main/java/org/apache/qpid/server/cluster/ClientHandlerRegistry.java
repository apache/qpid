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

import org.apache.qpid.client.handler.ConnectionCloseMethodHandler;
import org.apache.qpid.client.handler.ConnectionOpenOkMethodHandler;
import org.apache.qpid.client.handler.ConnectionSecureMethodHandler;
import org.apache.qpid.client.handler.ConnectionStartMethodHandler;
import org.apache.qpid.client.handler.ConnectionTuneMethodHandler;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.AMQStateManager;
// import org.apache.qpid.client.state.IllegalStateTransitionException;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.framing.*;

import java.util.HashMap;
import java.util.Map;

/**
 * An extension of client.AMQStateManager that allows different handlers to be registered.
 *
 */
public class ClientHandlerRegistry extends AMQStateManager
{
    private final Map<AMQState, ClientRegistry> _handlers = new HashMap<AMQState, ClientRegistry>();
    private final MemberHandle _identity;

    protected ClientHandlerRegistry(MemberHandle local, AMQProtocolSession protocolSession)
    {
        super(AMQState.CONNECTION_NOT_STARTED, false, protocolSession);

        _identity = local;

        addHandler(ConnectionStartBody.class, ConnectionStartMethodHandler.getInstance(),
                   AMQState.CONNECTION_NOT_STARTED);

        addHandler(ConnectionTuneBody.class, new ConnectionTuneHandler(),
                   AMQState.CONNECTION_NOT_TUNED);
        addHandler(ConnectionSecureBody.class, ConnectionSecureMethodHandler.getInstance(),
                   AMQState.CONNECTION_NOT_TUNED);
        addHandler(ConnectionOpenOkBody.class, ConnectionOpenOkMethodHandler.getInstance(),
                   AMQState.CONNECTION_NOT_OPENED);

        addHandlers(ConnectionCloseBody.class, ConnectionCloseMethodHandler.getInstance(),
                    AMQState.CONNECTION_NOT_STARTED,
                    AMQState.CONNECTION_NOT_TUNED,
                    AMQState.CONNECTION_NOT_OPENED);

    }

    private ClientRegistry state(AMQState state)
    {
        ClientRegistry registry = _handlers.get(state);
        if (registry == null)
        {
            registry = new ClientRegistry();
            _handlers.put(state, registry);
        }
        return registry;
    }

    protected StateAwareMethodListener findStateTransitionHandler(AMQState state, AMQMethodBody frame) //throws IllegalStateTransitionException
    {
        ClientRegistry registry = _handlers.get(state);
        return registry == null ? null : registry.getHandler(frame);
    }


    <A extends Class<AMQMethodBody>> void addHandlers(Class type, StateAwareMethodListener handler, AMQState... states)
    {
        for (AMQState state : states)
        {
            addHandler(type, handler, state);
        }
    }

    <A extends Class<AMQMethodBody>> void addHandler(Class type, StateAwareMethodListener handler, AMQState state)
    {
        ClientRegistry registry = _handlers.get(state);
        if (registry == null)
        {
            registry = new ClientRegistry();
            _handlers.put(state, registry);
        }
        registry.add(type, handler);
    }

    static class ClientRegistry
    {
        private final Map<Class<? extends AMQMethodBody>, StateAwareMethodListener> registry
                = new HashMap<Class<? extends AMQMethodBody>, StateAwareMethodListener>();

        <A extends Class<AMQMethodBody>> void add(A type, StateAwareMethodListener handler)
        {
            registry.put(type, handler);
        }

        StateAwareMethodListener getHandler(AMQMethodBody frame)
        {
            return registry.get(frame.getClass());
        }
    }

    class ConnectionTuneHandler extends ConnectionTuneMethodHandler
    {
        protected AMQFrame createConnectionOpenFrame(int channel, AMQShortString path, AMQShortString capabilities, boolean insist, byte major, byte minor)
        {
            // Be aware of possible changes to parameter order as versions change.
            return ConnectionOpenBody.createAMQFrame(channel,
                                                     major,
                                                     minor,
                                                     // AMQP version (major, minor)
                                                     new AMQShortString(ClusterCapability.add(capabilities, _identity)),
                                                     // capabilities
                                                     insist,
                                                     // insist
                                                     path);
        }
    }
}
