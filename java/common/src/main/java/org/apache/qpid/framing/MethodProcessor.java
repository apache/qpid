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

public interface MethodProcessor<T>
{
    T connectionStart(short versionMajor,
                      short versionMinor,
                      FieldTable serverProperties,
                      byte[] mechanisms,
                      byte[] locales);

    T connectionStartOk(FieldTable clientProperties,
                        AMQShortString mechanism,
                        byte[] response,
                        AMQShortString locale);

    T txSelect(int channelId);

    T txSelectOk(int channelId);

    T txCommit(int channelId);

    T txCommitOk(int channelId);

    T txRollback(int channelId);

    T txRollbackOk(int channelId);

    T connectionSecure(byte[] challenge);

    T connectionSecureOk(byte[] response);

    T connectionTune(int channelMax, long frameMax, int heartbeat);

    T connectionTuneOk(int channelMax, long frameMax, int heartbeat);

    T connectionOpen(AMQShortString virtualHost, AMQShortString capabilities, boolean insist);

    T connectionOpenOk(AMQShortString knownHosts);

    T connectionRedirect(AMQShortString host, AMQShortString knownHosts);

    T connectionClose(int replyCode, AMQShortString replyText, int classId, int methodId);

    T connectionCloseOk();

    T channelOpen(int channelId);

    T channelOpenOk(int channelId);

    T channelFlow(int channelId, boolean active);

    T channelFlowOk(int channelId, boolean active);

    T channelAlert(int channelId, int replyCode, final AMQShortString replyText, FieldTable details);

    T channelClose(int channelId, int replyCode, AMQShortString replyText, int classId, int methodId);

    T channelCloseOk(int channelId);

    T accessRequest(int channelId,
                    AMQShortString realm,
                    boolean exclusive,
                    boolean passive,
                    boolean active,
                    boolean write, boolean read);

    T accessRequestOk(int channelId, int ticket);

    T exchangeDeclare(int channelId,
                      AMQShortString exchange,
                      AMQShortString type,
                      boolean passive,
                      boolean durable,
                      boolean autoDelete, boolean internal, boolean nowait, final FieldTable arguments);

    T exchangeDeclareOk(int channelId);

    T exchangeDelete(int channelId, AMQShortString exchange, boolean ifUnused, boolean nowait);

    T exchangeDeleteOk(int channelId);

    T exchangeBound(int channelId, AMQShortString exchange, AMQShortString routingKey, AMQShortString queue);

    T exchangeBoundOk(int channelId, int replyCode, AMQShortString replyText);

    T queueBindOk(int channelId);

    T queueUnbindOk(final int channelId);

    T queueDeclare(int channelId,
                   AMQShortString queue,
                   boolean passive,
                   boolean durable,
                   boolean exclusive,
                   boolean autoDelete, boolean nowait, FieldTable arguments);

    T queueDeclareOk(int channelId, final AMQShortString queue, long messageCount, long consumerCount);

    T queueBind(int channelId,
                AMQShortString queue,
                AMQShortString exchange,
                AMQShortString bindingKey,
                boolean nowait, FieldTable arguments);

    T queuePurge(int channelId, AMQShortString queue, boolean nowait);

    T queuePurgeOk(int channelId, long messageCount);

    T queueDelete(int channelId, AMQShortString queue, boolean ifUnused, boolean ifEmpty, boolean nowait);

    T queueDeleteOk(int channelId, long messageCount);

    T queueUnbind(int channelId,
                  AMQShortString queue,
                  AMQShortString exchange,
                  AMQShortString bindingKey,
                  FieldTable arguments);

    T basicRecoverSyncOk(int channelId);

    T basicRecover(int channelId, final boolean requeue, boolean sync);

    T basicQos(int channelId, long prefetchSize, int prefetchCount, boolean global);

    T basicQosOk(int channelId);

    T basicConsume(int channelId,
                   AMQShortString queue,
                   AMQShortString consumerTag,
                   boolean noLocal,
                   boolean noAck,
                   boolean exclusive, boolean nowait, FieldTable arguments);

    T basicConsumeOk(int channelId, AMQShortString consumerTag);

    T basicCancel(int channelId, AMQShortString consumerTag, boolean noWait);

    T basicCancelOk(int channelId, AMQShortString consumerTag);

    T basicPublish(int channelId,
                   AMQShortString exchange,
                   AMQShortString routingKey,
                   boolean mandatory,
                   boolean immediate);

    T basicReturn(final int channelId,
                  int replyCode,
                  AMQShortString replyText,
                  AMQShortString exchange,
                  AMQShortString routingKey);

    T basicDeliver(int channelId,
                   AMQShortString consumerTag,
                   long deliveryTag,
                   boolean redelivered,
                   AMQShortString exchange, AMQShortString routingKey);

    T basicGet(int channelId, AMQShortString queue, boolean noAck);

    T basicGetOk(int channelId,
                 long deliveryTag,
                 boolean redelivered,
                 AMQShortString exchange,
                 AMQShortString routingKey, long messageCount);

    T basicGetEmpty(int channelId);

    T basicAck(int channelId, long deliveryTag, boolean multiple);

    T basicReject(int channelId, long deliveryTag, boolean requeue);

    T heartbeat();

    T messageContent(int channelId, byte[] data);

    T messageHeader(int channelId, BasicContentHeaderProperties properties, long bodySize);
}
