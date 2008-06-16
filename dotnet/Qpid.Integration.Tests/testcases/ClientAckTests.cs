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
	/// Checks that byte messages can be produced and received properly.
	/// </summary>
	[TestFixture, Category("Integration")]
	public class ClientAckTests : BaseMessagingTestFixture
	{
		private static ILog log = LogManager.GetLogger(typeof(ClientAckTests));
		private static string TEST_ROUTING_KEY = "MESSAGE_ACK_TEST_QUEUE";
		private IMessage msgA;
		private IMessage msgB;
		private IMessage msgC;
		
		[SetUp]
        public override void Init()
        {
            base.Init();

            // Create one producer and one consumer, p2p, tx, consumer with queue bound to producers routing key.
            SetUpEndPoint(0, true, false, TEST_ROUTING_KEY + testId, AcknowledgeMode.ClientAcknowledge, false, ExchangeNameDefaults.DIRECT, 
                          true, false, null);
            SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.ClientAcknowledge, false, ExchangeNameDefaults.DIRECT, 
                          true, true, testId.ToString());
            // Send 3 messages and get them back
            SendMessages(3, testProducer[0]);
        	msgA = testConsumer[1].Receive();
        	msgB = testConsumer[1].Receive();
        	msgC = testConsumer[1].Receive();
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
        
        [Test]
        /// <summary> Send 3 messages, get them back and each one rolling acks up. </summary>
        public void TestAckingABCAll()
        {
        	msgA.Acknowledge();
        	msgB.Acknowledge();
        	msgC.Acknowledge();
        	
        	CloseEndPoint(1);
        	SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.ClientAcknowledge, false, ExchangeNameDefaults.DIRECT, 
                          true, true, testId.ToString());
        	ConsumeNMessagesOnly(0, "wibble", testConsumer[1]);
        }
        
        [Test]
        /// <summary> Send 3 messages, get them back and ack each one individually </summary>
        public void TestAckingABCIndividual()
        {
        	msgA.Acknowledge(false);
        	msgB.Acknowledge(false);
        	msgC.Acknowledge(false);

        	CloseEndPoint(1);
        	SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.ClientAcknowledge, false, ExchangeNameDefaults.DIRECT, 
                          true, true, testId.ToString());
        	ConsumeNMessagesOnly(0, "wibble", testConsumer[1]);
        }
        
        [Test]
        /// <summary> Send 3 messages, get them back and the middle one only rolling acks up. </summary>
        public void TestAckingBOnlyAll()
        {
        	msgB.Acknowledge();
        	
        	CloseEndPoint(1);
        	SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.ClientAcknowledge, false, ExchangeNameDefaults.DIRECT, 
                          true, true, testId.ToString());
        	log.Debug("Checking we get the last message back");
        	ConsumeNMessagesOnly(1, "Test message 2", testConsumer[1]);
        }

        [Test]
        /// <summary> Send 3 messages, get them back and ack the middle one only individually. </summary>
        public void TestAckingBOnlyIndividual()
        {
        	msgB.Acknowledge(false);
        	CloseEndPoint(1);
			SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.ClientAcknowledge, false, ExchangeNameDefaults.DIRECT, 
                          true, true, testId.ToString());        	
        	ConsumeNMessages(1, "Test message 0", testConsumer[1]);
        	ConsumeNMessagesOnly(1, "Test message 2", testConsumer[1]);
        }

        [Test]
        /// <summary> Send 3 messages, get them back and ack the last one, rolling acks up. </summary>
        public void TestAckingCOnlyAll()
        {
        	msgC.Acknowledge();
        	
        	CloseEndPoint(1);
			SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.ClientAcknowledge, false, ExchangeNameDefaults.DIRECT, 
                          true, true, testId.ToString());        	
        	ConsumeNMessagesOnly(0, "wibble", testConsumer[1]);
        }

        [Test]
        /// <summary> Send 3 messages, get them back and ack the last oneindivdually. </summary>
        public void TestAckingCOnlyIndividual()
        {
        	msgC.Acknowledge(false);
        	
        	CloseEndPoint(1);
			SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.ClientAcknowledge, false, ExchangeNameDefaults.DIRECT, 
                          true, true, testId.ToString());        	
        	ConsumeNMessages(1, "Test message 0", testConsumer[1]);
        	ConsumeNMessagesOnly(1, "Test message 1", testConsumer[1]);
        }

        [Test]
        /// <summary> Send 3 messages, get them back and the first two indivdually. </summary>
        public void TestAckingAtoBIndivdual()
        {
        	msgA.Acknowledge(false);
        	msgB.Acknowledge(false);
        	CloseEndPoint(1);
			SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.ClientAcknowledge, false, ExchangeNameDefaults.DIRECT, 
                          true, true, testId.ToString());   
        	ConsumeNMessagesOnly(1, "Test message 2", testConsumer[1]);
        }
        
        [Test]
        /// <summary> Send 3 messages, get them back and ack the first and last one indivdually. </summary>
        public void TestAckingAandCIndivdual()
        {
        	msgA.Acknowledge(false);
        	msgC.Acknowledge(false);
        	CloseEndPoint(1);
        	SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.ClientAcknowledge, false, ExchangeNameDefaults.DIRECT, 
        	              true, true, testId.ToString());
        	//((AmqChannel)testChannel[2]).Suspend(false);
        	ConsumeNMessagesOnly(1, "Test message 1", testConsumer[1]);
        }
	}
}
