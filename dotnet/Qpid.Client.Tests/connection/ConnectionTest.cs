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
using Qpid.Client.qms;
using Qpid.Messaging;

namespace Qpid.Client.Tests.connection
{
    [TestFixture]
    public class ConnectionTest
    {
        [Test]
        public void simpleConnection()
        {
            ConnectionInfo connectionInfo = new QpidConnectionInfo();
            connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "localhost", 5672, false));
            using (IConnection connection = new AMQConnection(connectionInfo))
            {
                Console.WriteLine("connection = " + connection);
            }
        }

        [Test]
        public void passwordFailureConnection()
        {
            ConnectionInfo connectionInfo = new QpidConnectionInfo();
            connectionInfo.setPassword("rubbish");
            connectionInfo.AddBrokerInfo(new AmqBrokerInfo());
            try
            {
                using (IConnection connection = new AMQConnection(connectionInfo))
                {
                    Console.WriteLine("connection = " + connection);
                }
            } 
            catch (AMQException)
            {
                Assert.Fail();
//                if (!(e is AMQAuthenticationException))
//                {
//                    Assert.Fail("Expected AMQAuthenticationException!");
//                }
            }
        }
//
//        [Test]
//        public void connectionFailure()
//        {
//            try
//            {
//                new AMQConnection("amqp://guest:guest@clientid/testpath?brokerlist='tcp://localhost:5673?retries='0''");
//                Assert.fail("Connection should not be established");
//            }
//            catch (AMQException amqe)
//            {
//                if (!(amqe instanceof AMQConnectionException))
//                {
//                    Assert.fail("Correct exception not thrown");
//                }
//            }
//        }
//
//        [Test]
//        public void unresolvedHostFailure()
//        {
//            try
//            {
//                new AMQConnection("amqp://guest:guest@clientid/testpath?brokerlist='tcp://rubbishhost:5672?retries='0''");
//                Assert.fail("Connection should not be established");
//            }
//            catch (AMQException amqe)
//            {
//                if (!(amqe instanceof AMQUnresolvedAddressException))
//                {
//                    Assert.fail("Correct exception not thrown");
//                }
//            }
//        }
    }
}