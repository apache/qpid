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
using Org.Apache.Qpid.Messaging;

namespace Org.Apache.Qpid.Messaging.Examples {
    class Server {
        static int Main(string[] args) {
            // Usage: csharp.example.server [url [connectionOptions]]
            string url = "amqp:tcp:127.0.0.1:5672";
            string connectionOptions = "";

            if (args.Length > 0)
                url = args[0];
            if (args.Length > 1)
                connectionOptions = args[1];

            try {
                Connection connection = new Connection(url, connectionOptions);
                connection.Open();
                Session session = connection.CreateSession();
                Receiver receiver = session.CreateReceiver("service_queue; {create: always}");

                while (true) {
                    Message request = receiver.Fetch();
                    Address address = request.ReplyTo;

                    if (null != address) {
                        Sender sender = session.CreateSender(address);
                        String s = request.GetContent();
                        Message response = new Message(s.ToUpper());
                        sender.Send(response);
                        Console.WriteLine("Processed request: {0} -> {1}", request.GetContent(), response.GetContent());
                        session.Acknowledge();
                    } else {
                        Console.WriteLine("Error: no reply address specified for request: {0}", request.GetContent());
                        session.Reject(request);
                    }
                }
                // connection.Close();  // unreachable in this example
            } catch (Exception e) {
                Console.WriteLine("Exception {0}.", e);
            }
            return 1;
        }
    }
}
