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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionSecureOkBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.HeartbeatConfig;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

public class ConnectionSecureOkMethodHandler implements StateAwareMethodListener<ConnectionSecureOkBody>
{
    private static final Logger _logger = Logger.getLogger(ConnectionSecureOkMethodHandler.class);

    private static ConnectionSecureOkMethodHandler _instance = new ConnectionSecureOkMethodHandler();

    public static ConnectionSecureOkMethodHandler getInstance()
    {
        return _instance;
    }

    private ConnectionSecureOkMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ConnectionSecureOkBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        ConnectionSecureOkBody body = evt.getMethod();

        //fixme Vhost not defined yet
        //session.getVirtualHost().getAuthenticationManager();
        AuthenticationManager authMgr = ApplicationRegistry.getInstance().getAuthenticationManager();

        SaslServer ss = session.getSaslServer();
        if (ss == null)
        {
            throw new AMQException("No SASL context set up in session");
        }

        AuthenticationResult authResult = authMgr.authenticate(ss, body.response);
        switch (authResult.status)
        {
            case ERROR:
                // Can't do this as we violate protocol. Need to send Close
                // throw new AMQException(AMQConstant.NOT_ALLOWED.getCode(), AMQConstant.NOT_ALLOWED.getName());
                _logger.info("Authentication failed");
                stateManager.changeState(AMQState.CONNECTION_CLOSING);
                // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                // Be aware of possible changes to parameter order as versions change.
                AMQFrame close = ConnectionCloseBody.createAMQFrame(0,
                    (byte)8, (byte)0,	// AMQP version (major, minor)
                    ConnectionCloseBody.getClazz((byte)8, (byte)0),		// classId
                    ConnectionCloseBody.getMethod((byte)8, (byte)0),	// methodId
                    AMQConstant.NOT_ALLOWED.getCode(),	// replyCode
                    AMQConstant.NOT_ALLOWED.getName());	// replyText
                session.writeFrame(close);
                disposeSaslServer(session);
                break;
            case SUCCESS:
                _logger.info("Connected as: " + ss.getAuthorizationID());
                stateManager.changeState(AMQState.CONNECTION_NOT_TUNED);
                // TODO: Check the value of channelMax here: This should be the max
                // value of a 2-byte unsigned integer (as channel is only 2 bytes on the wire),
                // not Integer.MAX_VALUE (which is signed 4 bytes).
                // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                // Be aware of possible changes to parameter order as versions change.
                AMQFrame tune = ConnectionTuneBody.createAMQFrame(0,
                    (byte)8, (byte)0,	// AMQP version (major, minor)
                    Integer.MAX_VALUE,	// channelMax
                    ConnectionStartOkMethodHandler.getConfiguredFrameSize(),	// frameMax
                    HeartbeatConfig.getInstance().getDelay());	// heartbeat
                session.writeFrame(tune);
                session.setAuthorizedID(new UsernamePrincipal(ss.getAuthorizationID()));                
                disposeSaslServer(session);
                break;
            case CONTINUE:
                stateManager.changeState(AMQState.CONNECTION_NOT_AUTH);
                // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                // Be aware of possible changes to parameter order as versions change.
                AMQFrame challenge = ConnectionSecureBody.createAMQFrame(0,
                    (byte)8, (byte)0,	// AMQP version (major, minor)
                    authResult.challenge);	// challenge
                session.writeFrame(challenge);
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
}
