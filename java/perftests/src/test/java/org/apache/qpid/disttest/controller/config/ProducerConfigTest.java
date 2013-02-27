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

import javax.jms.DeliveryMode;
import javax.jms.Message;

import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.test.utils.QpidTestCase;

public class ProducerConfigTest extends QpidTestCase
{
    public void testProducerHasZeroArgConstructorForGson()
    {
        ProducerConfig p = new ProducerConfig();
        assertNotNull(p);
    }

    public void testConfigProvidesJmsDefaults()
    {
        CreateProducerCommand p = new ProducerConfig().createCommand("session1");
        assertEquals(Message.DEFAULT_DELIVERY_MODE, p.getDeliveryMode());
        assertEquals(Message.DEFAULT_PRIORITY, p.getPriority());
        assertEquals(Message.DEFAULT_TIME_TO_LIVE, p.getTimeToLive());
    }

    public void testCreateProducerCommand()
    {
        String destination = "url:/destination";
        int messageSize = 1000;
        int numberOfMessages = 10;
        int priority = 4;
        long timeToLive = 10000;
        int batchSize = 5;
        long interval = 60;
        long maximumDuration = 70;
        long startDelay = 80;
        String providerName = "testProvider1";

        ProducerConfig producerConfig = new ProducerConfig(
                "producer1",
                destination,
                numberOfMessages,
                batchSize,
                maximumDuration,
                DeliveryMode.NON_PERSISTENT,
                messageSize,
                priority,
                timeToLive,
                interval,
                startDelay,
                providerName);

        CreateProducerCommand command = producerConfig.createCommand("session1");

        assertEquals("session1", command.getSessionName());
        assertEquals("producer1", command.getParticipantName());
        assertEquals(destination, command.getDestinationName());
        assertEquals(numberOfMessages, command.getNumberOfMessages());
        assertEquals(batchSize, command.getBatchSize());
        assertEquals(maximumDuration, command.getMaximumDuration());

        assertEquals(DeliveryMode.NON_PERSISTENT, command.getDeliveryMode());
        assertEquals(messageSize, command.getMessageSize());
        assertEquals(priority, command.getPriority());
        assertEquals(timeToLive, command.getTimeToLive());
        assertEquals(interval, command.getInterval());
        assertEquals(startDelay, command.getStartDelay());
        assertEquals(providerName, command.getMessageProviderName());
    }
}
