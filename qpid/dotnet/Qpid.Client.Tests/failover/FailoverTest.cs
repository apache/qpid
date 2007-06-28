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
using Qpid.Client.Qms;
using Qpid.Messaging;

namespace Qpid.Client.Tests.failover
{
    [TestFixture, Category("Failover")]
    public class FailoverTest : IConnectionListener
    {
        private static readonly ILog _logger = LogManager.GetLogger(typeof(FailoverTest));

        private IConnection _connection;
        private IChannel _channel;
        private IMessagePublisher _publisher;
        private int _count;

        private IMessageConsumer _consumerOfResponse;

        void DoFailoverTest(IConnectionInfo info)
        {
            DoFailoverTest(new AMQConnection(info));
        }

        void DoFailoverTest(IConnection connection)
        {
            AMQConnection amqConnection = (AMQConnection)connection;
            amqConnection.ConnectionListener = this;
            //Console.WriteLine("connection.url = " + amqConnection.ToURL());
            _connection = connection;
            _connection.ExceptionListener = new ExceptionListenerDelegate(OnConnectionException);
            _channel = _connection.CreateChannel(false, AcknowledgeMode.NoAcknowledge);

            string exchangeName = ExchangeNameDefaults.TOPIC;
            string routingKey = "topic1";

            string queueName = DeclareAndBindTemporaryQueue(exchangeName, routingKey);
            
            new MsgListener(_connection.CreateChannel(false, AcknowledgeMode.NoAcknowledge), queueName);

            IChannel channel = _channel;

            string tempQueueName = channel.GenerateUniqueName();
            channel.DeclareQueue(tempQueueName, false, true, true);
            _consumerOfResponse = channel.CreateConsumerBuilder(tempQueueName).Create();
            _consumerOfResponse.OnMessage = new MessageReceivedDelegate(OnMessage);

            _connection.Start();

            IMessage msg = _channel.CreateTextMessage("Init");
            // FIXME: Leaving ReplyToExchangeName as default (i.e. the default exchange)
            // FIXME: but the implementation might not like this as it defaults to null rather than "".
            msg.ReplyToRoutingKey = tempQueueName;
//            msg.ReplyTo = new ReplyToDestination("" /* i.e. the default exchange */, tempQueueName);
            _logger.Info(String.Format("sending msg.Text={0}", ((ITextMessage)msg).Text));

//            _publisher = _channel.CreatePublisher(exchangeName, exchangeClass, routingKey);
            _publisher = _channel.CreatePublisherBuilder()
                .WithRoutingKey(routingKey)
                .WithExchangeName(exchangeName)
                .Create();
            _publisher.Send(msg);
        }

        public string DeclareAndBindTemporaryQueue(string exchangeName, string routingKey)
        {
            string queueName = _channel.GenerateUniqueName();

            // Queue.Declare
            _channel.DeclareQueue(queueName, false, true, true);

            // Queue.Bind
            _channel.Bind(queueName, exchangeName, routingKey);
            return queueName;
        }

        private void OnConnectionException(Exception e)
        {
            _logger.Error("Connection exception occurred", e);
        }

        public void OnMessage(IMessage message)
        {
            try
            {
                _logger.Info("received message on temp queue msg.Text=" + ((ITextMessage)message).Text);
                Thread.Sleep(1000);
                _publisher.Send(_channel.CreateTextMessage("Message" + (++_count)));
            }
            catch (QpidException e)
            {
                error(e);
            }
        }

        private void error(Exception e)
        {
            _logger.Error("exception received", e);
            stop();
        }

        private void stop()
        {
            _logger.Info("Stopping...");
            try
            {
                _connection.Dispose();
            }
            catch (QpidException e)
            {
                _logger.Error("Failed to shutdown", e);
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
            _logger.Info("preFailover(" + redirect + ") called");
            return true;
        }

        public bool PreResubscribe()
        {
            _logger.Info("preResubscribe() called");
            return true;
        }

        public void FailoverComplete()
        {
            _logger.Info("failoverComplete() called");
        }

        private class MsgListener
        {
            private IChannel _session;
            private IMessagePublisher _publisher;

            internal MsgListener(IChannel session, string queueName)
            {
                _session = session;
                _session.CreateConsumerBuilder(queueName).Create().OnMessage = 
                    new MessageReceivedDelegate(OnMessage);
            }

            public void OnMessage(IMessage message)
            {
                try
                {
                    _logger.Info("Received: msg.Text = " + ((ITextMessage) message).Text);
                    if(_publisher == null)
                    {
                        _publisher = init(message);
                    }
                    reply(message);
                }
                catch (QpidException e)
                {
//                   Error(e);
                    _logger.Error("yikes", e); // XXX
                }
            }

            private void reply(IMessage message)
            {
                string msg = ((ITextMessage) message).Text;
                _logger.Info("sending reply - " + msg);
                _publisher.Send(_session.CreateTextMessage(msg));
            }

            private IMessagePublisher init(IMessage message)
            {
                _logger.Info(string.Format("creating reply producer with dest = '{0}:{1}'", 
                                           message.ReplyToExchangeName, message.ReplyToRoutingKey));

                string exchangeName = message.ReplyToExchangeName;
                string routingKey = message.ReplyToRoutingKey;

                //return _channel.CreatePublisher(exchangeName, exchangeClass, routingKey);
                return _session.CreatePublisherBuilder()
                    .WithExchangeName(exchangeName)
                    .WithRoutingKey(routingKey)
                    .Create();
            }
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
            try
            {
                QpidConnectionInfo connectionInfo = new QpidConnectionInfo();
                connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "localhost", 5672, false));
                connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "localhost", 5673, false));               
                DoFailoverTest(connectionInfo);
                while (true)
                {
                    Thread.Sleep(5000);
                }
            }
            catch (Exception e)
            {
                _logger.Error("Exception caught", e);
            }
        }*/

//        [Test]
//        public void TestWithUrl()
//        {
//            String clientId = "failover" + DateTime.Now.Ticks;
//            String defaultUrl = "amqp://guest:guest@" + clientId + "/test" +
//                                "?brokerlist='tcp://localhost:5672;tcp://localhost:5673'&failover='roundrobin'";
//
//            _logger.Info("url = [" + defaultUrl + "]");
//
//            //            _logger.Info("connection url = [" + new AMQConnectionURL(defaultUrl) + "]");
//
//            String broker = defaultUrl;
//            //new FailoverTest(broker);
//        }
    }
}
