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
package org.apache.qpid.server.handler;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ConnectionOpenBody;
import org.apache.qpid.framing.ConnectionOpenOkBody;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

public class ConnectionOpenMethodHandler implements StateAwareMethodListener<ConnectionOpenBody>
{
    private static ConnectionOpenMethodHandler _instance = new ConnectionOpenMethodHandler();

    public static ConnectionOpenMethodHandler getInstance()
    {
        return _instance;
    }

    private ConnectionOpenMethodHandler()
    {
    }

    private static String generateClientID()
    {
        return Long.toString(System.currentTimeMillis());
    }

    public void methodReceived(AMQStateManager stateManager, QueueRegistry queueRegistry,
                               ExchangeRegistry exchangeRegistry, AMQProtocolSession protocolSession,
                               AMQMethodEvent<ConnectionOpenBody> evt) throws AMQException
    {
        ConnectionOpenBody body = evt.getMethod();
        String contextKey = body.virtualHost;

        //todo //FIXME The virtual host must be validated by the server for the connection to open-ok
        // See Spec (0.8.2). Section  3.1.2 Virtual Hosts
        if (contextKey == null)
        {
            contextKey = generateClientID();
        }
        protocolSession.setContextKey(contextKey);
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        AMQFrame response = ConnectionOpenOkBody.createAMQFrame((short)0,
            (byte)8, (byte)0,	// AMQP version (major, minor)
            contextKey);	// knownHosts
        stateManager.changeState(AMQState.CONNECTION_OPEN);
        protocolSession.writeFrame(response);
    }
}
