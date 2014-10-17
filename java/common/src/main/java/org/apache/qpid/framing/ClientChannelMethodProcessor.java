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

public interface ClientChannelMethodProcessor extends ChannelMethodProcessor
{
    void receiveChannelOpenOk();

    void receiveChannelAlert(int replyCode, final AMQShortString replyText, FieldTable details);

    void receiveAccessRequestOk(int ticket);

    void receiveExchangeDeclareOk();

    void receiveExchangeDeleteOk();

    void receiveExchangeBoundOk(int replyCode, AMQShortString replyText);

    void receiveQueueBindOk();

    void receiveQueueUnbindOk();

    void receiveQueueDeclareOk(final AMQShortString queue, long messageCount, long consumerCount);

    void receiveQueuePurgeOk(long messageCount);

    void receiveQueueDeleteOk(long messageCount);

    void receiveBasicRecoverSyncOk();

    void receiveBasicQosOk();

    void receiveBasicConsumeOk(AMQShortString consumerTag);

    void receiveBasicCancelOk(AMQShortString consumerTag);

    void receiveBasicReturn(int replyCode,
                            AMQShortString replyText,
                            AMQShortString exchange,
                            AMQShortString routingKey);

    void receiveBasicDeliver(AMQShortString consumerTag,
                             long deliveryTag,
                             boolean redelivered,
                             AMQShortString exchange, AMQShortString routingKey);

    void receiveBasicGetOk(long deliveryTag,
                           boolean redelivered,
                           AMQShortString exchange,
                           AMQShortString routingKey, long messageCount);

    void receiveBasicGetEmpty();

    void receiveTxSelectOk();

    void receiveTxCommitOk();

    void receiveTxRollbackOk();

    void receiveConfirmSelectOk();
}
