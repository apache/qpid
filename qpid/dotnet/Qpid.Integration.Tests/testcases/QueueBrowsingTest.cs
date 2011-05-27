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
using Apache.Qpid.Framing;

namespace Apache.Qpid.Integration.Tests.testcases
{
    [TestFixture, Category("Integration")]
    public class QueueBrowsingTest : BaseMessagingTestFixture
    {
        /// <summary>Used for debugging purposes.</summary>
        private static ILog log = LogManager.GetLogger(typeof(QueueBrowsingTest));

        public const string TEST_ROUTING_KEY = "queuebrowsingkey";
        public const string TEST_ROUTING_KEY2 = "lvqbrowsingkey";


        [SetUp]
        public override void Init()
        {
            base.Init();
        }

        [TearDown]
        public override void Shutdown()
        {
            base.Shutdown();
        }

        [Test]
        public void TestQueueBrowsing()
        {
            // Create a topic with one producer and two consumers.
            SetUpEndPoint(0, true, false, TEST_ROUTING_KEY + testId, AcknowledgeMode.AutoAcknowledge, false, ExchangeNameDefaults.DIRECT, true, true, null, false, false);
            SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.NoAcknowledge, false, ExchangeNameDefaults.DIRECT, true, true, TEST_ROUTING_KEY + testId, false, true);
            SetUpEndPoint(2, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.NoAcknowledge, false, ExchangeNameDefaults.DIRECT, true, true, TEST_ROUTING_KEY + testId, false, true);

            Thread.Sleep(500);

            // Send messages and receive on both consumers.
            testProducer[0].Send(testChannel[0].CreateTextMessage("msg"));
            testProducer[0].Send(testChannel[0].CreateTextMessage("msg"));
            testProducer[0].Send(testChannel[0].CreateTextMessage("msg"));
            testProducer[0].Send(testChannel[0].CreateTextMessage("msg"));
            testProducer[0].Send(testChannel[0].CreateTextMessage("msg"));
            testProducer[0].Send(testChannel[0].CreateTextMessage("msg"));

            Thread.Sleep(2000);

            
            ConsumeNMessagesOnly(6, "msg", testConsumer[1]);
            ConsumeNMessagesOnly(6, "msg", testConsumer[2]);

            // Clean up any open consumers at the end of the test.
            CloseEndPoint(2);
            CloseEndPoint(1);
            CloseEndPoint(0);
        }

        [Test]
        public void TestQueueBrowsingLVQ()
        {
            // Create a topic with one producer and two consumers.
            SetUpEndPoint(0, true, false, TEST_ROUTING_KEY2 + testId, AcknowledgeMode.AutoAcknowledge, false, ExchangeNameDefaults.DIRECT, true, true, TEST_ROUTING_KEY2 + testId, false, false);
            FieldTable args = new FieldTable();
            args.SetBoolean("qpid.last_value_queue", true);
            args.SetString("qpid.last_value_queue_key", "key");
            testChannel[0].DeclareQueue(TEST_ROUTING_KEY2 + testId, true, false, false, args);
            testChannel[0].Bind(TEST_ROUTING_KEY2 + testId, ExchangeNameDefaults.DIRECT, TEST_ROUTING_KEY2 + testId);            
            Thread.Sleep(500);

            
            for (int i = 0; i < 12; i++)
            {
                ITextMessage msg = testChannel[0].CreateTextMessage("msg");
                msg.Headers.SetInt("key", i%6);
                testProducer[0].Send(msg);
            }

            Thread.Sleep(2000);

            SetUpEndPoint(1, false, true, TEST_ROUTING_KEY2 + testId, AcknowledgeMode.NoAcknowledge, false, ExchangeNameDefaults.DIRECT, true, true, TEST_ROUTING_KEY2 + testId, false, true);
            SetUpEndPoint(2, false, true, TEST_ROUTING_KEY2 + testId, AcknowledgeMode.NoAcknowledge, false, ExchangeNameDefaults.DIRECT, true, true, TEST_ROUTING_KEY2 + testId, false, true);

            Thread.Sleep(500);

            
            ConsumeNMessagesOnly(6, "msg", testConsumer[1]);
            ConsumeNMessagesOnly(6, "msg", testConsumer[2]);

            // Clean up any open consumers at the end of the test.
            CloseEndPoint(2);
            CloseEndPoint(1);
            CloseEndPoint(0);
        }

    }
}
