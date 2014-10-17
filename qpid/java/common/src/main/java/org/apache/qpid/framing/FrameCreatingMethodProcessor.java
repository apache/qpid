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

import java.util.ArrayList;
import java.util.List;

public class FrameCreatingMethodProcessor implements MethodProcessor<FrameCreatingMethodProcessor.ClientAndServerChannelMethodProcessor>,
                                                     ClientMethodProcessor<FrameCreatingMethodProcessor.ClientAndServerChannelMethodProcessor>,
                                                     ServerMethodProcessor<FrameCreatingMethodProcessor.ClientAndServerChannelMethodProcessor>
{
    private ProtocolVersion _protocolVersion;
    
    private final List<AMQDataBlock> _processedMethods = new ArrayList<>();
    private int _classId;
    private int _methodId;

    public FrameCreatingMethodProcessor(final ProtocolVersion protocolVersion)
    {
        _protocolVersion = protocolVersion;
    }

    public List<AMQDataBlock> getProcessedMethods()
    {
        return _processedMethods;
    }
    
    @Override
    public void receiveConnectionStart(final short versionMajor,
                                       final short versionMinor,
                                       final FieldTable serverProperties,
                                       final byte[] mechanisms,
                                       final byte[] locales)
    {
        _processedMethods.add(new AMQFrame(0, new ConnectionStartBody(versionMajor, versionMinor, serverProperties, mechanisms, locales)));
    }

    @Override
    public void receiveConnectionStartOk(final FieldTable clientProperties,
                                         final AMQShortString mechanism,
                                         final byte[] response,
                                         final AMQShortString locale)
    {
        _processedMethods.add(new AMQFrame(0, new ConnectionStartOkBody(clientProperties, mechanism, response, locale)));
    }

    @Override
    public void receiveConnectionSecure(final byte[] challenge)
    {
        _processedMethods.add(new AMQFrame(0, new ConnectionSecureBody(challenge)));
    }

    @Override
    public void receiveConnectionSecureOk(final byte[] response)
    {
        _processedMethods.add(new AMQFrame(0, new ConnectionSecureOkBody(response)));
    }

    @Override
    public void receiveConnectionTune(final int channelMax, final long frameMax, final int heartbeat)
    {
        _processedMethods.add(new AMQFrame(0, new ConnectionTuneBody(channelMax, frameMax, heartbeat)));
    }

    @Override
    public void receiveConnectionTuneOk(final int channelMax, final long frameMax, final int heartbeat)
    {
        _processedMethods.add(new AMQFrame(0, new ConnectionTuneOkBody(channelMax, frameMax, heartbeat)));
    }

    @Override
    public void receiveConnectionOpen(final AMQShortString virtualHost,
                                      final AMQShortString capabilities,
                                      final boolean insist)
    {
        _processedMethods.add(new AMQFrame(0, new ConnectionOpenBody(virtualHost, capabilities, insist)));
    }

    @Override
    public void receiveConnectionOpenOk(final AMQShortString knownHosts)
    {
        _processedMethods.add(new AMQFrame(0, new ConnectionOpenOkBody(knownHosts)));
    }

    @Override
    public void receiveConnectionRedirect(final AMQShortString host, final AMQShortString knownHosts)
    {
        _processedMethods.add(new AMQFrame(0, new ConnectionRedirectBody(getProtocolVersion(), host, knownHosts)));
    }

    @Override
    public void receiveConnectionClose(final int replyCode,
                                       final AMQShortString replyText,
                                       final int classId,
                                       final int methodId)
    {
        _processedMethods.add(new AMQFrame(0, new ConnectionCloseBody(getProtocolVersion(), replyCode, replyText, classId, methodId)));
    }

    @Override
    public void receiveConnectionCloseOk()
    {
        _processedMethods.add(new AMQFrame(0, ProtocolVersion.v8_0.equals(getProtocolVersion())
                ? ConnectionCloseOkBody.CONNECTION_CLOSE_OK_0_8
                : ConnectionCloseOkBody.CONNECTION_CLOSE_OK_0_9));
    }

    @Override
    public void receiveChannelOpen(final int channelId)
    {
        _processedMethods.add(new AMQFrame(channelId, new ChannelOpenBody()));
    }

    private void receiveExchangeBoundOk(final int channelId, final int replyCode, final AMQShortString replyText)
    {
        _processedMethods.add(new AMQFrame(channelId, new ExchangeBoundOkBody(replyCode, replyText)));
    }

    @Override
    public void receiveHeartbeat()
    {
        _processedMethods.add(new AMQFrame(0, new HeartbeatBody()));
    }

    @Override
    public ProtocolVersion getProtocolVersion()
    {
        return _protocolVersion;
    }

    @Override
    public ClientAndServerChannelMethodProcessor getChannelMethodProcessor(final int channelId)
    {
        return new FrameCreatingChannelMethodProcessor(channelId);
    }

    public void setProtocolVersion(final ProtocolVersion protocolVersion)
    {
        _protocolVersion = protocolVersion;
    }

    @Override
    public void receiveProtocolHeader(final ProtocolInitiation protocolInitiation)
    {
        _processedMethods.add(protocolInitiation);
    }

    @Override
    public void setCurrentMethod(final int classId, final int methodId)
    {
        _classId = classId;
        _methodId = methodId;
    }

    @Override
    public boolean ignoreAllButCloseOk()
    {
        return false;
    }

    public int getClassId()
    {
        return _classId;
    }

    public int getMethodId()
    {
        return _methodId;
    }

    public static interface ClientAndServerChannelMethodProcessor extends ServerChannelMethodProcessor, ClientChannelMethodProcessor
    {

    }

    private class FrameCreatingChannelMethodProcessor implements ClientAndServerChannelMethodProcessor
    {
        private final int _channelId;

        private FrameCreatingChannelMethodProcessor(final int channelId)
        {
            _channelId = channelId;
        }


        @Override
        public void receiveChannelOpenOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, ProtocolVersion.v8_0.equals(getProtocolVersion())
                    ? ChannelOpenOkBody.INSTANCE_0_8
                    : ChannelOpenOkBody.INSTANCE_0_9));
        }

        @Override
        public void receiveChannelAlert(final int replyCode, final AMQShortString replyText, final FieldTable details)
        {
            _processedMethods.add(new AMQFrame(_channelId, new ChannelAlertBody(replyCode, replyText, details)));
        }

        @Override
        public void receiveAccessRequestOk(final int ticket)
        {
            _processedMethods.add(new AMQFrame(_channelId, new AccessRequestOkBody(ticket)));
        }

        @Override
        public void receiveExchangeDeclareOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, new ExchangeDeclareOkBody()));
        }

        @Override
        public void receiveExchangeDeleteOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, new ExchangeDeleteOkBody()));
        }

        @Override
        public void receiveExchangeBoundOk(final int replyCode, final AMQShortString replyText)
        {
            FrameCreatingMethodProcessor.this.receiveExchangeBoundOk(_channelId, replyCode, replyText);
        }

        @Override
        public void receiveQueueBindOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, new QueueBindOkBody()));
        }

        @Override
        public void receiveQueueUnbindOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, new QueueUnbindOkBody()));
        }

        @Override
        public void receiveQueueDeclareOk(final AMQShortString queue, final long messageCount, final long consumerCount)
        {
            _processedMethods.add(new AMQFrame(_channelId, new QueueDeclareOkBody(queue, messageCount, consumerCount)));
        }

        @Override
        public void receiveQueuePurgeOk(final long messageCount)
        {
            _processedMethods.add(new AMQFrame(_channelId, new QueuePurgeOkBody(messageCount)));
        }

        @Override
        public void receiveQueueDeleteOk(final long messageCount)
        {
            _processedMethods.add(new AMQFrame(_channelId, new QueueDeleteOkBody(messageCount)));
        }

        @Override
        public void receiveBasicRecoverSyncOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicRecoverSyncOkBody(getProtocolVersion())));
        }

        @Override
        public void receiveBasicQosOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicQosOkBody()));
        }

        @Override
        public void receiveBasicConsumeOk(final AMQShortString consumerTag)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicConsumeOkBody(consumerTag)));
        }

        @Override
        public void receiveBasicCancelOk(final AMQShortString consumerTag)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicCancelOkBody(consumerTag)));
        }

        @Override
        public void receiveBasicReturn(final int replyCode,
                                       final AMQShortString replyText,
                                       final AMQShortString exchange,
                                       final AMQShortString routingKey)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicReturnBody(replyCode,
                                                                               replyText,
                                                                               exchange,
                                                                               routingKey)));
        }

        @Override
        public void receiveBasicDeliver(final AMQShortString consumerTag,
                                        final long deliveryTag,
                                        final boolean redelivered,
                                        final AMQShortString exchange,
                                        final AMQShortString routingKey)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicDeliverBody(consumerTag,
                                                                                deliveryTag,
                                                                                redelivered,
                                                                                exchange,
                                                                                routingKey)));
        }

        @Override
        public void receiveBasicGetOk(final long deliveryTag,
                                      final boolean redelivered,
                                      final AMQShortString exchange,
                                      final AMQShortString routingKey,
                                      final long messageCount)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicGetOkBody(deliveryTag,
                                                                              redelivered,
                                                                              exchange,
                                                                              routingKey,
                                                                              messageCount)));
        }

        @Override
        public void receiveBasicGetEmpty()
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicGetEmptyBody((AMQShortString)null)));
        }

        @Override
        public void receiveTxSelectOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, TxSelectOkBody.INSTANCE));
        }

        @Override
        public void receiveTxCommitOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, TxCommitOkBody.INSTANCE));
        }

        @Override
        public void receiveTxRollbackOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, TxRollbackOkBody.INSTANCE));
        }

        @Override
        public void receiveConfirmSelectOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, ConfirmSelectOkBody.INSTANCE));
        }

        @Override
        public void receiveAccessRequest(final AMQShortString realm,
                                         final boolean exclusive,
                                         final boolean passive,
                                         final boolean active,
                                         final boolean write,
                                         final boolean read)
        {
            _processedMethods.add(new AMQFrame(_channelId, new AccessRequestBody(realm,
                                                                                 exclusive,
                                                                                 passive,
                                                                                 active,
                                                                                 write,
                                                                                 read)));
        }

        @Override
        public void receiveExchangeDeclare(final AMQShortString exchange,
                                           final AMQShortString type,
                                           final boolean passive,
                                           final boolean durable,
                                           final boolean autoDelete,
                                           final boolean internal,
                                           final boolean nowait,
                                           final FieldTable arguments)
        {
            _processedMethods.add(new AMQFrame(_channelId, new ExchangeDeclareBody(0,
                                                                                   exchange,
                                                                                   type,
                                                                                   passive,
                                                                                   durable,
                                                                                   autoDelete,
                                                                                   internal,
                                                                                   nowait,
                                                                                   arguments)));
        }

        @Override
        public void receiveExchangeDelete(final AMQShortString exchange, final boolean ifUnused, final boolean nowait)
        {
            _processedMethods.add(new AMQFrame(_channelId, new ExchangeDeleteBody(0, exchange, ifUnused, nowait)));
        }

        @Override
        public void receiveExchangeBound(final AMQShortString exchange,
                                         final AMQShortString routingKey,
                                         final AMQShortString queue)
        {
            _processedMethods.add(new AMQFrame(_channelId, new ExchangeBoundBody(exchange, routingKey, queue)));
        }

        @Override
        public void receiveQueueDeclare(final AMQShortString queue,
                                        final boolean passive,
                                        final boolean durable,
                                        final boolean exclusive,
                                        final boolean autoDelete,
                                        final boolean nowait,
                                        final FieldTable arguments)
        {
            _processedMethods.add(new AMQFrame(_channelId, new QueueDeclareBody(0,
                                                                                queue,
                                                                                passive,
                                                                                durable,
                                                                                exclusive,
                                                                                autoDelete,
                                                                                nowait,
                                                                                arguments)));
        }

        @Override
        public void receiveQueueBind(final AMQShortString queue,
                                     final AMQShortString exchange,
                                     final AMQShortString bindingKey,
                                     final boolean nowait,
                                     final FieldTable arguments)
        {
            _processedMethods.add(new AMQFrame(_channelId, new QueueBindBody(0,
                                                                             queue,
                                                                             exchange,
                                                                             bindingKey,
                                                                             nowait,
                                                                             arguments)));
        }

        @Override
        public void receiveQueuePurge(final AMQShortString queue, final boolean nowait)
        {
            _processedMethods.add(new AMQFrame(_channelId, new QueuePurgeBody(0, queue, nowait)));
        }

        @Override
        public void receiveQueueDelete(final AMQShortString queue,
                                       final boolean ifUnused,
                                       final boolean ifEmpty,
                                       final boolean nowait)
        {
            _processedMethods.add(new AMQFrame(_channelId, new QueueDeleteBody(0, queue, ifUnused, ifEmpty, nowait)));
        }

        @Override
        public void receiveQueueUnbind(final AMQShortString queue,
                                       final AMQShortString exchange,
                                       final AMQShortString bindingKey,
                                       final FieldTable arguments)
        {
            _processedMethods.add(new AMQFrame(_channelId, new QueueUnbindBody(0,
                                                                               queue,
                                                                               exchange,
                                                                               bindingKey,
                                                                               arguments)));
        }

        @Override
        public void receiveBasicRecover(final boolean requeue, final boolean sync)
        {
            if(ProtocolVersion.v8_0.equals(getProtocolVersion()) || !sync)
            {
                _processedMethods.add(new AMQFrame(_channelId, new BasicRecoverBody(requeue)));
            }
            else
            {
                _processedMethods.add(new AMQFrame(_channelId, new BasicRecoverSyncBody(getProtocolVersion(), requeue)));
            }
        }

        @Override
        public void receiveBasicQos(final long prefetchSize, final int prefetchCount, final boolean global)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicQosBody(prefetchSize, prefetchCount, global)));
        }

        @Override
        public void receiveBasicConsume(final AMQShortString queue,
                                        final AMQShortString consumerTag,
                                        final boolean noLocal,
                                        final boolean noAck,
                                        final boolean exclusive,
                                        final boolean nowait,
                                        final FieldTable arguments)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicConsumeBody(0,
                                                                                queue,
                                                                                consumerTag,
                                                                                noLocal,
                                                                                noAck,
                                                                                exclusive,
                                                                                nowait,
                                                                                arguments)));
        }

        @Override
        public void receiveBasicCancel(final AMQShortString consumerTag, final boolean noWait)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicCancelBody(consumerTag, noWait)));
        }

        @Override
        public void receiveBasicPublish(final AMQShortString exchange,
                                        final AMQShortString routingKey,
                                        final boolean mandatory,
                                        final boolean immediate)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicPublishBody(0,
                                                                                exchange,
                                                                                routingKey,
                                                                                mandatory,
                                                                                immediate)));
        }

        @Override
        public void receiveBasicGet(final AMQShortString queue, final boolean noAck)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicGetBody(0, queue, noAck)));
        }

        @Override
        public void receiveBasicAck(final long deliveryTag, final boolean multiple)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicAckBody(deliveryTag, multiple)));
        }

        @Override
        public void receiveBasicReject(final long deliveryTag, final boolean requeue)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicRejectBody(deliveryTag, requeue)));
        }

        @Override
        public void receiveTxSelect()
        {
            _processedMethods.add(new AMQFrame(_channelId, TxSelectBody.INSTANCE));
        }

        @Override
        public void receiveTxCommit()
        {
            _processedMethods.add(new AMQFrame(_channelId, TxCommitBody.INSTANCE));
        }

        @Override
        public void receiveTxRollback()
        {
            _processedMethods.add(new AMQFrame(_channelId, TxRollbackBody.INSTANCE));
        }

        @Override
        public void receiveConfirmSelect(final boolean nowait)
        {
            _processedMethods.add(new AMQFrame(_channelId, new ConfirmSelectBody(nowait)));
        }

        @Override
        public void receiveChannelFlow(final boolean active)
        {
            _processedMethods.add(new AMQFrame(_channelId, new ChannelFlowBody(active)));
        }

        @Override
        public void receiveChannelFlowOk(final boolean active)
        {
            _processedMethods.add(new AMQFrame(_channelId, new ChannelFlowOkBody(active)));
        }

        @Override
        public void receiveChannelClose(final int replyCode,
                                        final AMQShortString replyText,
                                        final int classId,
                                        final int methodId)
        {
            _processedMethods.add(new AMQFrame(_channelId, new ChannelCloseBody(replyCode, replyText, classId, methodId)));
        }

        @Override
        public void receiveChannelCloseOk()
        {
            _processedMethods.add(new AMQFrame(_channelId, ChannelCloseOkBody.INSTANCE));
        }

        @Override
        public void receiveMessageContent(final byte[] data)
        {
            _processedMethods.add(new AMQFrame(_channelId, new ContentBody(data)));
        }

        @Override
        public void receiveMessageHeader(final BasicContentHeaderProperties properties, final long bodySize)
        {
            _processedMethods.add(new AMQFrame(_channelId, new ContentHeaderBody(properties, bodySize)));
        }

        @Override
        public boolean ignoreAllButCloseOk()
        {
            return false;
        }

        @Override
        public void receiveBasicNack(final long deliveryTag, final boolean multiple, final boolean requeue)
        {
            _processedMethods.add(new AMQFrame(_channelId, new BasicNackBody(deliveryTag, multiple, requeue)));
        }
    }
}
