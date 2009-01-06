/*
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
*/

using System;
using System.IO;
using System.Text;
using System.Threading;
using org.apache.qpid.client;
using org.apache.qpid.transport;

namespace org.apache.qpid.example.fanout
{
    /// <summary>
    /// This program is one of two programs designed to be used
    /// together. 
    /// 
    /// Producer (this program):
    /// 
    /// Publishes to a broker, specifying a routing key.
    /// 
    /// Listener:
    /// 
    /// Reads from a queue on the broker using a message listener.
    /// 
    /// </summary>
    public class Listener
    {
        private static void Main(string[] args)
        {
            string host = args.Length > 0 ? args[0] : "localhost";
            int port = args.Length > 1 ? Convert.ToInt32(args[1]) : 5672;
            Client connection = new Client();
            try
            {
                connection.connect(host, port, "test", "guest", "guest");
                ClientSession session = connection.createSession(50000);

                //--------- Main body of program --------------------------------------------
                // Each client creates its own private queue, using the 
                // session id to guarantee a unique name. It then routes
                // all messages from the fanout exchange to its own queue
                // by binding to the queue.
                //
                // The binding specifies a binding key, but for a fanout
                // exchange, the binding key is optional and is not used
                // for routing decisions. It can be useful for tracking
                // messages and routing in logs.

                string myQueue = session.Name;
                session.queueDeclare(myQueue, Option.EXCLUSIVE, Option.AUTO_DELETE);
                session.exchangeBind(myQueue, "amq.fanout", "my-key");

                lock (session)
                {
                    Console.WriteLine("Listening");
                    // Create a listener and subscribe it to my queue.
                    IMessageListener listener = new MessageListener(session);
                    session.attachMessageListener(listener, myQueue);
                    session.messageSubscribe(myQueue);
                    // Receive messages until all messages are received
                    Monitor.Wait(session);
                }

                //---------------------------------------------------------------------------

                connection.close();
            }
            catch (Exception e)
            {
                Console.WriteLine("Error: \n" + e.StackTrace);
            }
        }
    }

    public class MessageListener : IMessageListener
    {
        private readonly ClientSession _session;
        private readonly RangeSet _range = new RangeSet();
        public MessageListener(ClientSession session)
        {
            _session = session;
        }

        public void messageTransfer(IMessage m)
        {
            BinaryReader reader = new BinaryReader(m.Body, Encoding.UTF8);
            byte[] body = new byte[m.Body.Length - m.Body.Position];
            reader.Read(body, 0, body.Length);
            ASCIIEncoding enc = new ASCIIEncoding();
            string message = enc.GetString(body);
            Console.WriteLine("Message: " + message);
            // Add this message to the list of message to be acknowledged 
            _range.add(m.Id);
            if (message.Equals("That's all, folks!"))
            {
                // Acknowledge all the received messages 
                _session.messageAccept(_range);
                lock (_session)
                {
                    Monitor.Pulse(_session);
                }
            }
        }
    }
}
