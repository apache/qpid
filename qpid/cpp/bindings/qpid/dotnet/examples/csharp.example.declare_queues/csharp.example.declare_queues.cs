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
    class DeclareQueues {
        //
        // Sample invocation: csharp.example.declare_queues.exe localhost:5672 my-queue
        //
        static int Main(string[] args) {
            string addr = "localhost:5672";
            string queue = "my-queue";

            if (args.Length > 0)
                addr = args[0];
            if (args.Length > 1)
                queue = args[1];

            Connection connection = null;
            try
            {
                connection = new Connection(addr);
                connection.Open();
                Session session = connection.CreateSession();
                String queueName = queue + "; {create: always}";
                Sender sender = session.CreateSender(queueName);
                session.Close();
                connection.Close();
                return 0;
            }
            catch (Exception e)
            {
                Console.WriteLine("Exception {0}.", e);
                if (null != connection)
                    connection.Close();
            }
            return 1;
        }
    }
}
