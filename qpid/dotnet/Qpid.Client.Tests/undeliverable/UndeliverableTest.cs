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
using Qpid.Messaging;

namespace Qpid.Client.Tests
{
    [TestFixture]
    public class UndeliverableTest : BaseMessagingTestFixture
    {
        private static ILog _logger = LogManager.GetLogger(typeof(UndeliverableTest));

        [SetUp]
        public override void Init()
        {
            base.Init();

            try
            {
                _connection.ExceptionListener = new ExceptionListenerDelegate(OnException);
            }
            catch (QpidException e)
            {
                _logger.Error("Could not add ExceptionListener", e);
            }
        }

        public static void OnException(Exception e)
        {
            // Here we dig out the AMQUndelivered exception (if present) in order to log the returned message.

            _logger.Error("OnException handler received connection-level exception", e);
            if (e is QpidException)
            {
                QpidException qe = (QpidException)e;
                if (qe.InnerException is AMQUndeliveredException)
                {
                    AMQUndeliveredException ue = (AMQUndeliveredException)qe.InnerException;
                    _logger.Error("inner exception is AMQUndeliveredException", ue);
                    _logger.Error(string.Format("Returned message = {0}", ue.GetUndeliveredMessage()));

                }
            }
        }

        [Test]
        public void SendUndeliverableMessage()
        {
            SendOne("default exchange", null);
            SendOne("direct exchange", ExchangeNameDefaults.DIRECT);
            SendOne("topic exchange", ExchangeNameDefaults.TOPIC);
            SendOne("headers exchange", ExchangeNameDefaults.HEADERS);

            Thread.Sleep(1000); // Wait for message returns!
        }

        private void SendOne(string exchangeNameFriendly, string exchangeName)
        {
            _logger.Info("Sending undeliverable message to " + exchangeNameFriendly);

            // Send a test message to a non-existant queue on the default exchange. See if message is returned!
            MessagePublisherBuilder builder = _channel.CreatePublisherBuilder()
                .WithRoutingKey("Non-existant route key!")
                .WithMandatory(true);
            if (exchangeName != null)
            {
                builder.WithExchangeName(exchangeName);
            }
            IMessagePublisher publisher = builder.Create();
            publisher.Send(_channel.CreateTextMessage("Hiya!"));
        }
    }
}
