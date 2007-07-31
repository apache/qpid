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
using Apache.Qpid.Messaging;

namespace Apache.Qpid.Client.Tests.failover
{
    [TestFixture, Category("Failover")]
    public class FailoverTxTest : IConnectionListener
    {
        private static readonly ILog _log = LogManager.GetLogger(typeof(FailoverTxTest));

        const int NUM_ITERATIONS = 10;
        const int NUM_COMMITED_MESSAGES = 10;
        const int NUM_ROLLEDBACK_MESSAGES = 3;
        const int SLEEP_MILLIS = 50;

        // AutoAcknowledge, ClientAcknowledge, DupsOkAcknowledge, NoAcknowledge, PreAcknowledge
        AcknowledgeMode _acknowledgeMode = AcknowledgeMode.DupsOkAcknowledge;
        const bool _noWait = true; // use Receive or ReceiveNoWait
        AMQConnection _connection;

        public void OnMessage(IMessage message)
        {
            try
            {
                _log.Info("Received: " + ((ITextMessage) message).Text);
                if (_acknowledgeMode == AcknowledgeMode.ClientAcknowledge)
                {
                    _log.Info("client acknowledging");
                    message.Acknowledge();
                }
            }
            catch (QpidException e)
            {
               Error(e);
            }
        }

        class NoWaitConsumer
        {
            FailoverTxTest _failoverTxTest;
            IMessageConsumer _consumer;
            private bool _noWait;

            internal NoWaitConsumer(FailoverTxTest failoverTxTest, IMessageConsumer channel, bool noWait)
            {
                _failoverTxTest = failoverTxTest;
                _consumer = channel;
                _noWait = noWait;
            }

            internal void Run()
            {
                int messages = 0;
                while (messages < NUM_COMMITED_MESSAGES)
                {
                    IMessage msg;
                    if (_noWait) msg = _consumer.ReceiveNoWait();
                    else msg = _consumer.Receive();
                    if (msg != null)
                    {
                        _log.Info("NoWait received message");
                        ++messages;
                        _failoverTxTest.OnMessage(msg);
                    }
                    else
                    {
                        Thread.Sleep(1);
                    }

                }

            }
        }

        void DoFailoverTxTest(IConnectionInfo connectionInfo)
        {
            _connection = new AMQConnection(connectionInfo);
            _connection.ConnectionListener = this;
            _log.Info("connection = " + _connection);
            _log.Info("connectionInfo = " + connectionInfo);
            _log.Info("connection.AsUrl = " + _connection.toURL());

            _log.Info("AcknowledgeMode is " + _acknowledgeMode);
            IChannel receivingChannel = _connection.CreateChannel(false, _acknowledgeMode);

            string queueName = receivingChannel.GenerateUniqueName();

            // Queue.Declare
            receivingChannel.DeclareQueue(queueName, false, true, true);

            // No need to call Queue.Bind as automatically bound to default direct exchange.
            receivingChannel.Bind(queueName, "amq.direct", queueName);

            IMessageConsumer consumer = receivingChannel.CreateConsumerBuilder(queueName)
                .WithPrefetchLow(30)
                .WithPrefetchHigh(60).Create();
            bool useThread = false;
            if (useThread)
            {
                NoWaitConsumer noWaitConsumer = new NoWaitConsumer(this, consumer, _noWait);
                new Thread(new ThreadStart(noWaitConsumer.Run)).Start();
            }
            else
            {
                consumer.OnMessage = new MessageReceivedDelegate(OnMessage);
            }

            _connection.Start();

            PublishInTx(queueName);

            Thread.Sleep(2000); // Wait a while for last messages.

            _connection.Close();
            _log.Info("FailoverTxText complete");
        }

        private void PublishInTx(string routingKey)
        {
            _log.Info("sendInTx");
            bool transacted = true;
            IChannel publishingChannel = _connection.CreateChannel(transacted, AcknowledgeMode.NoAcknowledge);
            IMessagePublisher publisher = publishingChannel.CreatePublisherBuilder()
                .WithRoutingKey(routingKey)
                .Create();

            for (int i = 1; i <= NUM_ITERATIONS; ++i)
            {
                for (int j = 1; j <= NUM_ROLLEDBACK_MESSAGES; ++j)
                {
                    ITextMessage msg = publishingChannel.CreateTextMessage("Tx=" + i + " rolledBackMsg=" + j);
                    _log.Info("sending message = " + msg.Text);
                    publisher.Send(msg);
                    Thread.Sleep(SLEEP_MILLIS);
                }
                if (transacted) publishingChannel.Rollback();

                for (int j = 1; j <= NUM_COMMITED_MESSAGES; ++j)
                {
                    ITextMessage msg = publishingChannel.CreateTextMessage("Tx=" + i + " commitedMsg=" + j);
                    _log.Info("sending message = " + msg.Text);
                    publisher.Send(msg);
                    Thread.Sleep(SLEEP_MILLIS);
                }
                if (transacted) publishingChannel.Commit();
            }
        }

        private void Error(Exception e)
        {
            _log.Fatal("Exception received. About to stop.", e);
            Stop();
        }

        private void Stop()
        {
            _log.Info("Stopping...");
            try
            {
                _connection.Close();
            }
            catch (QpidException e)
            {
                _log.Info("Failed to shutdown: ", e);
            }
        }

        public void BytesSent(long count)
        {
        }

        public void BytesReceived(long count)
        {
        }

        public bool PreFailover(bool redirect)
        {
            _log.Info("preFailover(" + redirect + ") called");
            return true;
        }

        public bool PreResubscribe()
        {
            _log.Info("preResubscribe() called");
            return true;
        }

        public void FailoverComplete()
        {
            _log.Info("failoverComplete() called");
        }

        [Test]
        public void TestFail()
        {
            Assert.Fail("Tests in this class do not pass, but hang forever, so commented out until can be fixed.");
        }

        /*[Test]
        public void TestWithBasicInfo()
        {
            Console.WriteLine("TestWithBasicInfo");
            Console.WriteLine(".NET Framework version: " + RuntimeEnvironment.GetSystemVersion());
            QpidConnectionInfo connectionInfo = new QpidConnectionInfo();

            connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "localhost", 5672, false));
            connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "localhost", 5673, false));

            DoFailoverTxTest(connectionInfo);
        }*/

        /*[Test]
        public void runTestWithUrl()
        {
            String clientId = "failover" + DateTime.Now.Ticks;
            string defaultUrl = "amqp://guest:guest@" + clientId + "/test" +
                    "?brokerlist='tcp://localhost:5672;tcp://localhost:5673'&failover='roundrobin'";

            _log.Info("url = [" + defaultUrl + "]");

            IConnectionInfo connectionInfo = QpidConnectionInfo.FromUrl(defaultUrl);

            _log.Info("connection url = [" + connectionInfo + "]");

            DoFailoverTxTest(connectionInfo);
        }*/
    }
}
