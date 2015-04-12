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
package org.apache.qpid.server.message;

import java.nio.ByteBuffer;

import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TransactionLogResource;

public interface ServerMessage<T extends StorableMessageMetaData> extends EnqueueableMessage, MessageContentSource
{
    String getInitialRoutingAddress();

    AMQMessageHeader getMessageHeader();

    public StoredMessage<T> getStoredMessage();

    boolean isPersistent();

    long getSize();

    long getExpiration();

    MessageReference newReference();

    MessageReference newReference(TransactionLogResource object);

    boolean isReferenced(TransactionLogResource resource);

    boolean isReferenced();

    long getMessageNumber();

    long getArrivalTime();

    public int getContent(ByteBuffer buf, int offset);

    ByteBuffer getContent(int offset, int size);

    Object getConnectionReference();
}
