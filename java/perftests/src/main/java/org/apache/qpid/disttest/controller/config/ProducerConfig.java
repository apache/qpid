/*
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
package org.apache.qpid.disttest.controller.config;

import javax.jms.Message;

import org.apache.qpid.disttest.message.CreateProducerCommand;

public class ProducerConfig extends ParticipantConfig
{
    private int _deliveryMode;
    private int _messageSize;
    private int _priority;
    private long _timeToLive;
    private long _interval;
    private long _startDelay;
    private String _messageProviderName;

    // For Gson
    public ProducerConfig()
    {
        _deliveryMode = Message.DEFAULT_DELIVERY_MODE;
        _messageSize = 0;
        _priority = Message.DEFAULT_PRIORITY;
        _timeToLive = Message.DEFAULT_TIME_TO_LIVE;
        _interval = 0;
        _startDelay = 0;
        _messageProviderName = null;
    }

    public ProducerConfig(
            String producerName,
            String destinationName,
            long numberOfMessages,
            int batchSize,
            long maximumDuration,
            int deliveryMode,
            int messageSize,
            int priority,
            long timeToLive,
            long interval,
            long startDelay,
            String messageProviderName)
    {
        super(producerName, destinationName, numberOfMessages, batchSize, maximumDuration);

        _deliveryMode = deliveryMode;
        _messageSize = messageSize;
        _priority = priority;
        _timeToLive = timeToLive;
        _interval = interval;
        _startDelay = startDelay;
        _messageProviderName = messageProviderName;
    }

    public CreateProducerCommand createCommand(String sessionName)
    {
        CreateProducerCommand command = new CreateProducerCommand();

        setParticipantProperties(command);

        command.setSessionName(sessionName);
        command.setDeliveryMode(_deliveryMode);
        command.setMessageSize(_messageSize);
        command.setPriority(_priority);
        command.setTimeToLive(_timeToLive);
        command.setInterval(_interval);
        command.setStartDelay(_startDelay);
        command.setMessageProviderName(_messageProviderName);

        return command;
    }
}
