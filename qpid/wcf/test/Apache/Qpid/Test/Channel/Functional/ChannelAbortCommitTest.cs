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
    using System.Collections.Generic;
    using System.ServiceModel.Channels;
    using NUnit.Framework;

    [TestFixture(1, true, false, true, true)]
    [TestFixture(1, true, false, true, false)]
    [TestFixture(1, true, false, false, true)]
    [TestFixture(1, true, false, false, false)]
    [TestFixture(1, false, true, true, true)]
    [TestFixture(1, false, true, true, false)]
    [TestFixture(1, false, true, false, true)]
    [TestFixture(1, false, true, false, false)]
    [TestFixture(5, true, false, true, true)]
    [TestFixture(5, true, false, true, false)]
    [TestFixture(5, true, false, false, true)]
    [TestFixture(5, true, false, false, false)]
    [TestFixture(5, false, true, true, true)]
    [TestFixture(5, false, true, true, false)]
    [TestFixture(5, false, true, false, true)]
    [TestFixture(5, false, true, false, false)]
    public class ChannelAbortCommitTest
    {
        private const string SendToUri = "amqp:amq.direct?routingkey=routing_key";
        private const string ListenUri = "amqp:message_queue";

        private ChannelContextParameters contextParameters;
        private Binding channelBinding;
        private List<string> expectedResults;

        public ChannelAbortCommitTest(int numberOfThreads, bool sendAbort, bool receiveAbort, bool asyncSend, bool asyncReceive)
        {
            this.contextParameters = new ChannelContextParameters();
            this.contextParameters.NumberOfThreads = numberOfThreads;
            this.contextParameters.SenderShouldAbort = sendAbort;
            this.contextParameters.ReceiverShouldAbort = receiveAbort;
            this.contextParameters.AsyncSend = asyncSend;
            this.contextParameters.AsyncReceive = asyncReceive;            
        }

        [SetUp]
        public void Setup()
        {
            this.channelBinding = Util.GetBinding();
            this.GenerateExpectedResults();
        }

        [Test]
        public void Run()
        {
            ChannelReceiver receiver = new ChannelReceiver(this.contextParameters, this.channelBinding);
            ChannelSender sender = new ChannelSender(this.contextParameters, this.channelBinding);

            sender.Run(SendToUri);
            receiver.Run(ListenUri);

            // Validate results.
            bool comparisonOutcome = Util.CompareResults(this.expectedResults, receiver.Results);
            Assert.AreEqual(true, comparisonOutcome, "The actual results were not as expected");
            Assert.AreEqual(0, Util.GetMessageCountFromQueue(ListenUri), "The actual message count wasn't as expected.");
        }

        [TearDown]
        public void Cleanup()
        {
            Util.PurgeQueue(ListenUri);
        }

        private void GenerateExpectedResults()
        {
            this.expectedResults = new List<string>();

            if (this.contextParameters.NumberOfThreads == 1)
            {
                this.expectedResults.Add("Received message with Action 'FirstMessage'");
                this.expectedResults.Add("Received message with Action 'Message 1'");
                this.expectedResults.Add("Received message with Action 'Message 2'");
                this.expectedResults.Add("Received message with Action 'Message 3'");
                this.expectedResults.Add("Received message with Action 'Message 4'");
                this.expectedResults.Add("Received message with Action 'Message 5'");
            }
            else
            {
                this.expectedResults.Add("Received message with Action 'FirstMessage'");
                this.expectedResults.Add("Received message with Action 'Message'");
                this.expectedResults.Add("Received message with Action 'Message'");
                this.expectedResults.Add("Received message with Action 'Message'");
                this.expectedResults.Add("Received message with Action 'Message'");
                this.expectedResults.Add("Received message with Action 'Message'");
            }
        }
    }
}
