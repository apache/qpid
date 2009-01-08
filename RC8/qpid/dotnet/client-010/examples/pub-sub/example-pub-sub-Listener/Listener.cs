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

namespace org.apache.qpid.example.pubsub
{
    /// <summary>
    /// This program is one of two programs designed to be used
    /// together. These programs use the topic exchange.
    ///    
    /// Publisher:
    /// 
    /// Publishes to a broker, specifying a routing key.
    /// 
    /// Listener (this program):
    /// 
    /// Reads from a queue on the broker using a message listener.
    /// 
    /// </summary>
    internal class Listener
    {
        public static int _count = 4;

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

                lock (session)
                {
                    Console.WriteLine("Listening for messages ...");
                    // Create a listener                    
                    prepareQueue("usa", "usa.#", session);
                    prepareQueue("europe", "europe.#", session);
                    prepareQueue("news", "#.news", session);
                    prepareQueue("weather", "#.weather", session);
                    while (_count > 0)
                    {
                        Monitor.Wait(session);
                    }
                }

                //---------------------------------------------------------------------------

                connection.close();
            }
            catch (Exception e)
            {
                Console.WriteLine("Error: \n" + e.StackTrace);
            }
        }

        private static void prepareQueue(string queue, string routing_key, ClientSession session)
        {
            // Create a unique queue name for this consumer by concatenating
            // the queue name parameter with the Session ID.     
            Console.WriteLine("Declaring queue: " + queue);
            session.queueDeclare(queue, Option.EXCLUSIVE, Option.AUTO_DELETE);

            // Route messages to the new queue if they match the routing key.
            // Also route any messages to with the "control" routing key to
            // this queue so we know when it's time to stop. A publisher sends
            // a message with the content "That's all, Folks!", using the
            // "control" routing key, when it is finished.

            session.exchangeBind(queue, "amq.topic", routing_key);
            session.exchangeBind(queue, "amq.topic", "control");

            // subscribe the listener to the queue
            IMessageListener listener = new MessageListener(session);
            session.attachMessageListener(listener, queue);
            session.messageSubscribe(queue);
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
            Console.WriteLine("Message: " + message + " from " + m.Destination);
            // Add this message to the list of message to be acknowledged 
            _range.add(m.Id);
            if (message.Equals("That's all, folks!"))
            {
                Console.WriteLine("Shutting down listener for " + m.DeliveryProperties.getRoutingKey());
                Listener._count--;
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
