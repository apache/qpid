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
using log4net;
using NUnit.Framework;
using Qpid.Messaging;

namespace Qpid.Client.Tests
{
    public class BaseMessagingTestFixture
    {
        private static ILog _logger = LogManager.GetLogger(typeof(BaseMessagingTestFixture));

        protected IConnection _connection;

        protected IChannel _channel;

        [SetUp]
        public virtual void Init()
        {
            try
            {
                QpidConnectionInfo connectionInfo = new QpidConnectionInfo();
                
                bool local = true;
                
                if (local)
                {
                    connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "localhost", 5672, false));                                    
                } 
                else
                {
                    connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "eqd-lxamq01.uk.jpmorgan.com", 8099, false));
                }
                _connection = new AMQConnection(connectionInfo);
                _channel = _connection.CreateChannel(false, AcknowledgeMode.NoAcknowledge, 1);
            }
            catch (QpidException e)
            {
                _logger.Error("Error initialisng test fixture: " + e, e);
                throw e;
            }
        }

        [TearDown]
        public void Shutdown()
        {
            Console.WriteLine("Shutdown");
            if (_connection != null)
            {
                Console.WriteLine("Disposing connection");
                _connection.Dispose();
            }
        }
    }
}
