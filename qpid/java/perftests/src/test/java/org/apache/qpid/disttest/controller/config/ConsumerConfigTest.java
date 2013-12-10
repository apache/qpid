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

import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.test.utils.QpidTestCase;

public class ConsumerConfigTest extends QpidTestCase
{
    public void testConsumerHasZeroArgConstructorForGson()
    {
        ConsumerConfig c = new ConsumerConfig();
        assertNotNull(c);
    }

    public void testCreateConsumerCommand()
    {
        boolean isTopic = true;
        boolean isDurableSubscription = true;
        boolean isBrowsingSubscription = true;
        boolean noLocal = true;
        long numberOfMessages = 100;
        String consumerName = "consumerName";
        String sessionName = "sessionName";
        String destinationName = "destinationName";
        String selector = "selector";
        int batchSize = 10;;
        long maximumDuration = 50;
        boolean isSynchronousNonDefault = false;

        ConsumerConfig consumerConfig = new ConsumerConfig(
            consumerName,
            destinationName,
            numberOfMessages,
            batchSize,
            maximumDuration,
            isTopic,
            isDurableSubscription,
            isBrowsingSubscription,
            selector,
            noLocal,
            isSynchronousNonDefault);

        CreateConsumerCommand createConsumerCommand = consumerConfig.createCommand(sessionName);

        assertEquals(sessionName, createConsumerCommand.getSessionName());
        assertEquals(consumerName, createConsumerCommand.getParticipantName());
        assertEquals(destinationName, createConsumerCommand.getDestinationName());
        assertEquals(numberOfMessages, createConsumerCommand.getNumberOfMessages());
        assertEquals(batchSize, createConsumerCommand.getBatchSize());
        assertEquals(maximumDuration, createConsumerCommand.getMaximumDuration());

        assertEquals(isTopic, createConsumerCommand.isTopic());
        assertEquals(isDurableSubscription, createConsumerCommand.isDurableSubscription());
        assertEquals(isBrowsingSubscription, createConsumerCommand.isBrowsingSubscription());
        assertEquals(selector, createConsumerCommand.getSelector());
        assertEquals(noLocal, createConsumerCommand.isNoLocal());
        assertEquals(isSynchronousNonDefault, createConsumerCommand.isSynchronous());
    }

}
