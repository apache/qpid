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

namespace Org.Apache.Qpid.Messaging.Examples
{
    using System;
    using System.Collections;
    using System.Collections.Generic;
    using System.Collections.ObjectModel;

    public class Options
    {
        private string url;
        private string address;
        private int timeout;
        private int count;
        private string id;
        private string replyTo;
        private Collection<string> properties;
        private Collection<string> entries;
        private string content;
        private string connectionOptions;
        private bool forever;

        public Options(string[] args)
        {
            this.url = "amqp:tcp:127.0.0.1:5672";
            this.address = "";
            this.timeout = 0;
            this.count = 1;
            this.id = "";
            this.replyTo = "";
            properties = new Collection<string>();
            entries = new Collection<string>();
            this.content = "";
            this.connectionOptions = "";
            this.forever = false;
            Parse(args);
        }

        private void Parse(string[] args)
        {
            int argCount = args.Length;
            int current = 0;

            while ((current + 1) < argCount)
            {
                string arg = args[current];
                if (arg == "--broker")
                {
                    this.url = args[++current];
                }
                else if (arg == "--address")
                {
                    this.address = args[++current];
                }
                else if (arg == "--timeout")
                {
                    arg = args[++current];
                    int i = int.Parse(arg);
                    if (i >= 0)
                    {
                        this.timeout = i;
                    }
                }
                else if (arg == "--count")
                {
                    arg = args[++current];
                    int i = int.Parse(arg);
                    if (i >= 0)
                    {
                        this.count = i;
                    }
                }
                else if (arg == "--id")
                {
                    this.id = args[++current];
                }
                else if (arg == "--reply-to")
                {
                    this.replyTo = args[++current];
                }
                else if (arg == "--properties")
                {
                    this.properties.Add(args[++current]);
                }
                else if (arg == "--map")
                {
                    this.entries.Add(args[++current]);
                }
                else if (arg == "--content")
                {
                    this.content = args[++current];
                }
                else if (arg == "--connection-options")
                {
                    this.connectionOptions = args[++current];
                }
                else if (arg == "--forever")
                {
                    this.forever = true;
                }
                else
                {
                    throw new ArgumentException(String.Format("unknown argument \"{0}\"", arg));
                }

                current++;
            }

            if (current == argCount)
            {
                throw new ArgumentException("missing argument: address");
            }

            address = args[current];
        }

        public string Url
        {
            get { return this.url; }
        }

        public string Address
        {
            get { return this.address; }
        }

        public int Timeout
        {
            get { return this.timeout; }
        }

        public int Count
        {
            get { return this.count; }
        }

        public string Id
        {
            get { return this.id; }
        }

        public string ReplyTo
        {
            get { return this.replyTo; }
        }

        public Collection<string> Entries
        {
            get { return this.entries; }
        }

        public string Content
        {
            get { return content; }
        }

        public string ConnectionOptions
        {
            get { return this.connectionOptions; }
        }

        public bool Forever
        {
            get { return this.forever; }
        }
    }
}
