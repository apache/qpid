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
using org.apache.qpid.client;
using org.apache.qpid.transport;
using org.apache.qpid.transport.util;

namespace ExcelAddInProducer
{
    class Program
    {
        static void Main(string[] args)
        {
            Client client = new Client();
            Console.WriteLine("Client created");
            client.connect("192.168.1.14", 5672, "test", "guest", "guest");
            Console.WriteLine("Connection established");

            ClientSession ssn = client.createSession(50000);
            Console.WriteLine("Session created");
            ssn.queueDeclare("queue1", null, null);
            ssn.exchangeBind("queue1", "amq.direct", "queue1", null);

            for (int i = 0; i < 100; i++)
            {
                ssn.messageTransfer("amq.direct", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
                                    new Header(new DeliveryProperties().setRoutingKey("queue1"),
                                               new MessageProperties().setMessageId(UUID.randomUUID())),
                                    Encoding.UTF8.GetBytes("test: " + i));
                Thread.Sleep(1000);
            }

            client.close();
        }
    }
}
