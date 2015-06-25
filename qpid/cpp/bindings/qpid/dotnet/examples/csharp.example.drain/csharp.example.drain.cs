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
using System.Collections;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using Org.Apache.Qpid.Messaging;

namespace Org.Apache.Qpid.Messaging.Examples {
    class Drain {
        //
        // Sample invocation: csharp.example.drain.exe --broker localhost:5672 --timeout 30 my-queue
        //
        static int Main(string[] args) {
            Options options = new Options(args);

            Connection connection = null;
            try
            {
                connection = new Connection(options.Url, options.ConnectionOptions);
                connection.Open();
                Session session = connection.CreateSession();
                Receiver receiver = session.CreateReceiver(options.Address);
                Duration timeout = options.Forever ? 
                                   DurationConstants.FORVER : 
                                   DurationConstants.SECOND * options.Timeout;
                Message message = new Message();

                while (receiver.Fetch(ref message, timeout))
                {
                    Dictionary<string, object> properties = new Dictionary<string, object>();
                    properties = message.Properties;
                    Console.Write("Message(properties={0}, content='", 
                                  message.MapAsString(properties));

                    if ("amqp/map" == message.ContentType)
                    {
                        Dictionary<string, object> content = new Dictionary<string, object>();
                        message.GetContent(content);
                        Console.Write(message.MapAsString(content));
                    }
                    else if ("amqp/list" == message.ContentType)
                    {
                        Collection<object> content = new Collection<object>();
                        message.GetContent(content);
                        Console.Write(message.ListAsString(content));
                    }
                    else
                    {
                        Console.Write(message.GetContent());
                    }
                    Console.WriteLine("')");
                    session.Acknowledge();
                }
                receiver.Close();
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
