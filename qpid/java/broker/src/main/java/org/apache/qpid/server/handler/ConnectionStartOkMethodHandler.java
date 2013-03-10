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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;


public class ConnectionStartOkMethodHandler implements StateAwareMethodListener<ConnectionStartOkBody>
{
    private static final Logger _logger = Logger.getLogger(ConnectionStartOkMethodHandler.class);

    private static ConnectionStartOkMethodHandler _instance = new ConnectionStartOkMethodHandler();

    public static ConnectionStartOkMethodHandler getInstance()
    {
        return _instance;
    }

    private ConnectionStartOkMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, ConnectionStartOkBody body, int channelId) throws AMQException
    {
        Broker broker = stateManager.getBroker();
        AMQProtocolSession session = stateManager.getProtocolSession();

        _logger.info("SASL Mechanism selected: " + body.getMechanism());
        _logger.info("Locale selected: " + body.getLocale());

        SubjectCreator subjectCreator = stateManager.getSubjectCreator();
        SaslServer ss = null;
        try
        {
            ss = subjectCreator.createSaslServer(String.valueOf(body.getMechanism()), session.getLocalFQDN(), session.getPeerPrincipal());

            if (ss == null)
            {
                throw body.getConnectionException(AMQConstant.RESOURCE_ERROR, "Unable to create SASL Server:" + body.getMechanism());
            }

            session.setSaslServer(ss);

            final SubjectAuthenticationResult authResult = subjectCreator.authenticate(ss, body.getResponse());
            //save clientProperties
            session.setClientProperties(body.getClientProperties());

            MethodRegistry methodRegistry = session.getMethodRegistry();

            switch (authResult.getStatus())
            {
                case ERROR:
                    Exception cause = authResult.getCause();

                    _logger.info("Authentication failed:" + (cause == null ? "" : cause.getMessage()));

                    stateManager.changeState(AMQState.CONNECTION_CLOSING);

                    ConnectionCloseBody closeBody =
                            methodRegistry.createConnectionCloseBody(AMQConstant.NOT_ALLOWED.getCode(),    // replyCode
                                                                     AMQConstant.NOT_ALLOWED.getName(),
                                                                     body.getClazz(),
                                                                     body.getMethod());

                    session.writeFrame(closeBody.generateFrame(0));
                    disposeSaslServer(session);
                    break;

                case SUCCESS:
                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Connected as: " + authResult.getSubject());
                    }
                    session.setAuthorizedSubject(authResult.getSubject());

                    stateManager.changeState(AMQState.CONNECTION_NOT_TUNED);

                    ConnectionTuneBody tuneBody = methodRegistry.createConnectionTuneBody((Integer)broker.getAttribute(Broker.SESSION_COUNT_LIMIT),
                                                                                          BrokerProperties.FRAME_SIZE,
                                                                                          (Integer)broker.getAttribute(Broker.HEART_BEAT_DELAY));
                    session.writeFrame(tuneBody.generateFrame(0));
                    break;
                case CONTINUE:
                    stateManager.changeState(AMQState.CONNECTION_NOT_AUTH);

                    ConnectionSecureBody secureBody = methodRegistry.createConnectionSecureBody(authResult.getChallenge());
                    session.writeFrame(secureBody.generateFrame(0));
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

}



