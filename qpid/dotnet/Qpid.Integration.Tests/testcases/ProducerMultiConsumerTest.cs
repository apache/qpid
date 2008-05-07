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
using System.Text;
using System.Threading;
using log4net;
using NUnit.Framework;
using Apache.Qpid.Messaging;
using Apache.Qpid.Client.Qms;
using Apache.Qpid.Client;

namespace Apache.Qpid.Integration.Tests.testcases
{
    /// ProducerMultiConsumerTest provides some tests for one producer and multiple consumers.
    ///
    /// <p><table id="crc"><caption>CRC Card</caption>
    /// <tr><th> Responsibilities <th> Collaborations
    /// <tr><td> Check that all consumers on a topic each receive all message on it.
    /// <tr><td> Check that consumers on the same queue receive each message once accross all consumers.
    /// </table>
    /// </summary>
    [TestFixture, Category("Integration")]
    public class ProducerMultiConsumerTest : BaseMessagingTestFixture
    {
        private static readonly ILog _logger = LogManager.GetLogger(typeof(ProducerMultiConsumerTest));
        
        /// <summary>Base name for the routing key used for this test (made unique by adding in test id).</summary>
        private const string TEST_ROUTING_KEY = "ProducerMultiConsumerTest";

        /// <summary>The number of consumers to test.</summary>
        private const int CONSUMER_COUNT = 5;

        /// <summary>The number of test messages to send.</summary>
        private const int MESSAGE_COUNT = 10;

        /// <summary>Monitor used to signal succesfull receipt of all test messages.</summary>
        AutoResetEvent _finishedEvent;

        /// <summary>Used to count test messages received so far.</summary>
        private int _messageReceivedCount;

        /// <summary>Used to hold the expected number of messages to receive.</summary>
        private int expectedMessageCount;

        /// <summary>Flag used to indicate that all messages really were received, and that the test did not just time out. </summary>
        private bool allReceived;

        /// <summary> Creates one producing end-point and many consuming end-points connected on a topic. </summary>
        [SetUp]
        public override void Init()
        {
            base.Init();

            // Reset all test counts and flags.
            _messageReceivedCount = 0;
            allReceived = false;
            _finishedEvent = new AutoResetEvent(false);
        }

        /// <summary> Cleans up all test end-points. </summary>
        [TearDown]
        public override void Shutdown()
        {
            try
            {
                // Close all end points for producer and consumers. 
                // Producer is on 0, and consumers on 1 .. n, so loop is from 0 to n inclusive.
                for (int i = 0; i <= CONSUMER_COUNT; i++)
                {
                    CloseEndPoint(i);
                }
            } 
            finally 
            {
                base.Shutdown();
            }
        }

        /// <summary> Check that all consumers on a topic each receive all message on it. </summary>
        [Test]
        public void AllConsumerReceiveAllMessagesOnTopic()
        {
            // Create end-points for all the consumers in the test.
            for (int i = 1; i <= CONSUMER_COUNT; i++)
            {
                SetUpEndPoint(i, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.AutoAcknowledge, false, ExchangeNameDefaults.TOPIC,
                              true, false, null);
                testConsumer[i].OnMessage += new MessageReceivedDelegate(OnMessage);
            }

            // Create an end-point to publish to the test topic.
            SetUpEndPoint(0, true, false, TEST_ROUTING_KEY + testId, AcknowledgeMode.AutoAcknowledge, false, ExchangeNameDefaults.TOPIC,
                          true, false, null);

            expectedMessageCount = (MESSAGE_COUNT * CONSUMER_COUNT);

            for (int i = 0; i < MESSAGE_COUNT; i++)
            {
                testProducer[0].Send(testChannel[0].CreateTextMessage("A"));
            }

            _finishedEvent.WaitOne(new TimeSpan(0, 0, 0, 30), false);

            // Check that all messages really were received.
            Assert.IsTrue(allReceived, "All messages were not received, only got " + _messageReceivedCount + " but wanted " + expectedMessageCount);
        }

        /// <summary> Check that consumers on the same queue receive each message once accross all consumers. </summary>
        [Test]
        public void AllConsumerReceiveAllMessagesOnDirect()
        {
            // Create end-points for all the consumers in the test.
            for (int i = 1; i <= CONSUMER_COUNT; i++)
            {
                SetUpEndPoint(i, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.AutoAcknowledge, false, ExchangeNameDefaults.DIRECT,
                              true, false, null);
                testConsumer[i].OnMessage += new MessageReceivedDelegate(OnMessage);
            }

            // Create an end-point to publish to the test topic.
            SetUpEndPoint(0, true, false, TEST_ROUTING_KEY + testId, AcknowledgeMode.AutoAcknowledge, false, ExchangeNameDefaults.DIRECT,
                          true, false, null);

            expectedMessageCount = (MESSAGE_COUNT * CONSUMER_COUNT);

            for (int i = 0; i < MESSAGE_COUNT; i++)
            {
                testProducer[0].Send(testChannel[0].CreateTextMessage("A"));
            }

            _finishedEvent.WaitOne(new TimeSpan(0, 0, 0, 30), false);

            // Check that all messages really were received.
            Assert.IsTrue(allReceived, "All messages were not received, only got: " + _messageReceivedCount + " but wanted " + expectedMessageCount);
        }

        /// <summary> Atomically increments the message count on every message, and signals once all messages in the test are received. </summary>
        public void OnMessage(IMessage m)
        {
            int newCount = Interlocked.Increment(ref _messageReceivedCount);

            if (newCount >= expectedMessageCount)
            {
                allReceived = true;
                _finishedEvent.Set();
            }
        }        
    }
}
