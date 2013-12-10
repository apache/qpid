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
    class Client {
        static int Main(string[] args) {
            String url = "amqp:tcp:127.0.0.1:5672";
            String connectionOptions = "";

            if (args.Length > 0)
                url = args[0];
            if (args.Length > 1)
                connectionOptions = args[1];

            Connection connection = new Connection(url, connectionOptions);
            try
            {
                connection.Open();

                Session session = connection.CreateSession();

                Sender sender = session.CreateSender("service_queue");

                Address responseQueue = new Address("#response-queue; {create:always, delete:always}");
                Receiver receiver = session.CreateReceiver(responseQueue);

                String[] s = new String[] {
                    "Twas brillig, and the slithy toves",
                    "Did gire and gymble in the wabe.",
                    "All mimsy were the borogroves,",
                    "And the mome raths outgrabe."
                };

                Message request = new Message("");
                request.ReplyTo = responseQueue;

                for (int i = 0; i < s.Length; i++) {
                    request.SetContent(s[i]);
                    sender.Send(request);
                    Message response = receiver.Fetch();
                    Console.WriteLine("{0} -> {1}", request.GetContent(), response.GetContent());
                }
                connection.Close();
                return 0;
            }
            catch (Exception e)
            {
                Console.WriteLine("Exception {0}.", e);
                connection.Close();
            }
            return 1;
        }
    }
}
