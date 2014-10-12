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

import java.security.PrivilegedAction;

import javax.security.auth.Subject;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.model.Broker;

public class ServerMethodDispatcherImpl implements MethodDispatcher
{
    private static final Logger _logger = Logger.getLogger(ServerMethodDispatcherImpl.class);

    private final AMQProtocolEngine _connection;


    private static interface ChannelAction
    {
        void onChannel(ChannelMethodProcessor channel);
    }


    private static interface ConnectionAction
    {
        void onConnection(ConnectionMethodProcessor connection);
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

    private void processConnectionMethod(final ConnectionAction action)
    {
            Subject.doAs(_connection.getSubject(), new PrivilegedAction<Void>()
            {
                @Override
                public Void run()
                {
                    action.onConnection(_connection);
                    return null;
                }
            });


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

    public boolean dispatchBasicReject(final BasicRejectBody body, int channelId)
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

    public boolean dispatchChannelOpen(ChannelOpenBody body, final int channelId)
    {
        processConnectionMethod(new ConnectionAction()
        {
            @Override
            public void onConnection(final ConnectionMethodProcessor connection)
            {
                connection.receiveChannelOpen(channelId);
            }
        });
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

    public boolean dispatchChannelClose(ChannelCloseBody body, int channelId)
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


    public boolean dispatchChannelCloseOk(ChannelCloseOkBody body, int channelId)
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


    public boolean dispatchChannelFlow(final ChannelFlowBody body, int channelId)
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


    public boolean dispatchConnectionOpen(final ConnectionOpenBody body, int channelId)
    {
        processConnectionMethod(new ConnectionAction()
        {
            @Override
            public void onConnection(final ConnectionMethodProcessor connection)
            {
                connection.receiveConnectionOpen(body.getVirtualHost(), body.getCapabilities(), body.getInsist());
            }
        });

        return true;
    }


    public boolean dispatchConnectionClose(final ConnectionCloseBody body, int channelId)
    {

        processConnectionMethod(new ConnectionAction()
        {
            @Override
            public void onConnection(final ConnectionMethodProcessor connection)
            {
                connection.receiveConnectionClose(body.getReplyCode(),
                                                  body.getReplyText(),
                                                  body.getClassId(),
                                                  body.getMethodId());
            }
        });

        return true;
    }


    public boolean dispatchConnectionCloseOk(ConnectionCloseOkBody body, int channelId)
    {

        processConnectionMethod(new ConnectionAction()
        {
            @Override
            public void onConnection(final ConnectionMethodProcessor connection)
            {
                connection.receiveConnectionCloseOk();
            }
        });

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


    public boolean dispatchConnectionSecureOk(final ConnectionSecureOkBody body, int channelId)
    {

        processConnectionMethod(new ConnectionAction()
        {
            @Override
            public void onConnection(final ConnectionMethodProcessor connection)
            {
                connection.receiveConnectionSecureOk(body.getResponse());
            }
        });

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

    public boolean dispatchConnectionStartOk(final ConnectionStartOkBody body, int channelId)
    {

        processConnectionMethod(new ConnectionAction()
        {
            @Override
            public void onConnection(final ConnectionMethodProcessor connection)
            {
                connection.receiveConnectionStartOk(body.getClientProperties(),
                                                    body.getMechanism(),
                                                    body.getResponse(),
                                                    body.getLocale());
            }
        });

        return true;
    }

    public boolean dispatchConnectionTuneOk(final ConnectionTuneOkBody body, int channelId)
    {

        processConnectionMethod(new ConnectionAction()
        {
            @Override
            public void onConnection(final ConnectionMethodProcessor connection)
            {
                connection.receiveConnectionTuneOk(body.getChannelMax(),
                                                    body.getFrameMax(),
                                                    body.getHeartbeat());
            }
        });
        final AMQProtocolEngine connection = getConnection();


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

    public boolean dispatchQueuePurge(final QueuePurgeBody body, int channelId)
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


    public boolean dispatchTxCommit(TxCommitBody body, final int channelId)
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

    public boolean dispatchTxRollback(TxRollbackBody body, final int channelId)
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

    public boolean dispatchTxSelect(TxSelectBody body, int channelId)
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

    public boolean dispatchBasicRecoverSync(final BasicRecoverSyncBody body, int channelId)
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

    public boolean dispatchQueueUnbind(final QueueUnbindBody body, int channelId)
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
