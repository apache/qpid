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
using NUnit.Framework;
using Qpid.Client.Protocol;
using Qpid.Framing;
using Qpid.Messaging;

namespace Qpid.Client.Transport
{
    [TestFixture]
    public class BlockingIo
    {
        [Test]
        public void connectFromOutside()
        {
            QpidConnectionInfo connectionInfo = new QpidConnectionInfo();
            connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "localhost", 5672, false));
            AMQConnection connection = new AMQConnection(connectionInfo);
            ProtocolWriter protocolWriter = connection.ConvenientProtocolWriter;

            // TODO: Open channels and handle them simultaneously.
            // Need more thread here?
            // Send ChannelOpen.
            ushort channelId = 1;
            protocolWriter.SyncWrite(ChannelOpenBody.CreateAMQFrame(channelId, null), typeof (ChannelOpenOkBody));

            connection.Close();
        }

        [Test]
        public void regularConnection()
        {
            QpidConnectionInfo connectionInfo = new QpidConnectionInfo();
            connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "localhost", 5672, false));
            using (IConnection connection = new AMQConnection(connectionInfo)) {
                Console.WriteLine("connection = {0}", connection);
                Thread.Sleep(2000);
            }
        }

        [Test]
        public void connectionAndSleepForHeartbeats()
        {
            QpidConnectionInfo connectionInfo = new QpidConnectionInfo();
            connectionInfo.AddBrokerInfo(new AmqBrokerInfo("amqp", "localhost", 5672, false));
            using (IConnection connection = new AMQConnection(connectionInfo))
            {
                Console.WriteLine("connection = {0}", connection);
                Thread.Sleep(60000);
            }
        }
    }
}
