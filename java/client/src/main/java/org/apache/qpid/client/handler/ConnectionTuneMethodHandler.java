/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.apache.qpid.framing.ConnectionOpenBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.framing.ConnectionTuneOkBody;
import org.apache.qpid.framing.AMQFrame;

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
        session.writeFrame(createConnectionOpenFrame(evt.getChannelId(), session.getAMQConnection().getVirtualHost(), null, true));
    }

    protected AMQFrame createConnectionOpenFrame(int channel, String path, String capabilities, boolean insist)
    {
        return ConnectionOpenBody.createAMQFrame(channel, path, capabilities, insist);
    }

    protected AMQFrame createTuneOkFrame(int channel, ConnectionTuneParameters params)
    {
        return ConnectionTuneOkBody.createAMQFrame(channel, params.getChannelMax(), params.getFrameMax(), params.getHeartbeat());
    }
}
