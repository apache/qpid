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

import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;

public class ServerMethodProcessor implements MethodProcessor
{
    private static final Logger LOGGER = Logger.getLogger(ServerMethodProcessor.class);
    private int _classId;
    private int _methodId;


    private static interface ChannelAction
    {
        void onChannel(ChannelMethodProcessor channel);
    }

    private ProtocolVersion _protocolVersion;
    private ServerMethodDispatcherImpl _dispatcher;
    private AMQProtocolEngine _connection;

    public ServerMethodProcessor(final ProtocolVersion protocolVersion)
    {
        _protocolVersion = protocolVersion;
    }


    private void processChannelMethod(int channelId, final ChannelAction action)
    {
        final AMQChannel channel = _connection.getChannel(channelId);
        if (channel == null)
        {
            // TODO throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }
        else
        {
            Subject.doAs(channel.getSubject(), new PrivilegedAction<Void>()
            {
                @Override
                public Void run()
                {
                    action.onChannel(channel.getMethodProcessor());
                    return null;
                }
            });
        }

    }

    @Override
    public void receiveConnectionStart(final short versionMajor,
                                       final short versionMinor,
                                       final FieldTable serverProperties,
                                       final byte[] mechanisms,
                                       final byte[] locales)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0,
                                          new ConnectionStartBody(versionMajor,
                                                                  versionMinor,
                                                                  serverProperties,
                                                                  mechanisms,
                                                                  locales));
        }
        _connection.closeConnection(AMQConstant.COMMAND_INVALID, "Unexpected method received:  ConnectionStart", 0
                                   );

    }

    @Override
    public void receiveConnectionStartOk(final FieldTable clientProperties,
                                         final AMQShortString mechanism,
                                         final byte[] response,
                                         final AMQShortString locale)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0, new ConnectionStartOkBody(clientProperties, mechanism, response, locale));
        }

        Broker<?> broker = _connection.getBroker();

        SubjectCreator subjectCreator = _connection.getSubjectCreator();
        SaslServer ss = null;
        try
        {
            ss = subjectCreator.createSaslServer(String.valueOf(mechanism),
                                                 _connection.getLocalFQDN(),
                                                 _connection.getPeerPrincipal());

            if (ss == null)
            {
                _connection.closeConnection(AMQConstant.RESOURCE_ERROR,
                                                  "Unable to create SASL Server:" + mechanism, 0
                                           );
            }
            else
            {
                _connection.setSaslServer(ss);

                final SubjectAuthenticationResult authResult = subjectCreator.authenticate(ss, response);
                //save clientProperties
                _connection.setClientProperties(clientProperties);

                MethodRegistry methodRegistry = _connection.getMethodRegistry();

                switch (authResult.getStatus())
                {
                    case ERROR:
                        Exception cause = authResult.getCause();

                        LOGGER.info("Authentication failed:" + (cause == null ? "" : cause.getMessage()));

                        _connection.closeConnection(AMQConstant.NOT_ALLOWED,
                                                    AMQConstant.NOT_ALLOWED.getName().toString(), 0
                                                   );

                        disposeSaslServer();
                        break;

                    case SUCCESS:
                        if (LOGGER.isInfoEnabled())
                        {
                            LOGGER.info("Connected as: " + authResult.getSubject());
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
            disposeSaslServer();

            _connection.closeConnection(AMQConstant.RESOURCE_ERROR, "SASL error:  " + e.getMessage(), 0
                                       );
        }

    }

    @Override
    public void receiveTxSelect(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, TxSelectBody.INSTANCE);
        }

    }

    @Override
    public void receiveTxSelectOk(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, TxSelectOkBody.INSTANCE);
        }

    }

    @Override
    public void receiveTxCommit(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, TxCommitBody.INSTANCE);
        }

    }

    @Override
    public void receiveTxCommitOk(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, TxCommitOkBody.INSTANCE);
        }

    }

    @Override
    public void receiveTxRollback(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, TxRollbackBody.INSTANCE);
        }

    }

    @Override
    public void receiveTxRollbackOk(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, TxRollbackOkBody.INSTANCE);
        }

    }

    @Override
    public void receiveConnectionSecure(final byte[] challenge)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0, new ConnectionSecureBody(challenge));
        }

    }

    @Override
    public void receiveConnectionSecureOk(final byte[] response)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0, new ConnectionSecureOkBody(response));
        }

    }

    @Override
    public void receiveConnectionTune(final int channelMax, final long frameMax, final int heartbeat)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0, new ConnectionTuneBody(channelMax, frameMax, heartbeat));
        }

    }

    @Override
    public void receiveConnectionTuneOk(final int channelMax, final long frameMax, final int heartbeat)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0, new ConnectionTuneOkBody(channelMax, frameMax, heartbeat));
        }

    }

    @Override
    public void receiveConnectionOpen(final AMQShortString virtualHost,
                                      final AMQShortString capabilities,
                                      final boolean insist)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0, new ConnectionOpenBody(virtualHost, capabilities, insist));
        }

    }

    @Override
    public void receiveConnectionOpenOk(final AMQShortString knownHosts)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0, new ConnectionOpenOkBody(knownHosts));
        }

    }

    @Override
    public void receiveConnectionRedirect(final AMQShortString host, final AMQShortString knownHosts)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0, new ConnectionRedirectBody(getProtocolVersion(), host, knownHosts));
        }

    }

    @Override
    public void receiveConnectionClose(final int replyCode,
                                       final AMQShortString replyText,
                                       final int classId,
                                       final int methodId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0,
                                          new ConnectionCloseBody(getProtocolVersion(),
                                                                  replyCode,
                                                                  replyText,
                                                                  classId,
                                                                  methodId));
        }

    }

    @Override
    public void receiveConnectionCloseOk()
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0, ProtocolVersion.v8_0.equals(getProtocolVersion())
                    ? ConnectionCloseOkBody.CONNECTION_CLOSE_OK_0_8
                    : ConnectionCloseOkBody.CONNECTION_CLOSE_OK_0_9);
        }
    }

    @Override
    public void receiveChannelOpen(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ChannelOpenBody());
        }

    }

    @Override
    public void receiveChannelOpenOk(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, ProtocolVersion.v8_0.equals(getProtocolVersion())
                    ? ChannelOpenOkBody.INSTANCE_0_8
                    : ChannelOpenOkBody.INSTANCE_0_9);
        }
    }

    @Override
    public void receiveChannelFlow(final int channelId, final boolean active)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ChannelFlowBody(active));
        }

    }

    @Override
    public void receiveChannelFlowOk(final int channelId, final boolean active)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ChannelFlowOkBody(active));
        }

    }

    @Override
    public void receiveChannelAlert(final int channelId,
                                    final int replyCode,
                                    final AMQShortString replyText,
                                    final FieldTable details)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ChannelAlertBody(replyCode, replyText, details));
        }

    }

    @Override
    public void receiveChannelClose(final int channelId,
                                    final int replyCode,
                                    final AMQShortString replyText,
                                    final int classId,
                                    final int methodId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ChannelCloseBody(replyCode, replyText, classId, methodId));
        }

    }

    @Override
    public void receiveChannelCloseOk(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, ChannelCloseOkBody.INSTANCE);
        }

    }

    @Override
    public void receiveAccessRequest(final int channelId,
                                     final AMQShortString realm,
                                     final boolean exclusive,
                                     final boolean passive,
                                     final boolean active,
                                     final boolean write,
                                     final boolean read)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame =
                    new AMQFrame(channelId, new AccessRequestBody(realm, exclusive, passive, active, write, read));
        }

    }

    @Override
    public void receiveAccessRequestOk(final int channelId, final int ticket)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new AccessRequestOkBody(ticket));
        }

    }

    @Override
    public void receiveExchangeDeclare(final int channelId,
                                       final AMQShortString exchange,
                                       final AMQShortString type,
                                       final boolean passive,
                                       final boolean durable,
                                       final boolean autoDelete,
                                       final boolean internal,
                                       final boolean nowait, final FieldTable arguments)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId,
                                          new ExchangeDeclareBody(0,
                                                                  exchange,
                                                                  type,
                                                                  passive,
                                                                  durable,
                                                                  autoDelete,
                                                                  internal,
                                                                  nowait,
                                                                  arguments));
        }

    }

    @Override
    public void receiveExchangeDeclareOk(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ExchangeDeclareOkBody());
        }

    }

    @Override
    public void receiveExchangeDelete(final int channelId,
                                      final AMQShortString exchange,
                                      final boolean ifUnused,
                                      final boolean nowait)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ExchangeDeleteBody(0, exchange, ifUnused, nowait));
        }

    }

    @Override
    public void receiveExchangeDeleteOk(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ExchangeDeleteOkBody());
        }

    }

    @Override
    public void receiveExchangeBound(final int channelId,
                                     final AMQShortString exchange,
                                     final AMQShortString routingKey,
                                     final AMQShortString queue)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ExchangeBoundBody(exchange, routingKey, queue));
        }

    }

    @Override
    public void receiveExchangeBoundOk(final int channelId, final int replyCode, final AMQShortString replyText)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ExchangeBoundOkBody(replyCode, replyText));
        }

    }

    @Override
    public void receiveQueueBindOk(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new QueueBindOkBody());
        }

    }

    @Override
    public void receiveQueueUnbindOk(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new QueueUnbindOkBody());
        }

    }

    @Override
    public void receiveQueueDeclare(final int channelId,
                                    final AMQShortString queue,
                                    final boolean passive,
                                    final boolean durable,
                                    final boolean exclusive,
                                    final boolean autoDelete,
                                    final boolean nowait,
                                    final FieldTable arguments)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId,
                                          new QueueDeclareBody(0,
                                                               queue,
                                                               passive,
                                                               durable,
                                                               exclusive,
                                                               autoDelete,
                                                               nowait,
                                                               arguments));
        }

    }

    @Override
    public void receiveQueueDeclareOk(final int channelId,
                                      final AMQShortString queue,
                                      final long messageCount,
                                      final long consumerCount)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new QueueDeclareOkBody(queue, messageCount, consumerCount));
        }

    }

    @Override
    public void receiveQueueBind(final int channelId,
                                 final AMQShortString queue,
                                 final AMQShortString exchange,
                                 final AMQShortString bindingKey,
                                 final boolean nowait,
                                 final FieldTable arguments)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame =
                    new AMQFrame(channelId, new QueueBindBody(0, queue, exchange, bindingKey, nowait, arguments));
        }

    }

    @Override
    public void receiveQueuePurge(final int channelId, final AMQShortString queue, final boolean nowait)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new QueuePurgeBody(0, queue, nowait));
        }

    }

    @Override
    public void receiveQueuePurgeOk(final int channelId, final long messageCount)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new QueuePurgeOkBody(messageCount));
        }

    }

    @Override
    public void receiveQueueDelete(final int channelId,
                                   final AMQShortString queue,
                                   final boolean ifUnused,
                                   final boolean ifEmpty,
                                   final boolean nowait)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new QueueDeleteBody(0, queue, ifUnused, ifEmpty, nowait));
        }

    }

    @Override
    public void receiveQueueDeleteOk(final int channelId, final long messageCount)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new QueueDeleteOkBody(messageCount));
        }

    }

    @Override
    public void receiveQueueUnbind(final int channelId,
                                   final AMQShortString queue,
                                   final AMQShortString exchange,
                                   final AMQShortString bindingKey,
                                   final FieldTable arguments)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new QueueUnbindBody(0, queue, exchange, bindingKey, arguments));
        }

    }

    @Override
    public void receiveBasicRecoverSyncOk(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new BasicRecoverSyncOkBody(getProtocolVersion()));
        }

    }

    @Override
    public void receiveBasicRecover(final int channelId, final boolean requeue, final boolean sync)
    {
        if (ProtocolVersion.v8_0.equals(getProtocolVersion()) || !sync)
        {
            if (LOGGER.isDebugEnabled())
            {
                AMQFrame frame = new AMQFrame(channelId, new BasicRecoverBody(requeue));
            }

        }
        else
        {
            if (LOGGER.isDebugEnabled())
            {
                AMQFrame frame = new AMQFrame(channelId, new BasicRecoverSyncBody(getProtocolVersion(), requeue));
            }

        }
    }

    @Override
    public void receiveBasicQos(final int channelId,
                                final long prefetchSize,
                                final int prefetchCount,
                                final boolean global)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new BasicQosBody(prefetchSize, prefetchCount, global));
        }

    }

    @Override
    public void receiveBasicQosOk(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new BasicQosOkBody());
        }

    }

    @Override
    public void receiveBasicConsume(final int channelId,
                                    final AMQShortString queue,
                                    final AMQShortString consumerTag,
                                    final boolean noLocal,
                                    final boolean noAck,
                                    final boolean exclusive,
                                    final boolean nowait,
                                    final FieldTable arguments)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId,
                                          new BasicConsumeBody(0,
                                                               queue,
                                                               consumerTag,
                                                               noLocal,
                                                               noAck,
                                                               exclusive,
                                                               nowait,
                                                               arguments));
        }

    }

    @Override
    public void receiveBasicConsumeOk(final int channelId, final AMQShortString consumerTag)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new BasicConsumeOkBody(consumerTag));
        }

    }

    @Override
    public void receiveBasicCancel(final int channelId, final AMQShortString consumerTag, final boolean noWait)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new BasicCancelBody(consumerTag, noWait));
        }

    }

    @Override
    public void receiveBasicCancelOk(final int channelId, final AMQShortString consumerTag)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new BasicCancelOkBody(consumerTag));
        }

    }

    @Override
    public void receiveBasicPublish(final int channelId,
                                    final AMQShortString exchange,
                                    final AMQShortString routingKey,
                                    final boolean mandatory,
                                    final boolean immediate)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame =
                    new AMQFrame(channelId, new BasicPublishBody(0, exchange, routingKey, mandatory, immediate));
        }

    }

    @Override
    public void receiveBasicReturn(final int channelId, final int replyCode,
                                   final AMQShortString replyText,
                                   final AMQShortString exchange,
                                   final AMQShortString routingKey)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new BasicReturnBody(replyCode, replyText, exchange, routingKey));
        }

    }

    @Override
    public void receiveBasicDeliver(final int channelId,
                                    final AMQShortString consumerTag,
                                    final long deliveryTag,
                                    final boolean redelivered,
                                    final AMQShortString exchange,
                                    final AMQShortString routingKey)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId,
                                          new BasicDeliverBody(consumerTag,
                                                               deliveryTag,
                                                               redelivered,
                                                               exchange,
                                                               routingKey));
        }

    }

    @Override
    public void receiveBasicGet(final int channelId, final AMQShortString queue, final boolean noAck)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new BasicGetBody(0, queue, noAck));
        }

    }

    @Override
    public void receiveBasicGetOk(final int channelId,
                                  final long deliveryTag,
                                  final boolean redelivered,
                                  final AMQShortString exchange,
                                  final AMQShortString routingKey,
                                  final long messageCount)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId,
                                          new BasicGetOkBody(deliveryTag,
                                                             redelivered,
                                                             exchange,
                                                             routingKey,
                                                             messageCount));
        }

    }

    @Override
    public void receiveBasicGetEmpty(final int channelId)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new BasicGetEmptyBody((AMQShortString) null));
        }

    }

    @Override
    public void receiveBasicAck(final int channelId, final long deliveryTag, final boolean multiple)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new BasicAckBody(deliveryTag, multiple));
        }

    }

    @Override
    public void receiveBasicReject(final int channelId, final long deliveryTag, final boolean requeue)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new BasicRejectBody(deliveryTag, requeue));
        }

    }

    @Override
    public void receiveHeartbeat()
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(0, new HeartbeatBody());
        }

    }

    @Override
    public ProtocolVersion getProtocolVersion()
    {
        return _protocolVersion;
    }

    public void setProtocolVersion(final ProtocolVersion protocolVersion)
    {
        _protocolVersion = protocolVersion;
    }

    @Override
    public void receiveMessageContent(final int channelId, final byte[] data)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ContentBody(data));
        }

    }

    @Override
    public void receiveMessageHeader(final int channelId,
                                     final BasicContentHeaderProperties properties,
                                     final long bodySize)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQFrame frame = new AMQFrame(channelId, new ContentHeaderBody(properties, bodySize));
        }

    }

    @Override
    public void receiveProtocolHeader(final ProtocolInitiation protocolInitiation)
    {
        if (LOGGER.isDebugEnabled())
        {
            AMQDataBlock frame = protocolInitiation;
        }

    }

    @Override
    public void setCurrentMethod(final int classId, final int methodId)
    {
        _classId = classId;
        _methodId = methodId;
    }

    private void disposeSaslServer()
    {
        SaslServer ss = _connection.getSaslServer();
        if (ss != null)
        {
            _connection.setSaslServer(null);
            try
            {
                ss.dispose();
            }
            catch (SaslException e)
            {
                LOGGER.error("Error disposing of Sasl server: " + e);
            }
        }
    }
}
