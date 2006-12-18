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
    public class ServiceProvidingClient : BaseMessagingTestFixture
    {
        private static ILog _logger = LogManager.GetLogger(typeof(ServiceProvidingClient));

        private int _messageCount;

        private string _replyToExchangeName;
        private string _replyToRoutingKey;

        private IMessagePublisher _destinationPublisher;

        private string _serviceName = "ServiceQ1";

        private string _selector = null;

        //private EventWaitHandle _event = new ManualResetEvent(false);
        private AutoResetEvent _event = new AutoResetEvent(false);

        [SetUp]
        public override void Init()
        {            
            base.Init();

            _logger.Info("Starting...");
            _logger.Info("Service (queue) name is '" + _serviceName + "'...");

            _connection.ExceptionListener = new ExceptionListenerDelegate(OnConnectionException);

            _logger.Info("Message selector is <" + _selector + ">...");
            
            _channel.DeclareQueue(_serviceName, false, false, false);

            IMessageConsumer consumer = _channel.CreateConsumerBuilder(_serviceName)
                .WithPrefetchLow(100)
                .WithPrefetchHigh(500)
                .WithNoLocal(true)
                .Create();
            consumer.OnMessage = new MessageReceivedDelegate(OnMessage);
        }

        private void OnConnectionException(Exception e)
        {
            _logger.Info("Connection exception occurred", e);
            _event.Set(); // Shutdown test on error
            // XXX: Test still doesn't shutdown when broker terminates. Is there no heartbeat?
        }

        [Test]
        public void Test()
        {
            _connection.Start();
            _logger.Info("Waiting...");
            _event.WaitOne();
        }

        public void OnMessage(IMessage message)
        {
//            _logger.Info("Got message '" + message + "'");

            ITextMessage tm = (ITextMessage)message;

            try
            {
                string replyToExchangeName = tm.ReplyToExchangeName;
                string replyToRoutingKey = tm.ReplyToRoutingKey;

                _replyToExchangeName = replyToExchangeName;
                _replyToRoutingKey = replyToRoutingKey;
                _logger.Debug("About to create a producer");

//                Console.WriteLine("ReplyTo.ExchangeName = " + _replyToExchangeName);
//                Console.WriteLine("ReplyTo.RoutingKey = " + _replyToRoutingKey);

                _destinationPublisher = _channel.CreatePublisherBuilder()
                    .WithExchangeName(_replyToExchangeName)
                    .WithRoutingKey(_replyToRoutingKey)
                    .Create();
                _destinationPublisher.DisableMessageTimestamp = true;
                _destinationPublisher.DeliveryMode = DeliveryMode.NonPersistent;
                _logger.Debug("After create a producer");
            }
            catch (QpidException e)
            {
                _logger.Error("Error creating destination", e);
                throw e;
            }
            _messageCount++;
            if (_messageCount % 1000 == 0)
            {
                _logger.Info("Received message total: " + _messageCount);
                _logger.Info(string.Format("Sending response to '{0}:{1}'", 
                                           _replyToExchangeName, _replyToRoutingKey));
            }

            try
            {
                String payload = "This is a response: sing together: 'Mahnah mahnah...'" + tm.Text;
                ITextMessage msg = _channel.CreateTextMessage(payload);
                if (tm.Headers.Contains("timeSent"))
                {
//                    _logger.Info("timeSent property set on message");
//                    _logger.Info("timeSent value is: " + tm.Headers["timeSent"]);
                    msg.Headers["timeSent"] = tm.Headers["timeSent"];
                }
                _destinationPublisher.Send(msg);
                if (_messageCount % 1000 == 0)
                {
                    _logger.Info(string.Format("Sending response to '{0}:{1}'",
                                               _replyToExchangeName, _replyToRoutingKey));
                }
            }
            catch (QpidException e)
            {
                _logger.Error("Error sending message: " + e, e);
                throw e;
            }
        }               
    }
}
