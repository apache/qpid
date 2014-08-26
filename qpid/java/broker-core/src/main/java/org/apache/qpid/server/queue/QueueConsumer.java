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
package org.apache.qpid.server.queue;

import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.model.Consumer;

public interface QueueConsumer<X extends QueueConsumer<X>> extends ConsumerImpl, Consumer<X>
{

    void flushBatched();

    void queueEmpty();

    boolean hasInterest(QueueEntry node);

    boolean wouldSuspend(QueueEntry entry);

    void restoreCredit(QueueEntry entry);

    void send(QueueEntry entry, boolean batch);

    void acquisitionRemoved(QueueEntry node);

    void queueDeleted();

    SubFlushRunner getRunner();

    AMQQueue getQueue();

    boolean resend(QueueEntry e);

    MessageInstance.ConsumerAcquiredState<X> getOwningState();

    QueueContext getQueueContext();

    ConsumerTarget getTarget();
}
