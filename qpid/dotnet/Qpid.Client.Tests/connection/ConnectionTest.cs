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
using NUnit.Framework;
using Qpid.Client.Qms;
using Qpid.Messaging;

namespace Qpid.Client.Tests.Connection
{
    [TestFixture]
    public class ConnectionTest
    {
        private AmqBrokerInfo _broker = 
           new AmqBrokerInfo("amqp", "localhost", 5672, false);
        
        [Test]
        public void SimpleConnection()
        {
            IConnectionInfo connectionInfo = new QpidConnectionInfo();
            connectionInfo.VirtualHost = "test";
            connectionInfo.AddBrokerInfo(_broker);
            using (IConnection connection = new AMQConnection(connectionInfo))
            {
                Console.WriteLine("connection = " + connection);
            }
        }

        [Test]
        [ExpectedException(typeof(AMQAuthenticationException))]
        public void PasswordFailureConnection()
        {
            IConnectionInfo connectionInfo = new QpidConnectionInfo();
            connectionInfo.VirtualHost = "test";
            connectionInfo.Password = "rubbish";
            connectionInfo.AddBrokerInfo(_broker);

            using (IConnection connection = new AMQConnection(connectionInfo))
            {
                 Console.WriteLine("connection = " + connection);
                 // wrong
                 Assert.Fail("Authentication succeeded but should've failed");
            }
        }

        [Test]
        [ExpectedException(typeof(AMQConnectionException))]
        public void ConnectionFailure()
        {
            string url = "amqp://guest:guest@clientid/testpath?brokerlist='tcp://localhost:5673?retries='0''";
            new AMQConnection(QpidConnectionInfo.FromUrl(url));
            Assert.Fail("Connection should not be established");
        }
    }
}
