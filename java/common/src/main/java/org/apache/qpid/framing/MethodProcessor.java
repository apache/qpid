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

public interface MethodProcessor
{
    ProtocolVersion getProtocolVersion();

    void receiveConnectionStart(short versionMajor,
                                short versionMinor,
                                FieldTable serverProperties,
                                byte[] mechanisms,
                                byte[] locales);

    void receiveConnectionStartOk(FieldTable clientProperties,
                                  AMQShortString mechanism,
                                  byte[] response,
                                  AMQShortString locale);

    void receiveTxSelect(int channelId);

    void receiveTxSelectOk(int channelId);

    void receiveTxCommit(int channelId);

    void receiveTxCommitOk(int channelId);

    void receiveTxRollback(int channelId);

    void receiveTxRollbackOk(int channelId);

    void receiveConnectionSecure(byte[] challenge);

    void receiveConnectionSecureOk(byte[] response);

    void receiveConnectionTune(int channelMax, long frameMax, int heartbeat);

    void receiveConnectionTuneOk(int channelMax, long frameMax, int heartbeat);

    void receiveConnectionOpen(AMQShortString virtualHost, AMQShortString capabilities, boolean insist);

    void receiveConnectionOpenOk(AMQShortString knownHosts);

    void receiveConnectionRedirect(AMQShortString host, AMQShortString knownHosts);

    void receiveConnectionClose(int replyCode, AMQShortString replyText, int classId, int methodId);

    void receiveConnectionCloseOk();

    void receiveChannelOpen(int channelId);

    void receiveChannelOpenOk(int channelId);

    void receiveChannelFlow(int channelId, boolean active);

    void receiveChannelFlowOk(int channelId, boolean active);

    void receiveChannelAlert(int channelId, int replyCode, final AMQShortString replyText, FieldTable details);

    void receiveChannelClose(int channelId, int replyCode, AMQShortString replyText, int classId, int methodId);

    void receiveChannelCloseOk(int channelId);

    void receiveAccessRequest(int channelId,
                              AMQShortString realm,
                              boolean exclusive,
                              boolean passive,
                              boolean active,
                              boolean write, boolean read);

    void receiveAccessRequestOk(int channelId, int ticket);

    void receiveExchangeDeclare(int channelId,
                                AMQShortString exchange,
                                AMQShortString type,
                                boolean passive,
                                boolean durable,
                                boolean autoDelete, boolean internal, boolean nowait, final FieldTable arguments);

    void receiveExchangeDeclareOk(int channelId);

    void receiveExchangeDelete(int channelId, AMQShortString exchange, boolean ifUnused, boolean nowait);

    void receiveExchangeDeleteOk(int channelId);

    void receiveExchangeBound(int channelId, AMQShortString exchange, AMQShortString routingKey, AMQShortString queue);

    void receiveExchangeBoundOk(int channelId, int replyCode, AMQShortString replyText);

    void receiveQueueBindOk(int channelId);

    void receiveQueueUnbindOk(final int channelId);

    void receiveQueueDeclare(int channelId,
                             AMQShortString queue,
                             boolean passive,
                             boolean durable,
                             boolean exclusive,
                             boolean autoDelete, boolean nowait, FieldTable arguments);

    void receiveQueueDeclareOk(int channelId, final AMQShortString queue, long messageCount, long consumerCount);

    void receiveQueueBind(int channelId,
                          AMQShortString queue,
                          AMQShortString exchange,
                          AMQShortString bindingKey,
                          boolean nowait, FieldTable arguments);

    void receiveQueuePurge(int channelId, AMQShortString queue, boolean nowait);

    void receiveQueuePurgeOk(int channelId, long messageCount);

    void receiveQueueDelete(int channelId, AMQShortString queue, boolean ifUnused, boolean ifEmpty, boolean nowait);

    void receiveQueueDeleteOk(int channelId, long messageCount);

    void receiveQueueUnbind(int channelId,
                            AMQShortString queue,
                            AMQShortString exchange,
                            AMQShortString bindingKey,
                            FieldTable arguments);

    void receiveBasicRecoverSyncOk(int channelId);

    void receiveBasicRecover(int channelId, final boolean requeue, boolean sync);

    void receiveBasicQos(int channelId, long prefetchSize, int prefetchCount, boolean global);

    void receiveBasicQosOk(int channelId);

    void receiveBasicConsume(int channelId,
                             AMQShortString queue,
                             AMQShortString consumerTag,
                             boolean noLocal,
                             boolean noAck,
                             boolean exclusive, boolean nowait, FieldTable arguments);

    void receiveBasicConsumeOk(int channelId, AMQShortString consumerTag);

    void receiveBasicCancel(int channelId, AMQShortString consumerTag, boolean noWait);

    void receiveBasicCancelOk(int channelId, AMQShortString consumerTag);

    void receiveBasicPublish(int channelId,
                             AMQShortString exchange,
                             AMQShortString routingKey,
                             boolean mandatory,
                             boolean immediate);

    void receiveBasicReturn(final int channelId,
                            int replyCode,
                            AMQShortString replyText,
                            AMQShortString exchange,
                            AMQShortString routingKey);

    void receiveBasicDeliver(int channelId,
                             AMQShortString consumerTag,
                             long deliveryTag,
                             boolean redelivered,
                             AMQShortString exchange, AMQShortString routingKey);

    void receiveBasicGet(int channelId, AMQShortString queue, boolean noAck);

    void receiveBasicGetOk(int channelId,
                           long deliveryTag,
                           boolean redelivered,
                           AMQShortString exchange,
                           AMQShortString routingKey, long messageCount);

    void receiveBasicGetEmpty(int channelId);

    void receiveBasicAck(int channelId, long deliveryTag, boolean multiple);

    void receiveBasicReject(int channelId, long deliveryTag, boolean requeue);

    void receiveHeartbeat();

    void receiveMessageContent(int channelId, byte[] data);

    void receiveMessageHeader(int channelId, BasicContentHeaderProperties properties, long bodySize);

    void receiveProtocolHeader(ProtocolInitiation protocolInitiation);
}
