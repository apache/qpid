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
using System.Text;
using org.apache.qpid.client;

namespace org.apache.qpid.example.pubsub
{
    /// <summary>
    /// This program is one of two programs designed to be used
    /// together. These programs use the topic exchange.
    ///    
    /// Publisher (this program):
    /// 
    /// Publishes to a broker, specifying a routing key.
    /// 
    /// Listener: 
    /// 
    /// Reads from a queue on the broker using a message listener.
    /// 
    /// </summary>
    internal class Publisher
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

                publishMessages(session, "usa.news");
                publishMessages(session, "usa.weather");
                publishMessages(session, "europe.news");
                publishMessages(session, "europe.weather");

                noMoreMessages(session);

                //-----------------------------------------------------------------------------

                connection.close();
            }
            catch (Exception e)
            {
                Console.WriteLine("Error: \n" + e.StackTrace);
            }
        }

        private static void publishMessages(ClientSession session, string routing_key)
        {
            IMessage message = new Message();
            // Asynchronous transfer sends messages as quickly as
            // possible without waiting for confirmation.
            for (int i = 0; i < 10; i++)
            {
                message.clearData();
                message.appendData(Encoding.UTF8.GetBytes("Message " + i));
                session.messageTransfer("amq.topic", routing_key, message);
            }
        }

        private static void noMoreMessages(ClientSession session)
        {
            IMessage message = new Message();
            // And send a syncrhonous final message to indicate termination.
            message.clearData();
            message.appendData(Encoding.UTF8.GetBytes("That's all, folks!"));
            session.messageTransfer("amq.topic", "control", message);
            session.sync();
        }
    }
}
