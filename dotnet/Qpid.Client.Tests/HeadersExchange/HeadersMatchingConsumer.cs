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
using Qpid.Framing;
using Qpid.Messaging;

namespace Qpid.Client.Tests
{
    [TestFixture]
    public class HeadersMatchingConsumer : BaseMessagingTestFixture
    {
        private static ILog _logger = LogManager.GetLogger(typeof(HeadersMatchingConsumer));        

        private string _serviceName = "ServiceQ1";

        private AutoResetEvent _evt = new AutoResetEvent(false);

        [SetUp]
        public override void Init()
        {            
            base.Init();            

            _logger.Info("Starting...");

            _logger.Info("Service (queue) name is '" + _serviceName + "'...");

            _connection.ExceptionListener = new ExceptionListenerDelegate(OnException);

            // Declare a new HeadersExchange with the name of the service.
            _channel.DeclareExchange(_serviceName, ExchangeClassConstants.HEADERS);

            // Create non-durable, temporary (aka auto-delete), exclusive queue.
            string queueName = _channel.GenerateUniqueName();
            _channel.DeclareQueue(queueName, false, true, true);

            // Bind our queue to the new HeadersExchange.
            _channel.Bind(queueName, _serviceName, null, CreatePatternAsFieldTable());

            IMessageConsumer consumer = _channel.CreateConsumerBuilder(queueName)
                .WithPrefetchLow(100)
                .WithPrefetchHigh(500)
                .WithNoLocal(true)
                .Create();

            consumer.OnMessage = new MessageReceivedDelegate(OnMessage);
        }

        [Test]
        public void Test()
        {
            _connection.Start();
            _logger.Info("Waiting...");
            _evt.WaitOne();
        }

        public void OnMessage(IMessage message)
        {
            _logger.Info(string.Format("message.Type = {0}", message.GetType()));
            _logger.Info("Got message '" + message + "'");
        }

        private FieldTable CreatePatternAsFieldTable()
        {
            FieldTable matchTable = new FieldTable();
            // Currently all String matching must be prefixed by an "S" ("S" for string because of a failing of the FieldType definition).
            matchTable["Smatch1"] = "foo";
            matchTable["Smatch2"] = "";
            return matchTable;
        }

        public void OnException(Exception e)
        {            
            if (e is QpidException && e.InnerException is AMQDisconnectedException)
            {
                _logger.Error("Broker closed connection");
            }
            else
            {
                _logger.Error("Connection exception occurred: " + e);
            }
            _evt.Set();
        }
    }
}
