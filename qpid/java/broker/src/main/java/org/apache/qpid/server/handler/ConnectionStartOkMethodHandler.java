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

import org.apache.log4j.Logger;
import org.apache.commons.configuration.Configuration;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.HeartbeatConfig;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.AuthenticationManager;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;


public class ConnectionStartOkMethodHandler implements StateAwareMethodListener<ConnectionStartOkBody>
{
    private static final Logger _logger = Logger.getLogger(ConnectionStartOkMethodHandler.class);

    private static ConnectionStartOkMethodHandler _instance = new ConnectionStartOkMethodHandler();

    private static final int DEFAULT_FRAME_SIZE = 65536;

    public static StateAwareMethodListener<ConnectionStartOkBody> getInstance()
    {
        return _instance;
    }

    private ConnectionStartOkMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, QueueRegistry queueRegistry,
                               ExchangeRegistry exchangeRegistry, AMQProtocolSession protocolSession,
                               AMQMethodEvent<ConnectionStartOkBody> evt) throws AMQException
    {
        final ConnectionStartOkBody body = evt.getMethod();
        _logger.info("SASL Mechanism selected: " + body.mechanism);
        _logger.info("Locale selected: " + body.locale);

        AuthenticationManager authMgr = ApplicationRegistry.getInstance().getAuthenticationManager();

        SaslServer ss = null;
        try
        {
            ss = authMgr.createSaslServer(body.mechanism, protocolSession.getLocalFQDN());
            protocolSession.setSaslServer(ss);

            AuthenticationResult authResult = authMgr.authenticate(ss, body.response);

            switch (authResult.status)
            {
                case ERROR:
                    throw new AMQException("Authentication failed");
                case SUCCESS:
                    _logger.info("Connected as: " + ss.getAuthorizationID());
                    stateManager.changeState(AMQState.CONNECTION_NOT_TUNED);
                    AMQFrame tune = ConnectionTuneBody.createAMQFrame(0, Integer.MAX_VALUE, getConfiguredFrameSize(),
                                                                      HeartbeatConfig.getInstance().getDelay());
                    protocolSession.writeFrame(tune);
                    break;
                case CONTINUE:
                    stateManager.changeState(AMQState.CONNECTION_NOT_AUTH);
                    AMQFrame challenge = ConnectionSecureBody.createAMQFrame(0, authResult.challenge);
                    protocolSession.writeFrame(challenge);
            }
        }
        catch (SaslException e)
        {
            disposeSaslServer(protocolSession);
            throw new AMQException("SASL error: " + e, e);
        }
    }

    private void disposeSaslServer(AMQProtocolSession ps)
    {
        SaslServer ss = ps.getSaslServer();
        if (ss != null)
        {
            ps.setSaslServer(null);
            try
            {
                ss.dispose();
            }
            catch (SaslException e)
            {
                _logger.error("Error disposing of Sasl server: " + e);
            }
        }
    }

    static int getConfiguredFrameSize()
    {
        final Configuration config = ApplicationRegistry.getInstance().getConfiguration();
        final int framesize =  config.getInt("advanced.framesize", DEFAULT_FRAME_SIZE);
        _logger.info("Framesize set to " + framesize);
        return framesize;
    }
}

