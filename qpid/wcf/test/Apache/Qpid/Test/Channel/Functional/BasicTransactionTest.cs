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
*/

namespace Apache.Qpid.Test.Channel.Functional
{
    using System;
    using System.Collections.Generic;
    using System.Reflection;
    using System.ServiceModel;
    using System.Threading;
    using NUnit.Framework;

    [TestFixture]
    public class BasicTransactionTest
    {
        private const string SendToUri = "amqp:amq.direct?routingkey=routing_key";
        private const string ListenUri = "amqp:message_queue";

        private MessageClient client;

        [SetUp]
        public void Setup()
        {
            // Create client
            this.client = new MessageClient();
            this.client.NumberOfMessages = 3;
            this.client.NumberOfIterations = 1;

            // Setup service
            MessageService.EndpointAddress = ListenUri;
            MessageService.ContractTypes = new List<Type>();            
            MessageService.CompletionHandle = new EventWaitHandle(false, EventResetMode.AutoReset);
        }

        [TestCase(true)]
        [TestCase(false)]
        public void TransactionalSend(bool commitTransaction)
        {
            int expectedMessageCount = 0;
            this.client.TransactedSend = true;

            MessageService.ContractTypes.Add(typeof(IQueuedServiceUsingTSRAttribute));
            this.client.CommitTransaction = commitTransaction;

            if (commitTransaction)
            {
                expectedMessageCount = this.client.NumberOfIterations * this.client.NumberOfMessages * MessageService.ContractTypes.Count;
            }

            // Call Service methods.
            this.SendMessages(String.Empty);

            // Validate results.
            int actualMessageCount = Util.GetMessageCountFromQueue(ListenUri);
            Assert.AreEqual(expectedMessageCount, actualMessageCount, "The actual message count wasn't as expected.");
        }

        [TestCase("UseTransactionScope", true)]
        [TestCase("UseTransactionScope", false)]
        [TestCase("UseTSRAttribute", true)]
        [TestCase("UseTSRAttribute", false)]
        public void TransactionalReceive(string testVariation, bool commitTransaction)
        {
            bool testPassed = true;
            int expectedMessageCount = 0;
            this.client.TransactedSend = false;
            string transactionOutcome = "Commit";

            switch (testVariation.Trim().ToLower())
            {
                case "usetransactionscope":
                    {
                        MessageService.ContractTypes.Add(typeof(IQueuedServiceUsingTransactionScope));
                    }

                    break;
                case "usetsrattribute":
                    {
                        MessageService.ContractTypes.Add(typeof(IQueuedServiceUsingTSRAttribute));
                    }

                    break;
            }

            int expectedMethodCallCount = this.client.NumberOfIterations * this.client.NumberOfMessages * MessageService.ContractTypes.Count;

            if (!commitTransaction)
            {
                expectedMessageCount = expectedMethodCallCount;
                transactionOutcome = "Abort";
            }
            
            MessageService.IntendedInvocationCount = expectedMethodCallCount;

            // Start the service.
            MessageService.StartService(Util.GetBinding());

            // Call Service methods.
            this.SendMessages(transactionOutcome);

            // Allow the wcf service to process all the messages before validation.
            if (!MessageService.CompletionHandle.WaitOne(TimeSpan.FromSeconds(10.0), false))
            {
                Console.WriteLine("The service did not finish processing messages in 10 seconds. Test will be FAILED");
                testPassed = false;
            }

            MessageService.StopService();

            // Validate results.
            if (expectedMethodCallCount > MessageService.TotalMethodCallCount)
            {
                Console.WriteLine("The expected method call count was {0} but got {1}.", expectedMethodCallCount, MessageService.TotalMethodCallCount);
                testPassed = false;
            }

            int actualMessageCount = Util.GetMessageCountFromQueue(ListenUri);
            if (expectedMessageCount != actualMessageCount)
            {
                Console.WriteLine("The expected message count was {0} but got {1}.", expectedMessageCount, actualMessageCount);
                testPassed = false;
            }
            
            Assert.AreEqual(true, testPassed, "Results not as expected. Testcase FAILED.");

        }

        [TearDown]
        public void Cleanup()
        {
            if (MessageService.IsServiceRunning())
            {
                MessageService.StopService();
            }
        }

        private void SendMessages(string messageString)
        {
            // Create messages to send.
            string[] messages = new string[this.client.NumberOfMessages];
            for (int i = 0; i < this.client.NumberOfMessages; ++i)
            {
                messages[i] = messageString + " Message " + i;
            }

            // Create Amqpchannel and send messages.
            MethodInfo runClientMethod = this.client.GetType().GetMethod("RunTestClient");
            EndpointAddress address = new EndpointAddress(SendToUri);

            foreach (Type contractType in MessageService.ContractTypes)
            {
                MethodInfo runClientT = runClientMethod.MakeGenericMethod(contractType);
                runClientT.Invoke(this.client, new object[] { address, messages });
            }
        }
    }
}
