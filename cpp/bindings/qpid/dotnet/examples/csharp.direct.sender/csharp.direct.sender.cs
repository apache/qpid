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
using System.Collections.Generic;
using System.Linq;
using System.Text;
using org.apache.qpid.messaging;

namespace csharp.direct.sender
{
    class Program
    {
        static void Main(string[] args)
        {
            String host = "localhost:5672";
            String addr = "amq.direct/key";
            Int32 nMsg = 10;

            if (args.Length > 0)
                host = args[0];
            if (args.Length > 1)
                addr = args[1];
            if (args.Length > 2)
                nMsg = Convert.ToInt32(args[2]);

            Console.WriteLine("csharp.direct.sender");
            Console.WriteLine("host : {0}", host);
            Console.WriteLine("addr : {0}", addr);
            Console.WriteLine("nMsg : {0}", nMsg);
            Console.WriteLine();

            Connection conn = new Connection(host);

            conn.open();

            if (!conn.isOpen())
            {
                Console.WriteLine("Failed to open connection to host : {0}", host);
            }
            else
            {
                Session sess = conn.createSession();

                Sender snd = sess.createSender(addr);

                for (int i = 0; i < nMsg; i++)
                {
                    Message msg = new Message(String.Format("Test Message {0}", i));

                    snd.send(msg);
                }

                conn.close();
            }
        }
    }
}
