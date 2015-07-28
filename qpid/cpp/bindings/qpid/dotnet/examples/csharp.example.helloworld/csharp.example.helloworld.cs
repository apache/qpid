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

namespace Org.Apache.Qpid.Messaging {
    class Program {
        static void Main(string[] args) {
            String broker = args.Length > 0 ? args[0] : "localhost:5672";
            String address = args.Length > 1 ? args[1] : "amq.topic";

            Connection connection = null;
            try {
                connection = new Connection(broker);
                connection.Open();
                Session session = connection.CreateSession();

                Receiver receiver = session.CreateReceiver(address);
                Sender sender = session.CreateSender(address);

                Message message = new Message();
                message.SetContentObject("Hello world!"); // all strings are utf-8 encoded
                sender.Send(message);
 
                message = receiver.Fetch(DurationConstants.SECOND * 1);
                Console.WriteLine("{0}", message.GetContent());
                session.Acknowledge();

                connection.Close();
            } catch (Exception e) {
                Console.WriteLine("Exception {0}.", e);
                if (null != connection)
                    connection.Close();
            }
        }
    }
}
