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

namespace Apache.Qpid.Client.Tests
{
   [TestFixture]
   public class TestSyncConsumer : BaseMessagingTestFixture
   {
      private static readonly ILog _logger = LogManager.GetLogger(typeof(TestSyncConsumer));

      private string _commandQueueName = "ServiceQ1";
      private const int MESSAGE_COUNT = 1000;
      private const string MESSAGE_DATA_BYTES = "jfd ghljgl hjvhlj cvhvjf ldhfsj lhfdsjf hldsjfk hdslkfj hsdflk  ";

      private static String GetData(int size)
      {
         StringBuilder buf = new StringBuilder(size);
         int count = 0;
         while ( count < size + MESSAGE_DATA_BYTES.Length )
         {
            buf.Append(MESSAGE_DATA_BYTES);
            count += MESSAGE_DATA_BYTES.Length;
         }
         if ( count < size )
         {
            buf.Append(MESSAGE_DATA_BYTES, 0, size - count);
         }

         return buf.ToString();
      }

      private IMessageConsumer _consumer;
      private IMessagePublisher _publisher;

      [SetUp]
      public override void Init()
      {
         base.Init();
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
