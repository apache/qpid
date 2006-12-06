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
using log4net;
using NUnit.Framework;
using Qpid.Messaging;

namespace Qpid.Client.Tests
{
    [TestFixture]
    public class HeadersMatchingProducer : BaseMessagingTestFixture
    {
        private static ILog _logger = LogManager.GetLogger(typeof(HeadersMatchingProducer));

        private string _commandExchangeName = "ServiceQ1";

        private int _messageCount = 12;

        private IMessagePublisher _publisher;

        [SetUp]
        public override void Init()
        {
            base.Init();

            try
            {
                _publisher = _channel.CreatePublisherBuilder()
                    .WithExchangeName(_commandExchangeName)
                    .WithMandatory(true)
                    .Create();

                // Disabling timestamps - a performance optimisation where timestamps and TTL/expiration 
                // are not required.
                _publisher.DisableMessageTimestamp = true;

                _publisher.DeliveryMode = DeliveryMode.NonPersistent;
            }
            catch (QpidException e)
            {
                _logger.Error("Error: " + e, e);
            }
        }
        
        [Test]
        public void SendMessages()
        {
            _connection.Start();
            for (int i = 0; i < _messageCount; i++)
            {
                int rem = i % 6;
                IMessage msg = null;
                switch (rem)
                {
                    case 0:
                        msg = _channel.CreateTextMessage("matches match2='bar'");
                        msg.Headers["match1"] = "foo";
                        msg.Headers["match2"] = "bar";
                        break;
                        
                    case 1:
                        msg = _channel.CreateTextMessage("not match - only match1");
                        msg.Headers["match1"] = "foo";
                        break;
                        
                    case 2:
                        msg = _channel.CreateTextMessage("not match - only match2");
                        msg.Headers["match2"] = "bar";
                        break;

                    case 3:
                        msg = _channel.CreateTextMessage("matches match2=''");
                        msg.Headers["match1"] = "foo";
                        msg.Headers["match2"] = "";
                        break;

                    case 4:
                        msg = _channel.CreateTextMessage("matches - extra headers");
                        msg.Headers["match1"] = "foo";
                        msg.Headers["match2"] = "bar";
                        msg.Headers["match3"] = "not required";
                        break;

                    case 5:
                        msg = _channel.CreateTextMessage("5: no match");
                        msg.Headers["match1"] = "foo1";
                        msg.Headers["match2"] = "bar";
                        msg.Headers["match3"] = "not required";
                        break;
                }
                _publisher.Send(msg);
            }
            _logger.Info("Finished sending " + _messageCount + " messages");
        }
    }
}
