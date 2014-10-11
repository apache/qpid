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

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;

public interface ChannelMethodProcessor
{
    void receiveAccessRequest(AMQShortString realm,
                              boolean exclusive,
                              boolean passive,
                              boolean active,
                              boolean write,
                              boolean read);

    void receiveBasicAck(long deliveryTag, boolean multiple);

    void receiveBasicCancel(AMQShortString consumerTag, boolean nowait);

    void receiveBasicConsume(AMQShortString queue,
                             AMQShortString consumerTag,
                             boolean noLocal,
                             boolean noAck,
                             boolean exclusive,
                             boolean nowait,
                             FieldTable arguments);

    void receiveBasicGet(AMQShortString queue, boolean noAck);

    void receiveBasicPublish(AMQShortString exchange,
                             AMQShortString routingKey,
                             boolean mandatory,
                             boolean immediate);

    void receiveBasicQos(long prefetchSize, int prefetchCount, boolean global);

    void receiveBasicRecover(boolean requeue, boolean sync);

    void receiveBasicReject(long deliveryTag, boolean requeue);

    void receiveChannelClose();

    void receiveChannelCloseOk();

    void receiveChannelFlow(boolean active);

    void receiveExchangeBound(AMQShortString exchange, AMQShortString queue, AMQShortString routingKey);

    void receiveExchangeDeclare(AMQShortString exchange,
                                AMQShortString type,
                                boolean passive,
                                boolean durable,
                                boolean autoDelete,
                                boolean internal,
                                boolean nowait,
                                FieldTable arguments);

    void receiveExchangeDelete(AMQShortString exchange, boolean ifUnused, boolean nowait);

    void receiveQueueBind(AMQShortString queue,
                          AMQShortString exchange,
                          AMQShortString routingKey,
                          boolean nowait,
                          FieldTable arguments);

    void receiveQueueDeclare(AMQShortString queueStr,
                             boolean passive,
                             boolean durable,
                             boolean exclusive,
                             boolean autoDelete,
                             boolean nowait,
                             FieldTable arguments);

    void receiveQueueDelete(AMQShortString queue, boolean ifUnused, boolean ifEmpty, boolean nowait);

    void receiveQueuePurge(AMQShortString queue, boolean nowait);

    void receiveQueueUnbind(AMQShortString queue,
                            AMQShortString exchange,
                            AMQShortString routingKey,
                            FieldTable arguments);

    void receiveTxSelect();

    void receiveTxCommit();

    void receiveTxRollback();


}
