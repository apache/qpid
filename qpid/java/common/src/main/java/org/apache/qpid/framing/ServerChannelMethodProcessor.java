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

public interface ServerChannelMethodProcessor extends ChannelMethodProcessor
{
    void receiveAccessRequest(AMQShortString realm,
                              boolean exclusive,
                              boolean passive,
                              boolean active,
                              boolean write, boolean read);

    void receiveExchangeDeclare(AMQShortString exchange,
                                AMQShortString type,
                                boolean passive,
                                boolean durable,
                                boolean autoDelete, boolean internal, boolean nowait, final FieldTable arguments);

    void receiveExchangeDelete(AMQShortString exchange, boolean ifUnused, boolean nowait);

    void receiveExchangeBound(AMQShortString exchange, AMQShortString routingKey, AMQShortString queue);

    void receiveQueueDeclare(AMQShortString queue,
                             boolean passive,
                             boolean durable,
                             boolean exclusive,
                             boolean autoDelete, boolean nowait, FieldTable arguments);

    void receiveQueueBind(AMQShortString queue,
                          AMQShortString exchange,
                          AMQShortString bindingKey,
                          boolean nowait, FieldTable arguments);

    void receiveQueuePurge(AMQShortString queue, boolean nowait);

    void receiveQueueDelete(AMQShortString queue, boolean ifUnused, boolean ifEmpty, boolean nowait);

    void receiveQueueUnbind(AMQShortString queue,
                            AMQShortString exchange,
                            AMQShortString bindingKey,
                            FieldTable arguments);

    void receiveBasicRecover(final boolean requeue, boolean sync);

    void receiveBasicQos(long prefetchSize, int prefetchCount, boolean global);

    void receiveBasicConsume(AMQShortString queue,
                             AMQShortString consumerTag,
                             boolean noLocal,
                             boolean noAck,
                             boolean exclusive, boolean nowait, FieldTable arguments);

    void receiveBasicCancel(AMQShortString consumerTag, boolean noWait);

    void receiveBasicPublish(AMQShortString exchange,
                             AMQShortString routingKey,
                             boolean mandatory,
                             boolean immediate);

    void receiveBasicGet(AMQShortString queue, boolean noAck);

    void receiveBasicAck(long deliveryTag, boolean multiple);

    void receiveBasicReject(long deliveryTag, boolean requeue);



    void receiveTxSelect();

    void receiveTxCommit();

    void receiveTxRollback();

    void receiveConfirmSelect(boolean nowait);
}
