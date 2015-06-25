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
using System.Diagnostics;
using System.Collections;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using Org.Apache.Qpid.Messaging;

namespace Org.Apache.Qpid.Messaging.Examples {
    class Spout {
        //
        // Sample invocation: csharp.example.spout.exe --broker localhost:5672 my-queue
        // 
        static bool NameVal(string In, out string nameOut, out string valueOut)
        {
            int pos = In.IndexOf("=");
            if (-1 == pos) {
                nameOut = In;
                valueOut = "";
                return false;
            } else {
                nameOut = In.Substring(0, pos);
                if (pos + 1 < In.Length) {
                    valueOut = In.Substring(pos + 1);
                    return true;
                } else {
                    valueOut = "";
                    return false;
                }
            }
        }

        static void SetEntries(Collection<string> entries, Dictionary<string, object> content)
        {
            foreach (String entry in entries)
            {
                string name = "";
                string value = "";
                if (NameVal(entry, out name, out value))
                    content.Add(name, value);
                else
                    content.Add(name, "");
            }
        }

        static int Main(string[] args) {
            Options options = new Options(args);

            Connection connection = null;
            try
            {
                connection = new Connection(options.Url, options.ConnectionOptions);
                connection.Open();
                Session session = connection.CreateSession();
                Sender sender = session.CreateSender(options.Address);
                Message message;
                if (options.Entries.Count > 0)
                {
                    Dictionary<string, object> content = new Dictionary<string, object>();
                    SetEntries(options.Entries, content);
                    message = new Message(content);
                }
                else
                {
                    message = new Message(options.Content);
                    message.ContentType = "text/plain";
                }
                Address replyToAddr = new Address(options.ReplyTo);

                Stopwatch stopwatch = new Stopwatch();
                TimeSpan timespan = new TimeSpan(0,0,options.Timeout);
                stopwatch.Start();
                for (int count = 0;
                    (0 == options.Count || count < options.Count) &&
                    (0 == options.Timeout || stopwatch.Elapsed <= timespan);
                    count++) 
                {
                    if ("" != options.ReplyTo) message.ReplyTo = replyToAddr;
                    string id = options.Id ;
                    if ("" == id) {
                        Guid g = Guid.NewGuid();
                        id = g.ToString();
                    }
                    string spoutid = id + ":" + count;
                    message.SetProperty("spout-id", spoutid);
                    sender.Send(message);
                }
                session.Sync();
                connection.Close();
                return 0;
            } catch (Exception e) {
                Console.WriteLine("Exception {0}.", e);
                if (null != connection)
                    connection.Close();
            }
            return 1;
        }
    }
}

