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
using Org.Apache.Qpid.Messaging;

namespace Org.Apache.Qpid.Messaging.examples
{
    class MapSender
    {
        // csharp.map.sender example
        //
        // Send an amqp/map message to amqp:tcp:localhost:5672 amq.direct/map_example
        // The map message contains simple types, a nested amqp/map,
        // an ampq/list, and specific instances of each supported type.
        //
        static int Main(string[] args)
        {
            string url = "amqp:tcp:localhost:5672";
            string address = "message_queue; {create: always}";
            string connectionOptions = "";

            if (args.Length > 0)
                url = args[0];
            if (args.Length > 1)
                address = args[1];
            if (args.Length > 2)
                connectionOptions = args[2];

            //
            // Create and open an AMQP connection to the broker URL
            //
            Connection connection = new Connection(url, connectionOptions);
            connection.Open();

            //
            // Create a session and a sender to the direct exchange
            //
            Session session = connection.CreateSession();
            Sender sender = session.CreateSender(address);

            //
            // Create structured content for the message.  This example builds a
            // map of items including a nested map and a list of values.
            //
            Dictionary<string, object> content = new Dictionary<string, object>();
            Dictionary<string, object> subMap = new Dictionary<string, object>();
            Collection<object> colors = new Collection<object>();

            // add simple types
            content["id"] = 987654321;
            content["name"] = "Widget";
            content["percent"] = 0.99;

            // add nested amqp/map
            subMap["name"] = "Smith";
            subMap["number"] = 354;
            content["nestedMap"] = subMap;

            // add an amqp/list
            colors.Add("red");
            colors.Add("green");
            colors.Add("white");
            // list contains null value
            colors.Add(null);
            content["colorsList"] = colors;

            // add one of each supported amqp data type
            bool mybool = true;
            content["mybool"] = mybool;

            byte mybyte = 4;
            content["mybyte"] = mybyte;

            UInt16 myUInt16 = 5 ;
            content["myUInt16"] = myUInt16;

            UInt32 myUInt32 = 6;
            content["myUInt32"] = myUInt32;

            UInt64 myUInt64 = 7;
            content["myUInt64"] = myUInt64;

            char mychar = 'h';
            content["mychar"] = mychar;

            Int16 myInt16 = 9;
            content["myInt16"] = myInt16;

            Int32 myInt32 = 10;
            content["myInt32"] = myInt32;

            Int64 myInt64 = 11;
            content["myInt64"] = myInt64;

            Single mySingle = (Single)12.12;
            content["mySingle"] = mySingle;

            Double myDouble = 13.13;
            content["myDouble"] = myDouble;

            Guid myGuid = new Guid("000102030405060708090a0b0c0d0e0f");
            content["myGuid"] = myGuid;

            content["myNull"] = null;

            //
            // Construct a message with the map content and send it synchronously
            // via the sender.
            //
            Message message = new Message(content);
            sender.Send(message, true);

            //
            // Wait until broker receives all messages.
            //
            session.Sync();

            //
            // Close the connection.
            //
            connection.Close();
            return 0;
        }
    }
}
