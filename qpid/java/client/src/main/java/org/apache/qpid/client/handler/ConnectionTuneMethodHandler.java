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
package org.apache.qpid.client.handler;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.ConnectionTuneParameters;
import org.apache.qpid.client.protocol.AMQMethodEvent;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.*;

public class ConnectionTuneMethodHandler implements StateAwareMethodListener
{
    private static final Logger _logger = Logger.getLogger(ConnectionTuneMethodHandler.class);

    private static final ConnectionTuneMethodHandler _instance = new ConnectionTuneMethodHandler();

    public static ConnectionTuneMethodHandler getInstance()
    {
        return _instance;
    }

    protected ConnectionTuneMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent evt) throws AMQException
    {
        _logger.debug("ConnectionTune frame received");
        ConnectionTuneBody frame = (ConnectionTuneBody) evt.getMethod();
        AMQProtocolSession session = evt.getProtocolSession();

        ConnectionTuneParameters params = session.getConnectionTuneParameters();
        if (params == null)
        {
            params = new ConnectionTuneParameters();
        }

        params.setFrameMax(frame.frameMax);        
        params.setChannelMax(frame.channelMax);
        params.setHeartbeat(Integer.getInteger("amqj.heartbeat.delay", frame.heartbeat));
        session.setConnectionTuneParameters(params);

        stateManager.changeState(AMQState.CONNECTION_NOT_OPENED);
        session.writeFrame(createTuneOkFrame(evt.getChannelId(), params));
        session.writeFrame(createConnectionOpenFrame(evt.getChannelId(), new AMQShortString(session.getAMQConnection().getVirtualHost()), null, true));
    }

    protected AMQFrame createConnectionOpenFrame(int channel, AMQShortString path, AMQShortString capabilities, boolean insist)
    {
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        return ConnectionOpenBody.createAMQFrame(channel,
            (byte)8, (byte)0,	// AMQP version (major, minor)
            capabilities,	// capabilities
            insist,	// insist
            path);	// virtualHost
    }

    protected AMQFrame createTuneOkFrame(int channel, ConnectionTuneParameters params)
    {
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        return ConnectionTuneOkBody.createAMQFrame(channel,
            (byte)8, (byte)0,	// AMQP version (major, minor)
            params.getChannelMax(),	// channelMax
            params.getFrameMax(),	// frameMax
            params.getHeartbeat());	// heartbeat
    }
}
