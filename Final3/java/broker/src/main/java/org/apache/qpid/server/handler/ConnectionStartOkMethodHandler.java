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

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.HeartbeatConfig;
import org.apache.qpid.server.protocol.AMQMinaProtocolSession;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;


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

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ConnectionStartOkBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        final ConnectionStartOkBody body = evt.getMethod();
        _logger.info("SASL Mechanism selected: " + body.mechanism);
        _logger.info("Locale selected: " + body.locale);

        AuthenticationManager authMgr = ApplicationRegistry.getInstance().getAuthenticationManager();//session.getVirtualHost().getAuthenticationManager();

        SaslServer ss = null;
        try
        {                       
            ss = authMgr.createSaslServer(String.valueOf(body.mechanism), session.getLocalFQDN());

            if (ss == null)
            {
                throw body.getConnectionException(AMQConstant.RESOURCE_ERROR, "Unable to create SASL Server:" + body.mechanism
                );
            }

            session.setSaslServer(ss);

            AuthenticationResult authResult = authMgr.authenticate(ss, body.response);

            //save clientProperties
            if (session.getClientProperties() == null)
            {
                session.setClientProperties(body.clientProperties);
            }

            switch (authResult.status)
            {
                case ERROR:
                    _logger.info("Authentication failed");
                    stateManager.changeState(AMQState.CONNECTION_CLOSING);
                    // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                    // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                    // Be aware of possible changes to parameter order as versions change.
                    AMQFrame close = ConnectionCloseBody.createAMQFrame(0,
                                                                        (byte) 8, (byte) 0,    // AMQP version (major, minor)
                                                                        ConnectionCloseBody.getClazz((byte) 8, (byte) 0),        // classId
                                                                        ConnectionCloseBody.getMethod((byte) 8, (byte) 0),    // methodId
                                                                        AMQConstant.NOT_ALLOWED.getCode(),    // replyCode
                                                                        AMQConstant.NOT_ALLOWED.getName());    // replyText
                    session.writeFrame(close);
                    disposeSaslServer(session);
                    break;

                case SUCCESS:
                    _logger.info("Connected as: " + ss.getAuthorizationID());
                    session.setAuthorizedID(new UsernamePrincipal(ss.getAuthorizationID()));

                    stateManager.changeState(AMQState.CONNECTION_NOT_TUNED);
                    // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                    // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                    // Be aware of possible changes to parameter order as versions change.
                    AMQFrame tune = ConnectionTuneBody.createAMQFrame(0,
                                                                      (byte) 8, (byte) 0,    // AMQP version (major, minor)
                                                                      Integer.MAX_VALUE,    // channelMax
                                                                      getConfiguredFrameSize(),    // frameMax
                                                                      HeartbeatConfig.getInstance().getDelay());    // heartbeat
                    session.writeFrame(tune);
                    break;
                case CONTINUE:
                    stateManager.changeState(AMQState.CONNECTION_NOT_AUTH);
                    // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                    // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                    // Be aware of possible changes to parameter order as versions change.
                    AMQFrame challenge = ConnectionSecureBody.createAMQFrame(0,
                                                                             (byte) 8, (byte) 0,    // AMQP version (major, minor)
                                                                             authResult.challenge);    // challenge
                    session.writeFrame(challenge);
            }
        }
        catch (SaslException e)
        {
            disposeSaslServer(session);
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
        final int framesize = config.getInt("advanced.framesize", DEFAULT_FRAME_SIZE);
        _logger.info("Framesize set to " + framesize);
        return framesize;
    }
}



