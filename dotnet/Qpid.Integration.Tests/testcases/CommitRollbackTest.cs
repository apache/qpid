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
using Apache.Qpid.Client;

namespace Apache.Qpid.Integration.Tests.testcases
{
    /// <summary>
    /// CommitRollbackTest
    ///
    /// <p><table id="crc"><caption>CRC Card</caption>
    /// <tr><th> Responsibilities <th> Collaborations
    /// <tr><td> Check that an uncommitted send cannot be received.
    /// <tr><td> Check that a committed send can be received.
    /// <tr><td> Check that a rolled back send cannot be received.
    /// <tr><td> Check that an uncommitted receive can be re-received.
    /// <tr><td> Check that a committed receive cannot be re-received.
    /// <tr><td> Check that a rolled back receive can be re-received.
    /// </table>
    /// </summary>
    [TestFixture, Category("Integration")]
    public class CommitRollbackTest : BaseMessagingTestFixture
    {
        /// <summary>Used for debugging purposes.</summary>
        private static ILog log = LogManager.GetLogger(typeof(CommitRollbackTest));

        /// <summary>Defines the name of the test topic to use with the tests.</summary>
        public const string TEST_ROUTING_KEY = "commitrollbacktestkey";

        /// <summary>A counter used to supply unique ids. </summary>
        int uniqueId = 0;

        /// <summary>Used to hold unique ids per test. </summary>
        int testId;

        [SetUp]
        public override void Init()
        {
            base.Init();

            // Set up a unqiue id for this test.
            testId = uniqueId++;

            // Create one producer and one consumer, p2p, tx, consumer with queue bound to producers routing key.
            SetUpEndPoint(0, true, false, TEST_ROUTING_KEY + testId);
            SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId);
        }

        [TearDown]
        public override void Shutdown()
        {
            try
            {
                // Clean up after the test.
                CloseEndPoint(0);
                CloseEndPoint(1);
            } 
            finally 
            {
                base.Shutdown();
            }
        }

        /// <summary> Check that an uncommitted send cannot be received. </summary>
        [Test]
        public void TestUncommittedSendNotReceived() 
        {
            // Send messages.
            testProducer[0].Send(testChannel[0].CreateTextMessage("A"));
          
            // Try to receive messages.
            ConsumeNMessagesOnly(0, "A", testConsumer[1]);
            testChannel[1].Commit();
        }
        
        /// <summary> Check that a committed send can be received. </summary>
        [Test]
        public void TestCommittedSendReceived() 
        {
            // Send messages.
            testProducer[0].Send(testChannel[0].CreateTextMessage("A"));
            testChannel[0].Commit();

            // Try to receive messages.
            ConsumeNMessagesOnly(1, "A", testConsumer[1]);
            testChannel[1].Commit();
        }

        /// <summary> Check that a rolled back send cannot be received. </summary>
        [Test]
        public void TestRolledBackSendNotReceived()
        {
            // Send messages.
            testProducer[0].Send(testChannel[0].CreateTextMessage("A"));
            testChannel[0].Rollback();

            // Try to receive messages.
            ConsumeNMessagesOnly(0, "A", testConsumer[1]);
            testChannel[1].Commit();
        }

        /// <summary> Check that an uncommitted receive can be re-received. </summary>
        [Test]
        public void TestUncommittedReceiveCanBeRereceived() 
        {
            // Send messages.
            testProducer[0].Send(testChannel[0].CreateTextMessage("A"));
            testChannel[0].Commit();

            // Try to receive messages.
            ConsumeNMessagesOnly(1, "A", testConsumer[1]);

            // Close end-point 1 without committing the message, then re-open to consume again.
            CloseEndPoint(1);
            SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId);

            // Try to receive messages.
            ConsumeNMessagesOnly(1, "A", testConsumer[1]);
        }

        /// <summary> Check that a committed receive cannot be re-received. </summary>
        [Test]
        public void TestCommittedReceiveNotRereceived() 
        {
            // Send messages.
            testProducer[0].Send(testChannel[0].CreateTextMessage("A"));
            testChannel[0].Commit();

            // Try to receive messages.
            ConsumeNMessagesOnly(1, "A", testConsumer[1]);
            testChannel[1].Commit();

            // Close end-point 1 without committing the message, then re-open to consume again.
            CloseEndPoint(1);
            SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId);

            // Try to receive messages.
            ConsumeNMessagesOnly(0, "A", testConsumer[1]);
        }

        /// <summary> Check that a rolled back receive can be re-received. </summary>
        [Test]
        public void TestRolledBackReceiveCanBeRereceived() 
        {
            // Send messages.
            testProducer[0].Send(testChannel[0].CreateTextMessage("A"));
            testChannel[0].Commit();

            // Try to receive messages.
            ConsumeNMessagesOnly(1, "A", testConsumer[1]);
            testChannel[1].Rollback();

            // Close end-point 1 without committing the message, then re-open to consume again.
            CloseEndPoint(1);
            SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId);

            // Try to receive messages.
            ConsumeNMessagesOnly(1, "A", testConsumer[1]);
        }
    }
}