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
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.HeartbeatConfig;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.security.auth.AuthenticationManager;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.log4j.Logger;

import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslException;

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

    public void methodReceived(AMQProtocolSession protocolSession,
                               AMQMethodEvent<ConnectionSecureOkBody> evt) throws AMQException
    {
        ConnectionSecureOkBody body = evt.getMethod();

        AuthenticationManager authMgr = ApplicationRegistry.getInstance().getAuthenticationManager();
        SaslServer ss = protocolSession.getSaslServer();
        if (ss == null)
        {
            throw new AMQException("No SASL context set up in session");
        }

        AuthenticationResult authResult = authMgr.authenticate(ss, body.response);
        AMQStateManager stateManager = protocolSession.getStateManager();
        byte major = protocolSession.getMajor();
        byte minor = protocolSession.getMinor();
        switch (authResult.status)
        {
            case ERROR:
                // Can't do this as we violate protocol. Need to send Close
                // throw new AMQException(AMQConstant.NOT_ALLOWED.getCode(), AMQConstant.NOT_ALLOWED.getName());
                _logger.info("Authentication failed");
                stateManager.changeState(AMQState.CONNECTION_CLOSING);
                // Be aware of possible changes to parameter order as versions change.
                AMQMethodBody close = ConnectionCloseBody.createMethodBody(
                    major, minor,	// AMQP version (major, minor)
                    ConnectionCloseBody.getClazz(major, minor),		// classId
                    ConnectionCloseBody.getMethod(major, minor),	// methodId
                    AMQConstant.NOT_ALLOWED.getCode(),	// replyCode
                    AMQConstant.NOT_ALLOWED.getName());	// replyText
                protocolSession.writeResponse(evt, close);
                disposeSaslServer(protocolSession);
                break;
            case SUCCESS:
                _logger.info("Connected as: " + ss.getAuthorizationID());
                stateManager.changeState(AMQState.CONNECTION_NOT_TUNED);
                // TODO: Check the value of channelMax here: This should be the max
                // value of a 2-byte unsigned integer (as channel is only 2 bytes on the wire),
                // not Integer.MAX_VALUE (which is signed 4 bytes).
                // Be aware of possible changes to parameter order as versions change.
                AMQMethodBody tune = ConnectionTuneBody.createMethodBody(
                    major, minor,	// AMQP version (major, minor)
                    Integer.MAX_VALUE,	// channelMax
                    ConnectionStartOkMethodHandler.getConfiguredFrameSize(),	// frameMax
                    HeartbeatConfig.getInstance().getDelay());	// heartbeat
                protocolSession.writeResponse(evt, tune);
                disposeSaslServer(protocolSession);
                break;
            case CONTINUE:
                stateManager.changeState(AMQState.CONNECTION_NOT_AUTH);
                // Be aware of possible changes to parameter order as versions change.
                AMQMethodBody challenge = ConnectionSecureBody.createMethodBody(
                    major, minor,	// AMQP version (major, minor)
                    authResult.challenge);	// challenge
                protocolSession.writeResponse(evt, challenge);
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
