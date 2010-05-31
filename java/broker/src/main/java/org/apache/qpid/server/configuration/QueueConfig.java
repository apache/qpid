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

package org.apache.qpid.server.configuration;

import java.util.Map;

import org.apache.qpid.AMQException;


public interface QueueConfig extends ConfiguredObject<QueueConfigType, QueueConfig>
{
    VirtualHostConfig getVirtualHost();

    String getName();

    boolean isExclusive();

    boolean isAutoDelete();

    ExchangeConfig getAlternateExchange();

    Map<String, Object> getArguments();

    long getReceivedMessageCount();

    int getMessageCount();

    long getQueueDepth();

    int getConsumerCount();
    
    int getConsumerCountHigh();

    int getBindingCount();
    
    int getBindingCountHigh();

    ConfigStore getConfigStore();

    long getMessageDequeueCount();

    long getTotalEnqueueSize();

    long getTotalDequeueSize();
    
    long getByteTxnEnqueues();

    long getByteTxnDequeues();

    long getMsgTxnEnqueues();
    
    long getMsgTxnDequeues();

    long getPersistentByteEnqueues();

    long getPersistentByteDequeues();

    long getPersistentMsgEnqueues();

    long getPersistentMsgDequeues();
    
    long getUnackedMessageCount();
    
    long getUnackedMessageCountHigh();

    void purge(long request) throws AMQException;
}