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
package org.apache.qpid.framing;

public class FrameCreatingMethodProcessor implements MethodProcessor<AMQFrame>
{
    private final MethodRegistry _methodRegistry;

    FrameCreatingMethodProcessor(final MethodRegistry methodRegistry)
    {
        _methodRegistry = methodRegistry;
    }

    @Override
    public AMQFrame connectionStart(final short versionMajor,
                                         final short versionMinor,
                                         final FieldTable serverProperties,
                                         final byte[] mechanisms,
                                         final byte[] locales)
    {
        return new AMQFrame(0, new ConnectionStartBody(versionMajor, versionMinor, serverProperties, mechanisms, locales));
    }

    @Override
    public AMQFrame connectionStartOk(final FieldTable clientProperties,
                                           final AMQShortString mechanism,
                                           final byte[] response,
                                           final AMQShortString locale)
    {
        return new AMQFrame(0, new ConnectionStartOkBody(clientProperties, mechanism, response, locale));
    }

    @Override
    public AMQFrame txSelect(final int channelId)
    {
        return new AMQFrame(channelId, TxSelectBody.INSTANCE);
    }

    @Override
    public AMQFrame txSelectOk(final int channelId)
    {
        return new AMQFrame(channelId, TxSelectOkBody.INSTANCE);
    }

    @Override
    public AMQFrame txCommit(final int channelId)
    {
        return new AMQFrame(channelId, TxCommitBody.INSTANCE);
    }

    @Override
    public AMQFrame txCommitOk(final int channelId)
    {
        return new AMQFrame(channelId, TxCommitOkBody.INSTANCE);
    }

    @Override
    public AMQFrame txRollback(final int channelId)
    {
        return new AMQFrame(channelId, TxRollbackBody.INSTANCE);
    }

    @Override
    public AMQFrame txRollbackOk(final int channelId)
    {
        return new AMQFrame(channelId, TxRollbackOkBody.INSTANCE);
    }

    @Override
    public AMQFrame connectionSecure(final byte[] challenge)
    {
        return new AMQFrame(0, new ConnectionSecureBody(challenge));
    }

    @Override
    public AMQFrame connectionSecureOk(final byte[] response)
    {
        return new AMQFrame(0, new ConnectionSecureOkBody(response));
    }

    @Override
    public AMQFrame connectionTune(final int channelMax, final long frameMax, final int heartbeat)
    {
        return new AMQFrame(0, new ConnectionTuneBody(channelMax, frameMax, heartbeat));
    }

    @Override
    public AMQFrame connectionTuneOk(final int channelMax, final long frameMax, final int heartbeat)
    {
        return new AMQFrame(0, new ConnectionTuneOkBody(channelMax, frameMax, heartbeat));
    }

    @Override
    public AMQFrame connectionOpen(final AMQShortString virtualHost,
                                        final AMQShortString capabilities,
                                        final boolean insist)
    {
        return new AMQFrame(0, new ConnectionOpenBody(virtualHost, capabilities, insist));
    }

    @Override
    public AMQFrame connectionOpenOk(final AMQShortString knownHosts)
    {
        return new AMQFrame(0, new ConnectionOpenOkBody(knownHosts));
    }

    @Override
    public AMQFrame connectionRedirect(final AMQShortString host, final AMQShortString knownHosts)
    {
        return new AMQFrame(0, new ConnectionRedirectBody(getProtocolVersion(), host, knownHosts));
    }

    @Override
    public AMQFrame connectionClose(final int replyCode,
                                         final AMQShortString replyText,
                                         final int classId,
                                         final int methodId)
    {
        return new AMQFrame(0, new ConnectionCloseBody(getProtocolVersion(), replyCode, replyText, classId, methodId));
    }

    @Override
    public AMQFrame connectionCloseOk()
    {
        return new AMQFrame(0, ProtocolVersion.v8_0.equals(getProtocolVersion())
                ? ConnectionCloseOkBody.CONNECTION_CLOSE_OK_0_8
                : ConnectionCloseOkBody.CONNECTION_CLOSE_OK_0_9);
    }

    @Override
    public AMQFrame channelOpen(final int channelId)
    {
        return new AMQFrame(channelId, new ChannelOpenBody());
    }

    @Override
    public AMQFrame channelOpenOk(final int channelId)
    {
        return new AMQFrame(channelId, ProtocolVersion.v8_0.equals(getProtocolVersion())
                ? ChannelOpenOkBody.INSTANCE_0_8
                : ChannelOpenOkBody.INSTANCE_0_9);
    }

    @Override
    public AMQFrame channelFlow(final int channelId, final boolean active)
    {
        return new AMQFrame(channelId, new ChannelFlowBody(active));
    }

    @Override
    public AMQFrame channelFlowOk(final int channelId, final boolean active)
    {
        return new AMQFrame(channelId, new ChannelFlowOkBody(active));
    }

    @Override
    public AMQFrame channelAlert(final int channelId,
                                      final int replyCode,
                                      final AMQShortString replyText,
                                      final FieldTable details)
    {
        return new AMQFrame(channelId, new ChannelAlertBody(replyCode, replyText, details));
    }

    @Override
    public AMQFrame channelClose(final int channelId,
                                      final int replyCode,
                                      final AMQShortString replyText,
                                      final int classId,
                                      final int methodId)
    {
        return new AMQFrame(channelId, new ChannelCloseBody(replyCode, replyText, classId, methodId));
    }

    @Override
    public AMQFrame channelCloseOk(final int channelId)
    {
        return new AMQFrame(channelId, ChannelCloseOkBody.INSTANCE);
    }

    @Override
    public AMQFrame accessRequest(final int channelId,
                                       final AMQShortString realm,
                                       final boolean exclusive,
                                       final boolean passive,
                                       final boolean active,
                                       final boolean write,
                                       final boolean read)
    {
        return new AMQFrame(channelId, new AccessRequestBody(realm, exclusive, passive, active, write, read));
    }

    @Override
    public AMQFrame accessRequestOk(final int channelId, final int ticket)
    {
        return new AMQFrame(channelId, new AccessRequestOkBody(ticket));
    }

    @Override
    public AMQFrame exchangeDeclare(final int channelId,
                                         final AMQShortString exchange,
                                         final AMQShortString type,
                                         final boolean passive,
                                         final boolean durable,
                                         final boolean autoDelete,
                                         final boolean internal,
                                         final boolean nowait, final FieldTable arguments)
    {
        return new AMQFrame(channelId, new ExchangeDeclareBody(0, exchange, type, passive, durable, autoDelete, internal, nowait, arguments));
    }

    @Override
    public AMQFrame exchangeDeclareOk(final int channelId)
    {
        return new AMQFrame(channelId, new ExchangeDeclareOkBody());
    }

    @Override
    public AMQFrame exchangeDelete(final int channelId,
                                        final AMQShortString exchange,
                                        final boolean ifUnused,
                                        final boolean nowait)
    {
        return new AMQFrame(channelId, new ExchangeDeleteBody(0, exchange, ifUnused, nowait));
    }

    @Override
    public AMQFrame exchangeDeleteOk(final int channelId)
    {
        return new AMQFrame(channelId, new ExchangeDeleteOkBody());
    }

    @Override
    public AMQFrame exchangeBound(final int channelId,
                                       final AMQShortString exchange,
                                       final AMQShortString routingKey,
                                       final AMQShortString queue)
    {
        return new AMQFrame(channelId, new ExchangeBoundBody(exchange, routingKey, queue));
    }

    @Override
    public AMQFrame exchangeBoundOk(final int channelId, final int replyCode, final AMQShortString replyText)
    {
        return new AMQFrame(channelId, new ExchangeBoundOkBody(replyCode, replyText));
    }

    @Override
    public AMQFrame queueBindOk(final int channelId)
    {
        return new AMQFrame(channelId, new QueueBindOkBody());
    }

    @Override
    public AMQFrame queueUnbindOk(final int channelId)
    {
        return new AMQFrame(channelId, new QueueUnbindOkBody());
    }

    @Override
    public AMQFrame queueDeclare(final int channelId,
                                      final AMQShortString queue,
                                      final boolean passive,
                                      final boolean durable,
                                      final boolean exclusive,
                                      final boolean autoDelete,
                                      final boolean nowait,
                                      final FieldTable arguments)
    {
        return new AMQFrame(channelId, new QueueDeclareBody(0, queue, passive, durable, exclusive, autoDelete, nowait, arguments));
    }

    @Override
    public AMQFrame queueDeclareOk(final int channelId,
                                        final AMQShortString queue,
                                        final long messageCount,
                                        final long consumerCount)
    {
        return new AMQFrame(channelId, new QueueDeclareOkBody(queue, messageCount, consumerCount));
    }

    @Override
    public AMQFrame queueBind(final int channelId,
                                   final AMQShortString queue,
                                   final AMQShortString exchange,
                                   final AMQShortString bindingKey,
                                   final boolean nowait,
                                   final FieldTable arguments)
    {
        return new AMQFrame(channelId, new QueueBindBody(0, queue, exchange, bindingKey, nowait, arguments));
    }

    @Override
    public AMQFrame queuePurge(final int channelId, final AMQShortString queue, final boolean nowait)
    {
        return new AMQFrame(channelId, new QueuePurgeBody(0, queue, nowait));
    }

    @Override
    public AMQFrame queuePurgeOk(final int channelId, final long messageCount)
    {
        return new AMQFrame(channelId, new QueuePurgeOkBody(messageCount));
    }

    @Override
    public AMQFrame queueDelete(final int channelId,
                                     final AMQShortString queue,
                                     final boolean ifUnused,
                                     final boolean ifEmpty,
                                     final boolean nowait)
    {
        return new AMQFrame(channelId, new QueueDeleteBody(0, queue, ifUnused, ifEmpty, nowait));
    }

    @Override
    public AMQFrame queueDeleteOk(final int channelId, final long messageCount)
    {
        return new AMQFrame(channelId, new QueueDeleteOkBody(messageCount));
    }

    @Override
    public AMQFrame queueUnbind(final int channelId,
                                     final AMQShortString queue,
                                     final AMQShortString exchange,
                                     final AMQShortString bindingKey,
                                     final FieldTable arguments)
    {
        return new AMQFrame(channelId, new QueueUnbindBody(0, queue, exchange, bindingKey, arguments));
    }

    @Override
    public AMQFrame basicRecoverSyncOk(final int channelId)
    {
        return new AMQFrame(channelId, new BasicRecoverSyncOkBody(getProtocolVersion()));
    }

    @Override
    public AMQFrame basicRecover(final int channelId, final boolean requeue, final boolean sync)
    {
        if(ProtocolVersion.v8_0.equals(getProtocolVersion()) || !sync)
        {
            return new AMQFrame(channelId, new BasicRecoverBody(requeue));
        }
        else
        {
            return new AMQFrame(channelId, new BasicRecoverSyncBody(getProtocolVersion(), requeue));
        }
    }

    @Override
    public AMQFrame basicQos(final int channelId,
                                  final long prefetchSize,
                                  final int prefetchCount,
                                  final boolean global)
    {
        return new AMQFrame(channelId, new BasicQosBody(prefetchSize, prefetchCount, global));
    }

    @Override
    public AMQFrame basicQosOk(final int channelId)
    {
        return new AMQFrame(channelId, new BasicQosOkBody());
    }

    @Override
    public AMQFrame basicConsume(final int channelId,
                                      final AMQShortString queue,
                                      final AMQShortString consumerTag,
                                      final boolean noLocal,
                                      final boolean noAck,
                                      final boolean exclusive,
                                      final boolean nowait,
                                      final FieldTable arguments)
    {
        return new AMQFrame(channelId, new BasicConsumeBody(0, queue, consumerTag, noLocal, noAck, exclusive, nowait, arguments));
    }

    @Override
    public AMQFrame basicConsumeOk(final int channelId, final AMQShortString consumerTag)
    {
        return new AMQFrame(channelId, new BasicConsumeOkBody(consumerTag));
    }

    @Override
    public AMQFrame basicCancel(final int channelId, final AMQShortString consumerTag, final boolean noWait)
    {
        return new AMQFrame(channelId, new BasicCancelBody(consumerTag, noWait));
    }

    @Override
    public AMQFrame basicCancelOk(final int channelId, final AMQShortString consumerTag)
    {
        return new AMQFrame(channelId, new BasicCancelOkBody(consumerTag));
    }

    @Override
    public AMQFrame basicPublish(final int channelId,
                                      final AMQShortString exchange,
                                      final AMQShortString routingKey,
                                      final boolean mandatory,
                                      final boolean immediate)
    {
        return new AMQFrame(channelId, new BasicPublishBody(0, exchange, routingKey, mandatory, immediate));
    }

    @Override
    public AMQFrame basicReturn(final int channelId, final int replyCode,
                                final AMQShortString replyText,
                                final AMQShortString exchange,
                                final AMQShortString routingKey)
    {
        return new AMQFrame(channelId, new BasicReturnBody(replyCode, replyText, exchange, routingKey));
    }

    @Override
    public AMQFrame basicDeliver(final int channelId,
                                      final AMQShortString consumerTag,
                                      final long deliveryTag,
                                      final boolean redelivered,
                                      final AMQShortString exchange,
                                      final AMQShortString routingKey)
    {
        return new AMQFrame(channelId, new BasicDeliverBody(consumerTag, deliveryTag, redelivered, exchange, routingKey));
    }

    @Override
    public AMQFrame basicGet(final int channelId, final AMQShortString queue, final boolean noAck)
    {
        return new AMQFrame(channelId, new BasicGetBody(0, queue, noAck));
    }

    @Override
    public AMQFrame basicGetOk(final int channelId,
                                    final long deliveryTag,
                                    final boolean redelivered,
                                    final AMQShortString exchange,
                                    final AMQShortString routingKey,
                                    final long messageCount)
    {
        return new AMQFrame(channelId, new BasicGetOkBody(deliveryTag, redelivered, exchange, routingKey, messageCount));
    }

    @Override
    public AMQFrame basicGetEmpty(final int channelId)
    {
        return new AMQFrame(channelId, new BasicGetEmptyBody((AMQShortString)null));
    }

    @Override
    public AMQFrame basicAck(final int channelId, final long deliveryTag, final boolean multiple)
    {
        return new AMQFrame(channelId, new BasicAckBody(deliveryTag, multiple));
    }

    @Override
    public AMQFrame basicReject(final int channelId, final long deliveryTag, final boolean requeue)
    {
        return new AMQFrame(channelId, new BasicRejectBody(deliveryTag, requeue));
    }

    @Override
    public AMQFrame heartbeat()
    {
        return new AMQFrame(0, new HeartbeatBody());
    }

    private ProtocolVersion getProtocolVersion()
    {
        return _methodRegistry.getProtocolVersion();
    }

    @Override
    public AMQFrame messageContent(final int channelId, final byte[] data)
    {
        return new AMQFrame(channelId, new ContentBody(data));
    }

    @Override
    public AMQFrame messageHeader(final int channelId,
                                 final BasicContentHeaderProperties properties,
                                 final long bodySize)
    {
        return new AMQFrame(channelId, new ContentHeaderBody(properties, bodySize));
    }
}
