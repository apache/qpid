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
package org.apache.qpid.server.consumer;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.protocol.AMQSessionModel;

public interface ConsumerImpl
{
    AtomicLong CONSUMER_NUMBER_GENERATOR = new AtomicLong(0);

    void externalStateChange();

    enum Option
    {
        ACQUIRES,
        SEES_REQUEUES,
        TRANSIENT,
        EXCLUSIVE,
        NO_LOCAL,
        DURABLE
    }

    long getBytesOut();

    long getMessagesOut();

    long getUnacknowledgedBytes();

    long getUnacknowledgedMessages();

    AMQSessionModel getSessionModel();

    MessageSource getMessageSource();

    long getConsumerNumber();

    boolean isSuspended();

    boolean isClosed();

    boolean acquires();

    boolean seesRequeues();

    void close();

    boolean trySendLock();


    void getSendLock();

    void releaseSendLock();

    boolean isActive();

    String getName();

    void flush();
}
