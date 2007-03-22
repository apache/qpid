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
package org.apache.qpid.client.handler.amqp_8_0;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.ConnectionTuneParameters;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionOpenBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.framing.ConnectionTuneOkBody;
import org.apache.qpid.framing.amqp_8_0.ConnectionOpenBodyImpl;
import org.apache.qpid.framing.amqp_8_0.ConnectionTuneOkBodyImpl;
import org.apache.qpid.protocol.AMQMethodEvent;

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
        final AMQProtocolSession protocolSession = stateManager.getProtocolSession();
        ConnectionTuneBody frame = (ConnectionTuneBody) evt.getMethod();

        ConnectionTuneParameters params = protocolSession.getConnectionTuneParameters();
        if (params == null)
        {
            params = new ConnectionTuneParameters();
        }

        params.setFrameMax(frame.getFrameMax());
        params.setChannelMax(frame.getChannelMax());
        params.setHeartbeat(Integer.getInteger("amqj.heartbeat.delay", frame.getHeartbeat()));
        protocolSession.setConnectionTuneParameters(params);

        stateManager.changeState(AMQState.CONNECTION_NOT_OPENED);
        protocolSession.getOutputHandler().sendCommand(evt.getChannelId(),
                                                       createTuneOkBody(params));

        String host = protocolSession.getAMQConnection().getVirtualHost();
        AMQShortString virtualHost = new AMQShortString("/" + host);


        protocolSession.getOutputHandler().sendCommand(evt.getChannelId(),
                                                       createConnectionOpenBody( virtualHost, null, true));
    }

    protected ConnectionOpenBody createConnectionOpenBody(AMQShortString path, AMQShortString capabilities, boolean insist)
    {

        return new ConnectionOpenBodyImpl(path,// virtualHost
            capabilities,	// capabilities
            insist);	// insist

    }

    protected ConnectionTuneOkBody createTuneOkBody(ConnectionTuneParameters params)
    {
        // Be aware of possible changes to parameter order as versions change.
        return new ConnectionTuneOkBodyImpl(
            params.getChannelMax(),	// channelMax
            params.getFrameMax(),	// frameMax
            params.getHeartbeat());	// heartbeat
    }
}
