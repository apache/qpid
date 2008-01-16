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
        
        public void TestDurableSubscriptionNoAck() 
        {
            TestDurableSubscription(AcknowledgeMode.NoAcknowledge);
        }

        public void TestDurableSubscriptionAutoAck()
        {
            TestDurableSubscription(AcknowledgeMode.AutoAcknowledge);
        }

        private void TestDurableSubscription(AcknowledgeMode ackMode)
        {
            // Create a topic with one producer and two consumers.
            IChannel channel0 = _connection.CreateChannel(false, AcknowledgeMode.AutoAcknowledge, 1);
            IMessagePublisher publisher = channel0.CreatePublisherBuilder()
                .WithExchangeName(ExchangeNameDefaults.TOPIC)
                .WithRoutingKey(TEST_ROUTING_KEY)
                .Create();

            IChannel channel1 = _connection.CreateChannel(false, AcknowledgeMode.AutoAcknowledge, 1);
            string topicQueueName1 = channel1.GenerateUniqueName();
            channel1.DeclareQueue(topicQueueName1, false, true, true);
            channel1.Bind(topicQueueName1, ExchangeNameDefaults.TOPIC, TEST_ROUTING_KEY);
            IMessageConsumer consumer1 = channel1.CreateConsumerBuilder(topicQueueName1)
                .Create();

            IChannel channel2 = _connection.CreateChannel(false, AcknowledgeMode.AutoAcknowledge, 1);
            string topicQueueName2 = channel2.GenerateUniqueName();
            channel2.DeclareQueue(topicQueueName2, false, true, true);
            channel2.Bind(topicQueueName2, ExchangeNameDefaults.TOPIC, TEST_ROUTING_KEY);
            IMessageConsumer consumer2 = channel2.CreateConsumerBuilder(topicQueueName2)
                .WithSubscriptionName("TestSubscription")
                .WithDurable(true)
                .Create();

            // Send messages and receive on both consumers.
            publisher.Send(channel0.CreateTextMessage("A"));

            ConsumeNMessagesOnly(1, "A", consumer1);
            ConsumeNMessagesOnly(1, "A", consumer2);

            // Detach one consumer.
            consumer2.Dispose();

            // Send message and receive on one consumer.
            publisher.Send(channel0.CreateTextMessage("B"));

            ConsumeNMessagesOnly(1, "B", consumer1);

            // Re-attach consumer, check that it gets the messages that it missed.
            IChannel channel3 = _connection.CreateChannel(false, AcknowledgeMode.AutoAcknowledge, 1);
            //string topicQueueName3 = channe3.GenerateUniqueName();
            //channe3.DeclareQueue(topicQueueName3, false, true, true);
            //channe3.Bind(topicQueueName3, ExchangeNameDefaults.TOPIC, TEST_ROUTING_KEY);
            IMessageConsumer consumer3 = channel3.CreateConsumerBuilder(topicQueueName2)
                .WithSubscriptionName("TestSubscription")
                .WithDurable(true)
                .Create();

            ConsumeNMessagesOnly(1, "B", consumer3);

            // Clean up any open consumers at the end of the test.
            consumer1.Dispose();
            consumer3.Dispose();
        }

        /// Consumes n messages, checking that the n+1th is not available within a timeout.
        private void ConsumeNMessagesOnly(int n, string body, IMessageConsumer consumer)
        {
            IMessage msg;

            // Try to receive n messages.
            for (int i = 0; i < n; i++)
            {
                msg = consumer.Receive(500);
                Assert.IsNotNull(msg, "Consumer did not receive message number " + i);
                Assert.AreEqual(body, ((ITextMessage)msg).Text, "Incorrect Message recevied on consumer1.");
            }

            // Check that one more than n cannot be received.
            msg = consumer.Receive(500);
            Assert.IsNull(msg, "Consumer got more messages than the number requested (" + n + ").");
        }
    }
}
