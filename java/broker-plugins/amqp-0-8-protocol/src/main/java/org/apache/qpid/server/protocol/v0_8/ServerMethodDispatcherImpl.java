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
package org.apache.qpid.server.protocol.v0_8;

import java.security.AccessControlException;
import java.security.PrivilegedAction;

import javax.security.auth.Subject;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class ServerMethodDispatcherImpl implements MethodDispatcher
{
    private static final Logger _logger = Logger.getLogger(ServerMethodDispatcherImpl.class);

    private final AMQProtocolEngine _connection;


    private static interface ChannelAction
    {
        void onChannel(ChannelMethodProcessor channel);
    }

    public static MethodDispatcher createMethodDispatcher(AMQProtocolEngine connection)
    {
        return new ServerMethodDispatcherImpl(connection);
    }


    public ServerMethodDispatcherImpl(AMQProtocolEngine connection)
    {
        _connection = connection;
    }


    protected final AMQProtocolEngine getConnection()
    {
        return _connection;
    }

    private void processChannelMethod(int channelId, final ChannelAction action)
    {
        final AMQChannel channel = _connection.getChannel(channelId);
        if (channel == null)
        {
            closeConnection(AMQConstant.CHANNEL_ERROR, "Unknown channel id: " + channelId);
        }
        else
        {
            Subject.doAs(channel.getSubject(), new PrivilegedAction<Void>()
            {
                @Override
                public Void run()
                {
                    action.onChannel(channel);
                    return null;
                }
            });
        }

    }

    public boolean dispatchAccessRequest(final AccessRequestBody body, int channelId)
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                                {
                                    @Override
                                    public void onChannel(final ChannelMethodProcessor channel)
                                    {
                                        channel.receiveAccessRequest(body.getRealm(),
                                                                     body.getExclusive(),
                                                                     body.getPassive(),
                                                                     body.getActive(),
                                                                     body.getWrite(),
                                                                     body.getRead());
                                    }
                                }
                            );

        return true;
    }

    public boolean dispatchBasicAck(final BasicAckBody body, int channelId)
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                    channel.receiveBasicAck(body.getDeliveryTag(), body.getMultiple());
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchBasicCancel(final BasicCancelBody body, int channelId)
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                    channel.receiveBasicCancel(body.getConsumerTag(),
                                                               body.getNowait()
                                                              );
                                 }
                             }
                            );
        return true;
    }

    public boolean dispatchBasicConsume(final BasicConsumeBody body, int channelId)
    {

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveBasicConsume(body.getQueue(), body.getConsumerTag(),
                                                                 body.getNoLocal(), body.getNoAck(),
                                                                 body.getExclusive(), body.getNowait(),
                                                                 body.getArguments());
                                 }
                             }
                            );


        return true;
    }

    private void closeConnection(final AMQConstant constant,
                                 final String message)
    {
        _connection.closeConnection(constant, message, 0);
    }

    public boolean dispatchBasicGet(final BasicGetBody body, int channelId)
    {

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveBasicGet(body.getQueue(), body.getNoAck());
                                 }
                             }
                            );
        return true;
    }

    public boolean dispatchBasicPublish(final BasicPublishBody body, int channelId)
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveBasicPublish(body.getExchange(), body.getRoutingKey(),
                                                                 body.getMandatory(), body.getImmediate());
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchBasicQos(final BasicQosBody body, int channelId)
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveBasicQos(body.getPrefetchSize(), body.getPrefetchCount(),
                                                             body.getGlobal());
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchBasicRecover(final BasicRecoverBody body, int channelId)
    {
        final boolean sync = _connection.getProtocolVersion().equals(ProtocolVersion.v8_0);

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveBasicRecover(body.getRequeue(), sync);
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchBasicReject(final BasicRejectBody body, int channelId) throws AMQException
    {

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveBasicReject(body.getDeliveryTag(), body.getRequeue());
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchChannelOpen(ChannelOpenBody body, int channelId) throws AMQException
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();

        // Protect the broker against out of order frame request.
        if (virtualHost == null)
        {
            throw new AMQException(AMQConstant.COMMAND_INVALID,
                                   "Virtualhost has not yet been set. ConnectionOpen has not been called.",
                                   null);
        }
        _logger.info("Connecting to: " + virtualHost.getName());

        final AMQChannel channel = new AMQChannel(_connection, channelId, virtualHost.getMessageStore());

        _connection.addChannel(channel);

        ChannelOpenOkBody response;


        response = _connection.getMethodRegistry().createChannelOpenOkBody();


        _connection.writeFrame(response.generateFrame(channelId));
        return true;
    }


    public boolean dispatchAccessRequestOk(AccessRequestOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }


    public boolean dispatchBasicCancelOk(BasicCancelOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicConsumeOk(BasicConsumeOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicDeliver(BasicDeliverBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicGetEmpty(BasicGetEmptyBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicGetOk(BasicGetOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicQosOk(BasicQosOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicReturn(BasicReturnBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchChannelClose(ChannelCloseBody body, int channelId) throws AMQException
    {

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveChannelClose();
                                 }
                             }
                            );

        return true;
    }


    public boolean dispatchChannelCloseOk(ChannelCloseOkBody body, int channelId) throws AMQException
    {

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveChannelCloseOk();
                                 }
                             }
                            );

        return true;
    }


    public boolean dispatchChannelFlow(final ChannelFlowBody body, int channelId) throws AMQException
    {

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveChannelFlow(body.getActive());
                                 }
                             }
                            );
        return true;
    }

    public boolean dispatchChannelFlowOk(ChannelFlowOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchChannelOpenOk(ChannelOpenOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }


    public boolean dispatchConnectionOpen(ConnectionOpenBody body, int channelId) throws AMQException
    {

        //ignore leading '/'
        String virtualHostName;
        if ((body.getVirtualHost() != null) && body.getVirtualHost().charAt(0) == '/')
        {
            virtualHostName =
                    new StringBuilder(body.getVirtualHost().subSequence(1, body.getVirtualHost().length())).toString();
        }
        else
        {
            virtualHostName = body.getVirtualHost() == null ? null : String.valueOf(body.getVirtualHost());
        }

        VirtualHostImpl virtualHost = ((AmqpPort) _connection.getPort()).getVirtualHost(virtualHostName);

        if (virtualHost == null)
        {
            closeConnection(AMQConstant.NOT_FOUND,
                            "Unknown virtual host: '" + virtualHostName + "'");

        }
        else
        {
            // Check virtualhost access
            if (virtualHost.getState() != State.ACTIVE)
            {
                closeConnection(AMQConstant.CONNECTION_FORCED,
                                "Virtual host '" + virtualHost.getName() + "' is not active"
                               );

            }
            else
            {
                _connection.setVirtualHost(virtualHost);
                try
                {
                    virtualHost.getSecurityManager().authoriseCreateConnection(_connection);
                    if (_connection.getContextKey() == null)
                    {
                        _connection.setContextKey(new AMQShortString(Long.toString(System.currentTimeMillis())));
                    }

                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createConnectionOpenOkBody(body.getVirtualHost());

                    _connection.writeFrame(responseBody.generateFrame(channelId));
                }
                catch (AccessControlException e)
                {
                    closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage());
                }
            }
        }
        return true;
    }


    public boolean dispatchConnectionClose(ConnectionCloseBody body, int channelId) throws AMQException
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("ConnectionClose received with reply code/reply text " + body.getReplyCode() + "/" +
                         body.getReplyText() + " for " + _connection);
        }
        try
        {
            _connection.closeSession();
        }
        catch (Exception e)
        {
            _logger.error("Error closing protocol session: " + e, e);
        }

        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        ConnectionCloseOkBody responseBody = methodRegistry.createConnectionCloseOkBody();
        _connection.writeFrame(responseBody.generateFrame(channelId));

        _connection.closeProtocolSession();

        return true;
    }


    public boolean dispatchConnectionCloseOk(ConnectionCloseOkBody body, int channelId) throws AMQException
    {
        _logger.info("Received Connection-close-ok");

        try
        {
            _connection.closeSession();
        }
        catch (Exception e)
        {
            _logger.error("Error closing protocol session: " + e, e);
        }
        return true;
    }

    public boolean dispatchConnectionOpenOk(ConnectionOpenOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchConnectionRedirect(ConnectionRedirectBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchConnectionSecure(ConnectionSecureBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchConnectionStart(ConnectionStartBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchConnectionTune(ConnectionTuneBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }


    public boolean dispatchExchangeBoundOk(ExchangeBoundOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchExchangeDeclareOk(ExchangeDeclareOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchExchangeDeleteOk(ExchangeDeleteOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueueBindOk(QueueBindOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueueDeclareOk(QueueDeclareOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueueDeleteOk(QueueDeleteOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueuePurgeOk(QueuePurgeOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchTxCommitOk(TxCommitOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchTxRollbackOk(TxRollbackOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchTxSelectOk(TxSelectOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }


    public boolean dispatchConnectionSecureOk(ConnectionSecureOkBody body, int channelId) throws AMQException
    {
        Broker<?> broker = _connection.getBroker();

        SubjectCreator subjectCreator = _connection.getSubjectCreator();

        SaslServer ss = _connection.getSaslServer();
        if (ss == null)
        {
            throw new AMQException("No SASL context set up in session");
        }
        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        SubjectAuthenticationResult authResult = subjectCreator.authenticate(ss, body.getResponse());
        switch (authResult.getStatus())
        {
            case ERROR:
                Exception cause = authResult.getCause();

                _logger.info("Authentication failed:" + (cause == null ? "" : cause.getMessage()));

                ConnectionCloseBody connectionCloseBody =
                        methodRegistry.createConnectionCloseBody(AMQConstant.NOT_ALLOWED.getCode(),
                                                                 AMQConstant.NOT_ALLOWED.getName(),
                                                                 body.getClazz(),
                                                                 body.getMethod());

                _connection.writeFrame(connectionCloseBody.generateFrame(0));
                disposeSaslServer(_connection);
                break;
            case SUCCESS:
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Connected as: " + authResult.getSubject());
                }

                int frameMax = broker.getContextValue(Integer.class, Broker.BROKER_FRAME_SIZE);

                if (frameMax <= 0)
                {
                    frameMax = Integer.MAX_VALUE;
                }

                ConnectionTuneBody tuneBody =
                        methodRegistry.createConnectionTuneBody(broker.getConnection_sessionCountLimit(),
                                                                frameMax,
                                                                broker.getConnection_heartBeatDelay());
                _connection.writeFrame(tuneBody.generateFrame(0));
                _connection.setAuthorizedSubject(authResult.getSubject());
                disposeSaslServer(_connection);
                break;
            case CONTINUE:

                ConnectionSecureBody
                        secureBody = methodRegistry.createConnectionSecureBody(authResult.getChallenge());
                _connection.writeFrame(secureBody.generateFrame(0));
        }
        return true;
    }

    private void disposeSaslServer(AMQProtocolEngine connection)
    {
        SaslServer ss = connection.getSaslServer();
        if (ss != null)
        {
            connection.setSaslServer(null);
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

    public boolean dispatchConnectionStartOk(ConnectionStartOkBody body, int channelId) throws AMQException
    {
        Broker<?> broker = _connection.getBroker();

        _logger.info("SASL Mechanism selected: " + body.getMechanism());
        _logger.info("Locale selected: " + body.getLocale());

        SubjectCreator subjectCreator = _connection.getSubjectCreator();
        SaslServer ss = null;
        try
        {
            ss = subjectCreator.createSaslServer(String.valueOf(body.getMechanism()),
                                                 _connection.getLocalFQDN(),
                                                 _connection.getPeerPrincipal());

            if (ss == null)
            {
                closeConnection(AMQConstant.RESOURCE_ERROR,
                                "Unable to create SASL Server:" + body.getMechanism()
                               );

            }
            else
            {

                _connection.setSaslServer(ss);

                final SubjectAuthenticationResult authResult = subjectCreator.authenticate(ss, body.getResponse());
                //save clientProperties
                _connection.setClientProperties(body.getClientProperties());

                MethodRegistry methodRegistry = _connection.getMethodRegistry();

                switch (authResult.getStatus())
                {
                    case ERROR:
                        Exception cause = authResult.getCause();

                        _logger.info("Authentication failed:" + (cause == null ? "" : cause.getMessage()));

                        ConnectionCloseBody closeBody =
                                methodRegistry.createConnectionCloseBody(AMQConstant.NOT_ALLOWED.getCode(),
                                                                         // replyCode
                                                                         AMQConstant.NOT_ALLOWED.getName(),
                                                                         body.getClazz(),
                                                                         body.getMethod());

                        _connection.writeFrame(closeBody.generateFrame(0));
                        disposeSaslServer(_connection);
                        break;

                    case SUCCESS:
                        if (_logger.isInfoEnabled())
                        {
                            _logger.info("Connected as: " + authResult.getSubject());
                        }
                        _connection.setAuthorizedSubject(authResult.getSubject());

                        int frameMax = broker.getContextValue(Integer.class, Broker.BROKER_FRAME_SIZE);

                        if (frameMax <= 0)
                        {
                            frameMax = Integer.MAX_VALUE;
                        }

                        ConnectionTuneBody
                                tuneBody =
                                methodRegistry.createConnectionTuneBody(broker.getConnection_sessionCountLimit(),
                                                                        frameMax,
                                                                        broker.getConnection_heartBeatDelay());
                        _connection.writeFrame(tuneBody.generateFrame(0));
                        break;
                    case CONTINUE:
                        ConnectionSecureBody
                                secureBody = methodRegistry.createConnectionSecureBody(authResult.getChallenge());
                        _connection.writeFrame(secureBody.generateFrame(0));
                }
            }
        }
        catch (SaslException e)
        {
            disposeSaslServer(_connection);
            throw new AMQException("SASL error: " + e, e);
        }
        return true;
    }

    public boolean dispatchConnectionTuneOk(ConnectionTuneOkBody body, int channelId) throws AMQException
    {
        final AMQProtocolEngine connection = getConnection();

        connection.initHeartbeats(body.getHeartbeat());

        int brokerFrameMax = connection.getBroker().getContextValue(Integer.class, Broker.BROKER_FRAME_SIZE);
        if (brokerFrameMax <= 0)
        {
            brokerFrameMax = Integer.MAX_VALUE;
        }

        if (body.getFrameMax() > (long) brokerFrameMax)
        {
            throw new AMQConnectionException(AMQConstant.SYNTAX_ERROR,
                                             "Attempt to set max frame size to " + body.getFrameMax()
                                             + " greater than the broker will allow: "
                                             + brokerFrameMax,
                                             body.getClazz(), body.getMethod(),
                                             connection.getMethodRegistry(), null);
        }
        else if (body.getFrameMax() > 0 && body.getFrameMax() < AMQConstant.FRAME_MIN_SIZE.getCode())
        {
            throw new AMQConnectionException(AMQConstant.SYNTAX_ERROR,
                                             "Attempt to set max frame size to " + body.getFrameMax()
                                             + " which is smaller than the specification definined minimum: "
                                             + AMQConstant.FRAME_MIN_SIZE.getCode(),
                                             body.getClazz(), body.getMethod(),
                                             connection.getMethodRegistry(), null);
        }
        int frameMax = body.getFrameMax() == 0 ? brokerFrameMax : (int) body.getFrameMax();
        connection.setMaxFrameSize(frameMax);

        long maxChannelNumber = body.getChannelMax();
        //0 means no implied limit, except that forced by protocol limitations (0xFFFF)
        connection.setMaximumNumberOfChannels(maxChannelNumber == 0 ? 0xFFFFL : maxChannelNumber);
        return true;
    }

    public boolean dispatchExchangeBound(final ExchangeBoundBody body, int channelId)
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveExchangeBound(body.getExchange(), body.getQueue(), body.getRoutingKey());
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchExchangeDeclare(final ExchangeDeclareBody body, int channelId)
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveExchangeDeclare(body.getExchange(), body.getType(),
                                                                    body.getPassive(),
                                                                    body.getDurable(),
                                                                    body.getAutoDelete(),
                                                                    body.getInternal(),
                                                                    body.getNowait(),
                                                                    body.getArguments());
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchExchangeDelete(final ExchangeDeleteBody body, int channelId)
    {

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveExchangeDelete(body.getExchange(),
                                                                   body.getIfUnused(),
                                                                   body.getNowait());
                                 }
                             }
                            );

        return true;
    }

    private boolean isDefaultExchange(final AMQShortString exchangeName)
    {
        return exchangeName == null || exchangeName.equals(AMQShortString.EMPTY_STRING);
    }

    public boolean dispatchQueueBind(final QueueBindBody body, int channelId)
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveQueueBind(body.getQueue(),
                                                              body.getExchange(),
                                                              body.getRoutingKey(),
                                                              body.getNowait(),
                                                              body.getArguments());
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchQueueDeclare(final QueueDeclareBody body, int channelId)
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveQueueDeclare(body.getQueue(),
                                                                 body.getPassive(),
                                                                 body.getDurable(),
                                                                 body.getExclusive(),
                                                                 body.getAutoDelete(),
                                                                 body.getNowait(),
                                                                 body.getArguments());
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchQueueDelete(final QueueDeleteBody body, int channelId)
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveQueueDelete(body.getQueue(),
                                                                body.getIfUnused(),
                                                                body.getIfEmpty(),
                                                                body.getNowait());
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchQueuePurge(final QueuePurgeBody body, int channelId) throws AMQException
    {

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveQueuePurge(body.getQueue(),
                                                               body.getNowait());
                                 }
                             }
                            );

        return true;
    }


    public boolean dispatchTxCommit(TxCommitBody body, final int channelId) throws AMQException
    {

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveTxCommit();
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchTxRollback(TxRollbackBody body, final int channelId) throws AMQException
    {

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveTxRollback();
                                 }
                             }
                            );
        return true;
    }

    public boolean dispatchTxSelect(TxSelectBody body, int channelId) throws AMQException
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveTxSelect();
                                 }
                             }
                            );
        return true;
    }

    public boolean dispatchBasicRecoverSync(final BasicRecoverSyncBody body, int channelId) throws AMQException
    {
        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveBasicRecover(body.getRequeue(), true);
                                 }
                             }
                            );

        return true;
    }

    public boolean dispatchBasicRecoverSyncOk(BasicRecoverSyncOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    @Override
    public boolean dispatchChannelAlert(final ChannelAlertBody body, final int channelId)
            throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueueUnbindOk(QueueUnbindOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueueUnbind(final QueueUnbindBody body, int channelId) throws AMQException
    {

        processChannelMethod(channelId,
                             new ChannelAction()
                             {
                                 @Override
                                 public void onChannel(final ChannelMethodProcessor channel)
                                 {
                                     channel.receiveQueueUnbind(body.getQueue(),
                                                                body.getExchange(),
                                                                body.getRoutingKey(),
                                                                body.getArguments());
                                 }
                             }
                            );

        return true;
    }

}
