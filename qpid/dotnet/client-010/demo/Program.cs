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
using System.IO;
using System.Text;
using System.Threading;
using log4net.Config;
using org.apache.qpid.client;
using org.apache.qpid.transport;
using org.apache.qpid.transport.util;

namespace WindowsClient
{
    class Program
    {
        static void Main(string[] args)
        {
             XmlConfigurator.Configure(new FileInfo("..\\..\\log.xml"));
            // DOMConfigurator.Configure()            
            Client client = new Client();
            Console.WriteLine("Client created");
            client.connect("192.168.1.14", 5673, "test", "guest", "guest");
            Console.WriteLine("Connection established");

            ClientSession ssn = client.createSession(50000);
            Console.WriteLine("Session created");
            ssn.queueDeclare("queue1", null, null);
            ssn.exchangeBind("queue1", "amq.direct", "queue1", null);


            Object wl = new Object();
            ssn.attachMessageListener(new MyListener(ssn, wl), "myDest");

            ssn.messageSubscribe("queue1", "myDest", MessageAcceptMode.EXPLICIT, MessageAcquireMode.PRE_ACQUIRED, null,
                                 0, null);
            DateTime start = DateTime.Now;

            // issue credits     
            ssn.messageSetFlowMode("myDest", MessageFlowMode.WINDOW);
            ssn.messageFlow("myDest", MessageCreditUnit.BYTE, ClientSession.MESSAGE_FLOW_MAX_BYTES);
            ssn.messageFlow("myDest", MessageCreditUnit.MESSAGE, 10000);
            ssn.sync();

            for (int i = 0; i < 10000; i ++)
            {            
            ssn.messageTransfer("amq.direct", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
                                new Header(new DeliveryProperties().setRoutingKey("queue1"),
                                           new MessageProperties().setMessageId(UUID.randomUUID())),
                                Encoding.UTF8.GetBytes("test: " + i));
            }

            lock(wl)
            {
                Monitor.Wait(wl);
            }
            DateTime now = DateTime.Now;
            Console.WriteLine("Start time " + start + " now: " + now);

            Console.WriteLine("Done time: " +  (now - start));
            lock (wl)
            {
                Monitor.Wait(wl, 30000);
            }
            client.close();
        }
    }

    class MyListener : IMessageListener
    {
        private readonly Object _wl;
        private ClientSession _session;
        private int _count;

        public MyListener(ClientSession session, object wl)
        {
            _wl = wl;
            _session = session;
            _count = 0;
        }

        public void messageTransfer(IMessage m)
        {
            BinaryReader reader = new BinaryReader(m.Body, Encoding.UTF8);
            byte[] body = new byte[m.Body.Length - m.Body.Position];
            reader.Read(body, 0, body.Length);
            ASCIIEncoding enc = new ASCIIEncoding();
        //   Console.WriteLine("Got a message: " + enc.GetString(body) + " count = " + _count);           
            _count++;
            if (_count == 10000)
            {
                lock (_wl)
                {
                    Monitor.PulseAll(_wl);
                }
            }
        }
    }
}
