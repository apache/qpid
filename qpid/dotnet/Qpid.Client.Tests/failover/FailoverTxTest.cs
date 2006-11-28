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
using Qpid.Client.qms;
using Qpid.Messaging;

namespace Qpid.Client.Tests.failover
{
    [TestFixture]
    public class FailoverTxTest : IConnectionListener
    {
        private static readonly ILog _log = LogManager.GetLogger(typeof(FailoverTxTest));

        const int NUM_ITERATIONS = 10;
        const int NUM_COMMITED_MESSAGES = 10;
        const int NUM_ROLLEDBACK_MESSAGES = 5;
        const int SLEEP_MILLIS = 500;

        AMQConnection _connection;

        public void onMessage(IMessage message)
        {
            try
            {
                _log.Info("Received: " + ((ITextMessage) message).Text);
            }
            catch (QpidException e)
            {
               error(e);
            }
        }

        void DoFailoverTxTest(ConnectionInfo connectionInfo)
        {
            _connection = new AMQConnection(connectionInfo);
            _connection.ConnectionListener = this;
            _log.Info("connection = " + _connection);
            _log.Info("connectionInfo = " + connectionInfo);
            _log.Info("connection.asUrl = " + _connection.toURL());

            IChannel receivingChannel = _connection.CreateChannel(false, AcknowledgeMode.NoAcknowledge);

            string queueName = receivingChannel.GenerateUniqueName();

            // Queue.Declare
            receivingChannel.DeclareQueue(queueName, false, true, true);

            // No need to call Queue.Bind as automatically bound to default direct exchange.
            receivingChannel.Bind(queueName, "amq.direct", queueName);

            receivingChannel.CreateConsumerBuilder(queueName).Create().OnMessage = new MessageReceivedDelegate(onMessage);

            _connection.Start();

            publishInTx(queueName);

            Thread.Sleep(2000); // Wait a while for last messages.

            _connection.Close();
            _log.Info("FailoverTxText complete");
        }

        private void publishInTx(string routingKey)
        {
            _log.Info("sendInTx");
            bool transacted = true;
            IChannel publishingChannel = _connection.CreateChannel(transacted, AcknowledgeMode.NoAcknowledge);
            IMessagePublisher publisher = publishingChannel.CreatePublisherBuilder()
                .withRoutingKey(routingKey)
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

        private void error(Exception e)
        {
            _log.Fatal("Exception received. About to stop.", e);
            stop();
        }

        private void stop()
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
        public void TestWithBasicInfo()
        {
            Console.WriteLine("TestWithBasicInfo");
            Console.WriteLine(".NET Framework version: " + RuntimeEnvironment.GetSystemVersion());
            try
            {
                QpidConnectionInfo connectionInfo = new QpidConnectionInfo();
                
                bool local = true;
                if (local)
                {
                    connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "localhost", 5672, false));
                    connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "localhost", 5673, false));
                }
                else
                {
                    connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "eqd-lxamq01.uk.jpmorgan.com", 8099, false));                    
                }
                
                DoFailoverTxTest(connectionInfo);
            }
            catch (Exception e)
            {
                _log.Error("Exception caught", e);
            }
        }

        //[Test]
        //public void runTestWithUrl()
        //{
        //    try {
        //        String clientId = "failover" + DateTime.Now.Ticks;
        //        string defaultUrl = "amqp://guest:guest@" + clientId + "/test" +
        //                    "?brokerlist='tcp://localhost:5672;tcp://localhost:5673'&failover='roundrobin'";

        //        _log.Info("url = [" + defaultUrl + "]");

        //        _log.Info("connection url = [" + new AMQConnectionInfo(defaultUrl) + "]");

        //        DoFailoverTxTest(new AMQConnectionInfo(defaultUrl));
        //    } catch (Exception e) {
        //        _log.Error("test failed", e);
        //    }
        //}
    }
}
