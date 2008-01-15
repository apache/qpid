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
using System.Runtime.InteropServices;
using System.Threading;
using log4net;
using NUnit.Framework;
using Apache.Qpid.Client.Qms;
using Apache.Qpid.Client;
using Apache.Qpid.Messaging;

namespace Apache.Qpid.Integration.Tests.testcases
{
    [TestFixture, Category("Integration")]
    public class MandatoryMessageTest
    {
        private static readonly ILog _log = LogManager.GetLogger(typeof(MandatoryMessageTest));

        /// <summary>Specifies the number of times to run the test cycle.</summary>
        const int NUM_MESSAGES = 10;

        /// <summary>Determines how many messages to send within each commit.</summary>
        const int COMMIT_BATCH_SIZE = 1;

        /// <summary>Specifies the duration of the pause to place between each message sent in the test.</summary>
        //const int SLEEP_MILLIS = 1;

        /// <summary>Specified the maximum time in milliseconds to wait for the test to complete.</summary>
        const int TIMEOUT = 10000;

        /// <summary>Defines the number of test messages to send, before prompting the user to fail a broker.</summary>
        const int FAIL_POINT = 5;

        /// <summary>Specified the ack mode to use for the test.</summary>
        AcknowledgeMode _acknowledgeMode = AcknowledgeMode.AutoAcknowledge;

        /// <summary>Determines whether this test runs transactionally or not. </summary>
        bool transacted = false;

        /// <summary>Holds the connection to run the test over.</summary>
        AMQConnection _connection;

        /// <summary>Holds the channel for the test message publisher. </summary>
        IChannel publishingChannel;

        /// <summary>Holds the test message publisher. </summary>
        IMessagePublisher publisher;

        /// <summary>Used to keep count of the number of messages sent. </summary>
        int messagesSent;

        /// <summary>Used to keep count of the number of messages received. </summary>
        int messagesReceived;

        /// <summary>Used to wait for test completion on. </summary>
        private static object testComplete = new Object();

        /// <summary>
        /// </summary>
        /// [SetUp]
        public void Init(IConnectionInfo connectionInfo)
        {
            // Reset all counts.
            messagesSent = 0;
            messagesReceived = 0;

            // Create a connection for the test.
            _connection = new AMQConnection(connectionInfo);

            // Create a consumer to receive the test messages.
            IChannel receivingChannel = _connection.CreateChannel(false, _acknowledgeMode);

            string queueName = receivingChannel.GenerateUniqueName();
            receivingChannel.DeclareQueue(queueName, false, true, true);
            receivingChannel.Bind(queueName, "amq.direct", queueName);

            IMessageConsumer consumer = receivingChannel.CreateConsumerBuilder(queueName)
                .WithPrefetchLow(30)
                .WithPrefetchHigh(60).Create();

            consumer.OnMessage = new MessageReceivedDelegate(OnMessage);
            _connection.Start();

            // Create a publisher to send the test messages.
            publishingChannel = _connection.CreateChannel(transacted, AcknowledgeMode.NoAcknowledge);
            publisher = publishingChannel.CreatePublisherBuilder()
                .WithRoutingKey(queueName)
                .Create();

            _log.Debug("connection = " + _connection);
            _log.Debug("connectionInfo = " + connectionInfo);
            _log.Debug("connection.AsUrl = " + _connection.toURL());
            _log.Debug("AcknowledgeMode is " + _acknowledgeMode);
        }

        /// <summary>
        /// Clean up the test connection.
        /// </summary>
        [TearDown]
        public virtual void Shutdown()
        {
            Thread.Sleep(2000);
            _connection.Close();
        }

        /// <summary>
        /// Runs a failover test, with the failover configuration specified in the Qpid connection URL format.
        /// </summary>
        [Test]
        public void TestWithUrl()
        {
            _log.Debug("public void runTestWithUrl(): called");

            // Parse the connection parameters from a URL.
            String clientId = "failover" + DateTime.Now.Ticks;
            string defaultUrl = "amqp://guest:guest@" + clientId + "/test" +
                "?brokerlist='tcp://localhost:5672;tcp://localhost:5673'&failover='roundrobin'";            
            IConnectionInfo connectionInfo = QpidConnectionInfo.FromUrl(defaultUrl);
            
            Init(connectionInfo);
            DoMandatoryMessageTest();
        }

        /// <summary>
        /// Send the test messages, prompting at the fail point for the user to cause a broker failure. The test checks that all messages sent
        /// are received within the test time limit.
        /// </summary>
        ///
        /// <param name="connectionInfo">The connection parameters, specifying the brokers to fail between.</param>
        void DoMandatoryMessageTest()
        {
            _log.Debug("void DoMandatoryMessageTest(IConnectionInfo connectionInfo): called");

            for (int i = 1; i <= NUM_MESSAGES; ++i)
            {
                ITextMessage msg = publishingChannel.CreateTextMessage("message=" + messagesSent);
                //_log.Debug("sending message = " + msg.Text);
                publisher.Send(msg);
                messagesSent++;

                _log.Debug("messagesSent = " + messagesSent);

                if (transacted)
                {
                    publishingChannel.Commit();
                }
            }

            // Wait for all of the test messages to be received, checking that this occurs within the test time limit.
            bool withinTimeout;

            lock(testComplete)
            {
                withinTimeout = Monitor.Wait(testComplete, TIMEOUT);
            }            

            if (!withinTimeout)
            {
                Assert.Fail("Test timed out, before all messages received.");
            }

            _log.Debug("void DoMandatoryMessageTest(IConnectionInfo connectionInfo): exiting");
        }

        /// <summary>
        /// Receives all of the test messages.
        /// </summary>
        ///
        /// <param name="message">The newly arrived test message.</param>
        public void OnMessage(IMessage message)
        {
            try
            {
                if (_acknowledgeMode == AcknowledgeMode.ClientAcknowledge)
                {
                    message.Acknowledge();
                }

                messagesReceived++;

                _log.Debug("messagesReceived = " + messagesReceived);

                // Check if all of the messages in the test have been received, in which case notify the message producer that the test has 
                // succesfully completed.
                if (messagesReceived == NUM_MESSAGES)
                {
                    lock (testComplete)
                    {
                        Monitor.Pulse(testComplete);
                    }
                }
            }
            catch (QpidException e)
            {
                _log.Fatal("Exception received. About to stop.", e);
                Stop();
            }
        }

        // <summary>Closes the test connection.</summary>
        private void Stop()
        {
            _log.Debug("Stopping...");
            try
            {
                _connection.Close();
            }
            catch (QpidException e)
            {
                _log.Debug("Failed to shutdown: ", e);
            }
        }
    }
}
