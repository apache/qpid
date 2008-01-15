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
using Apache.Qpid.Messaging;

namespace Apache.Qpid.Integration.Tests.testcases
{
   /// <summary>
   /// Tests that when sending undeliverable messages with the 
   /// mandatory flag set, an exception is raised on the connection
   /// as the message is bounced back by the broker
   /// </summary>
  [TestFixture, Category("Integration")]
   public class UndeliverableTest : BaseMessagingTestFixture
   {
      private static ILog _logger = LogManager.GetLogger(typeof(UndeliverableTest));
      private ManualResetEvent _event;
      public const int TIMEOUT = 1000;
      private Exception _lastException;

      [SetUp]
      public override void Init()
      {
         base.Init();
         _event = new ManualResetEvent(false);
         _lastException = null;

         try
         {
            _connection.ExceptionListener = new ExceptionListenerDelegate(OnException);
         } catch ( QpidException e )
         {
            _logger.Error("Could not add ExceptionListener", e);
         }
      }

      public void OnException(Exception e)
      {
         // Here we dig out the AMQUndelivered exception (if present) in order to log the returned message.

         _lastException = e;
         _logger.Error("OnException handler received connection-level exception", e);
         if ( e is QpidException )
         {
            QpidException qe = (QpidException)e;
            if ( qe.InnerException is AMQUndeliveredException )
            {
               AMQUndeliveredException ue = (AMQUndeliveredException)qe.InnerException;
               _logger.Error("inner exception is AMQUndeliveredException", ue);
               _logger.Error(string.Format("Returned message = {0}", ue.GetUndeliveredMessage()));
            }
         }
         _event.Set();
      }

      [Test]
      public void SendUndeliverableMessageOnDefaultExchange()
      {
         SendOne("default exchange", null);
      }
      [Test]
      public void SendUndeliverableMessageOnDirectExchange()
      {
         SendOne("direct exchange", ExchangeNameDefaults.DIRECT);
      }
      [Test]
      public void SendUndeliverableMessageOnTopicExchange()
      {
         SendOne("topic exchange", ExchangeNameDefaults.TOPIC);
      }
      [Test]
      public void SendUndeliverableMessageOnHeadersExchange()
      {
         SendOne("headers exchange", ExchangeNameDefaults.HEADERS);
      }

      private void SendOne(string exchangeNameFriendly, string exchangeName)
      {
         _logger.Info("Sending undeliverable message to " + exchangeNameFriendly);

         // Send a test message to a non-existant queue 
         // on the specified exchange. See if message is returned!
         MessagePublisherBuilder builder = _channel.CreatePublisherBuilder()
             .WithRoutingKey("Non-existant route key!")
             .WithMandatory(true); // necessary so that the server bounces the message back
         if ( exchangeName != null )
         {
            builder.WithExchangeName(exchangeName);
         }
         IMessagePublisher publisher = builder.Create();
         publisher.Send(_channel.CreateTextMessage("Hiya!"));

         // check we received an exception on the connection
         // and that it is of the right type
         _event.WaitOne(TIMEOUT, true);

         Type expectedException = typeof(AMQUndeliveredException);
         Exception ex = _lastException;
         Assert.IsNotNull(ex, "No exception was thrown by the test. Expected " + expectedException);

         if ( ex.InnerException != null )
            ex = ex.InnerException;

         Assert.IsInstanceOfType(expectedException, ex);
      }
   }
}
