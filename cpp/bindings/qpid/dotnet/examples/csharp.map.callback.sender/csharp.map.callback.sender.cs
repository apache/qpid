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
using System.Collections.ObjectModel;
using System.Linq;
using System.Text;
using Org.Apache.Qpid.Messaging;

namespace Org.Apache.Qpid.Messaging.Examples
{
    class MapSender
    {
        //
        // usage
        //
        static void usage(string url, string addr, UInt32 count)
        {

            Console.WriteLine("usage: {0} [url  [addr [count]]]",
                System.Diagnostics.Process.GetCurrentProcess().ProcessName);
            Console.WriteLine();
            Console.WriteLine("A program to connect to a broker and send N");
            Console.WriteLine("messages to a named exchange with a routing key.");
            Console.WriteLine();
            Console.WriteLine(" url = target address for 'new Connection(url)'");
            Console.WriteLine(" addr = address for 'session.CreateReceiver(addr)'");
            Console.WriteLine(" count = number of messages to send");
            Console.WriteLine();
            Console.WriteLine("Default values:");
            Console.WriteLine("  {0} {1} {2} {3}",
                System.Diagnostics.Process.GetCurrentProcess().ProcessName,
                url, addr, count);
        }

        
        //
        // TestProgram
        //
        public void TestProgram(string[] args)
        {
            string url = "amqp:tcp:localhost:5672";
            string addr = "amq.direct/map_example";
            UInt32 count = 1;

            if (1 == args.Length)
            {
                if (args[0].Equals("-h") || args[0].Equals("-H") || args[0].Equals("/?"))
                {
                    usage(url, addr, count);
                    return;
                }
            }

            if (args.Length > 0)
                url = args[0];
            if (args.Length > 1)
                addr = args[1];
            if (args.Length > 2)
                count = System.Convert.ToUInt32(args[2]);

            
            //
            // Create and open an AMQP connection to the broker URL
            //
            Connection connection = new Connection(url);
            connection.Open();

            //
            // Create a session and a sender to the direct exchange using the
            // routing key "map_example".
            //
            Session session = connection.CreateSession();
            Sender sender = session.CreateSender(addr);

            //
            // Create structured content for the message.  This example builds a
            // map of items including a nested map and a list of values.
            //
            Dictionary<string, object> content = new Dictionary<string, object>();
            Dictionary<string, object> subMap = new Dictionary<string, object>();
            Collection<object> colors = new Collection<object>();

            content["id"] = 987654321;
            content["name"] = "Widget";
            content["percent"] = 0.99;

            subMap["name"] = "Smith";
            subMap["number"] = 354;

            content["nested"] = subMap;

            colors.Add("red");
            colors.Add("green");
            colors.Add("white");

            content["colors"] = colors;

            //
            // Construct a message with the map content and send it synchronously
            // via the sender.
            //
            Message message = new Message(content);
            for (UInt32 i = 0; i<count; i++)
                sender.Send(message, true);

            //
            // Close the connection.
            //
            connection.Close();
        }
    }

    class MapSenderMain
    {
        //
        // Main
        //
        static void Main(string[] args)
        {
            // Invoke 'TestProgram' as non-static class.
            MapSender mainProc = new MapSender();

            mainProc.TestProgram(args);

        }
    }
}
