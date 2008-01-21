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
using System;
using System.Threading;
using log4net;
using NUnit.Framework;
using Apache.Qpid.Messaging;
using Apache.Qpid.Client.Qms;

namespace Apache.Qpid.Integration.Tests.testcases
{
    /// <summary>
    /// DurableSubscriptionTest checks that durable subscriptions work, by sending messages that can be picked up by
    /// a subscription that is currently off-line, and checking that the subscriber gets all of its messages when it
    /// does come on-line.
    ///
    /// <p><table id="crc"><caption>CRC Card</caption>
    /// <tr><th> Responsibilities <th> Collaborations
    /// <tr><td> 
    /// </table>
    /// </summary>
    [TestFixture, Category("Integration")]
    public class DurableSubscriptionTest : BaseMessagingTestFixture
    {
        /// <summary>Used for debugging purposes.</summary>
        private static ILog log = LogManager.GetLogger(typeof(DurableSubscriptionTest));

        /// <summary>Defines the name of the test topic to use with the tests.</summary>
        public const string TEST_ROUTING_KEY = "durablesubtestkey";

        [SetUp]
        public override void Init()
        {
            base.Init();

            _connection.Start();
        }

        [TearDown]
        public override void Shutdown()
        {
            try
            {
                _connection.Stop();
            } 
            finally 
            {
                base.Shutdown();
            }
        }

        [Test]        
        public void TestDurableSubscriptionNoAck() 
        {
            TestDurableSubscription(AcknowledgeMode.NoAcknowledge);
        }

        [Test]
        public void TestDurableSubscriptionAutoAck()
        {
            TestDurableSubscription(AcknowledgeMode.AutoAcknowledge);
        }

        private void TestDurableSubscription(AcknowledgeMode ackMode)
        {
            // Create a topic with one producer and two consumers.
            SetUpEndPoint(0, true, false, TEST_ROUTING_KEY + testId, ackMode, false, ExchangeNameDefaults.TOPIC, false, null);
            SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, ackMode, false, ExchangeNameDefaults.TOPIC, false, null);
            SetUpEndPoint(2, false, true, TEST_ROUTING_KEY + testId, ackMode, false, ExchangeNameDefaults.TOPIC,
                          true, "TestSubscription" + testId);

            // Send messages and receive on both consumers.
            testProducer[0].Send(testChannel[0].CreateTextMessage("A"));

            ConsumeNMessagesOnly(1, "A", testConsumer[1]);
            ConsumeNMessagesOnly(1, "A", testConsumer[2]);

            // Detach one consumer.
            CloseEndPoint(2);

            // Send message and receive on one consumer.
            testProducer[0].Send(testChannel[0].CreateTextMessage("B"));

            ConsumeNMessagesOnly(1, "B", testConsumer[1]);

            // Re-attach consumer, check that it gets the messages that it missed.
            SetUpEndPoint(3, false, true, TEST_ROUTING_KEY + testId, ackMode, false, ExchangeNameDefaults.TOPIC,
                          true, "TestSubscription" + testId);

            ConsumeNMessagesOnly(1, "B", testConsumer[3]);

            // Clean up any open consumers at the end of the test.
            CloseEndPoint(0);
            CloseEndPoint(1);
            CloseEndPoint(3);
        }
    }
}
