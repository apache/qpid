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
    [TestFixture, Category("Integration")]
    public class SyncConsumerTest : BaseMessagingTestFixture
    {
        private static readonly ILog _logger = LogManager.GetLogger(typeof(SyncConsumerTest));
        
        private string _commandQueueName = "ServiceQ1";
        private const int MESSAGE_COUNT = 1000;

        private IMessageConsumer _consumer;
        private IMessagePublisher _publisher;

        /// <summary> Holds the test connection. </summary>
        protected IConnection _connection;

        /// <summary> Holds the test channel. </summary>
        protected IChannel _channel;
        
        [SetUp]
        public override void Init()
        {
            base.Init();

            _connection = new AMQConnection(connectionInfo);
            _channel = _connection.CreateChannel(false, AcknowledgeMode.AutoAcknowledge, 500, 300);

            _publisher = _channel.CreatePublisherBuilder()
                .WithRoutingKey(_commandQueueName)
                .WithExchangeName(ExchangeNameDefaults.TOPIC)
                .Create();
            
            _publisher.DisableMessageTimestamp = true;
            _publisher.DeliveryMode = DeliveryMode.NonPersistent;
            
            string queueName = _channel.GenerateUniqueName();
            _channel.DeclareQueue(queueName, false, true, true);
            
            _channel.Bind(queueName, ExchangeNameDefaults.TOPIC, _commandQueueName);
            
            _consumer = _channel.CreateConsumerBuilder(queueName)
                .WithPrefetchLow(100).Create();
            _connection.Start();
        }

        /// <summary>
        /// Deregisters the on message delegate before closing the connection.
        /// </summary>
        [TearDown]
        public override void Shutdown()
        {
            _logger.Info("public void Shutdown(): called");

            //_consumer.OnMessage -= _msgRecDelegate;
            //_connection.ExceptionListener -= _exceptionDelegate;

            _connection.Stop();
            _connection.Close();
            _connection.Dispose();

            base.Shutdown();
        }

        [Test]
        public void ReceiveWithInfiniteWait()
        {
            // send all messages
            for ( int i = 0; i < MESSAGE_COUNT; i++ )
            {
                ITextMessage msg;
                try
                {
                    msg = _channel.CreateTextMessage(GetData(512 + 8 * i));
                } catch ( Exception e )
                {
                    _logger.Error("Error creating message: " + e, e);
                    break;
                }
                _publisher.Send(msg);
            }
            
            _logger.Debug("All messages sent");
            // receive all messages
            for ( int i = 0; i < MESSAGE_COUNT; i++ )
            {
                try
                {
                    IMessage msg = _consumer.Receive();
                    Assert.IsNotNull(msg);
                } catch ( Exception e )
                {
                    _logger.Error("Error receiving message: " + e, e);
                    Assert.Fail(e.ToString());
                }
            }
        }
        
        [Test]
        public void ReceiveWithTimeout()
        {
            ITextMessage msg = _channel.CreateTextMessage(GetData(512 + 8));
            _publisher.Send(msg);
            
            IMessage recvMsg = _consumer.Receive();
            Assert.IsNotNull(recvMsg);
            // empty queue, should timeout
            Assert.IsNull(_consumer.Receive(1000));
        }
    }
}
