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
	public class ByteMessageTest : BaseMessagingTestFixture
		
	{
		private static ILog log = LogManager.GetLogger(typeof(ByteMessageTest));
		private static string TEST_ROUTING_KEY = "BYTE_MESSAGE_TEST_QUEUE";
		
		[SetUp]
        public override void Init()
        {
            base.Init();

            // Create one producer and one consumer, p2p, tx, consumer with queue bound to producers routing key.
            SetUpEndPoint(0, true, false, TEST_ROUTING_KEY + testId, AcknowledgeMode.AutoAcknowledge, false, ExchangeNameDefaults.DIRECT, 
                          true, false, null);
            SetUpEndPoint(1, false, true, TEST_ROUTING_KEY + testId, AcknowledgeMode.AutoAcknowledge, false, ExchangeNameDefaults.DIRECT, 
                          true, false, null);
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

        /// <summary> Send the byte message and get it back. </summary>
        private void TestAnyByteMessage(int size)
        {
        	byte[] content = new byte[size];
        	
        	for (int i = 0; i < content.Length; i++)
        	{
        		content[i] = (byte)i;
        	}
            // Send messages.
            IBytesMessage msg = testChannel[0].CreateBytesMessage();
            msg.WriteBytes(content);
            testProducer[0].Send(msg);
          
            // Try to receive messages.
            ConsumeNMessagesOnly(1, content, testConsumer[1]);
        }
		
        [Test]
        /// <summary> Send a small byte message and get it back. </summary>
        public void TestSmallByteMessage()
        {
        	TestAnyByteMessage(4);
        }
        
        [Test]
        /// <summary> Send a byte message just under the frame boundry and get it back. </summary>
        public void TestByteMessageUnderFrameBoundry()
        {
        	TestAnyByteMessage((int) Math.Pow(2,16) - 32);
        }
        
        [Test]
        /// <summary> Send a byte message on the frame boundry and get it back. </summary>
        public void TestByteMessageOnFrameBoundry()
        {
        	TestAnyByteMessage((int) Math.Pow(2,16) - 2);
        }
        
        [Test]
        /// <summary> Send a byte message on the frame boundry and get it back. </summary>
        public void TestByteMessageOverFrameBoundry()
        {
        	TestAnyByteMessage((int) Math.Pow(2,16) - 1);
        }
        
        [Test]
        /// <summary> Send a byte message on the frame boundry and get it back. </summary>
        public void TestByteMessageWellOverFrameBoundry()
        {
        	TestAnyByteMessage((int) Math.Pow(2,17));
        }
        
	}
}
