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
package org.apache.qpid.disttest.controller.config;

import org.apache.qpid.disttest.message.CreateConsumerCommand;

public class ConsumerConfig extends ParticipantConfig
{
    private boolean _isDurableSubscription;
    private boolean _isBrowsingSubscription;
    private String _selector;
    private boolean _noLocal;
    private boolean _synchronous;
    private boolean _evaluateLatency;

    // For Gson
    public ConsumerConfig()
    {
        _isDurableSubscription = false;
        _isBrowsingSubscription = false;
        _selector = null;
        _noLocal = false;
        _synchronous = true;
    }

    public ConsumerConfig(
            String consumerName,
            String destinationName,
            long numberOfMessages,
            int batchSize,
            long maximumDuration,
            boolean isTopic,
            boolean isDurableSubscription,
            boolean isBrowsingSubscription,
            String selector,
            boolean noLocal,
            boolean synchronous)
    {
        super(consumerName, destinationName, isTopic, numberOfMessages, batchSize, maximumDuration);

        _isDurableSubscription = isDurableSubscription;
        _isBrowsingSubscription = isBrowsingSubscription;
        _selector = selector;
        _noLocal = noLocal;
        _synchronous = synchronous;
    }

    public CreateConsumerCommand createCommand(String sessionName)
    {
        CreateConsumerCommand createConsumerCommand = new CreateConsumerCommand();

        setParticipantProperties(createConsumerCommand);

        createConsumerCommand.setSessionName(sessionName);
        createConsumerCommand.setDurableSubscription(_isDurableSubscription);
        createConsumerCommand.setBrowsingSubscription(_isBrowsingSubscription);
        createConsumerCommand.setSelector(_selector);
        createConsumerCommand.setNoLocal(_noLocal);
        createConsumerCommand.setSynchronous(_synchronous);
        createConsumerCommand.setEvaluateLatency(_evaluateLatency);

        return createConsumerCommand;
    }

}
