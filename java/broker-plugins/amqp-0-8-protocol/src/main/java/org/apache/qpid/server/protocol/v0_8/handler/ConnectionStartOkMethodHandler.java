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
package org.apache.qpid.server.protocol.v0_8.handler;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.state.AMQState;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;


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

    public void methodReceived(final AMQProtocolSession<?> connection,
                               ConnectionStartOkBody body,
                               int channelId) throws AMQException
    {
        Broker<?> broker = connection.getBroker();

        _logger.info("SASL Mechanism selected: " + body.getMechanism());
        _logger.info("Locale selected: " + body.getLocale());

        SubjectCreator subjectCreator = connection.getSubjectCreator();
        SaslServer ss = null;
        try
        {
            ss = subjectCreator.createSaslServer(String.valueOf(body.getMechanism()),
                                                 connection.getLocalFQDN(),
                                                 connection.getPeerPrincipal());

            if (ss == null)
            {
                throw body.getConnectionException(AMQConstant.RESOURCE_ERROR, "Unable to create SASL Server:" + body.getMechanism(),
                                                  connection.getMethodRegistry());
            }

            connection.setSaslServer(ss);

            final SubjectAuthenticationResult authResult = subjectCreator.authenticate(ss, body.getResponse());
            //save clientProperties
            connection.setClientProperties(body.getClientProperties());

            MethodRegistry methodRegistry = connection.getMethodRegistry();

            switch (authResult.getStatus())
            {
                case ERROR:
                    Exception cause = authResult.getCause();

                    _logger.info("Authentication failed:" + (cause == null ? "" : cause.getMessage()));

                    connection.changeState(AMQState.CONNECTION_CLOSING);

                    ConnectionCloseBody closeBody =
                            methodRegistry.createConnectionCloseBody(AMQConstant.NOT_ALLOWED.getCode(),    // replyCode
                                                                     AMQConstant.NOT_ALLOWED.getName(),
                                                                     body.getClazz(),
                                                                     body.getMethod());

                    connection.writeFrame(closeBody.generateFrame(0));
                    disposeSaslServer(connection);
                    break;

                case SUCCESS:
                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Connected as: " + authResult.getSubject());
                    }
                    connection.setAuthorizedSubject(authResult.getSubject());

                    connection.changeState(AMQState.CONNECTION_NOT_TUNED);
                    int frameMax = broker.getContextValue(Integer.class, Broker.BROKER_FRAME_SIZE);

                    if(frameMax <= 0)
                    {
                        frameMax = Integer.MAX_VALUE;
                    }

                    ConnectionTuneBody tuneBody = methodRegistry.createConnectionTuneBody(broker.getConnection_sessionCountLimit(),
                                                                                          frameMax,
                                                                                          broker.getConnection_heartBeatDelay());
                    connection.writeFrame(tuneBody.generateFrame(0));
                    break;
                case CONTINUE:
                    connection.changeState(AMQState.CONNECTION_NOT_AUTH);

                    ConnectionSecureBody secureBody = methodRegistry.createConnectionSecureBody(authResult.getChallenge());
                    connection.writeFrame(secureBody.generateFrame(0));
            }
        }
        catch (SaslException e)
        {
            disposeSaslServer(connection);
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



