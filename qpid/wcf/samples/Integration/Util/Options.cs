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

namespace Apache.Qpid.Samples.Integration
{
    using System;
    using System.Collections;
    using System.Collections.Generic;
    using System.Diagnostics;
    using System.IO;
    using System.ServiceModel;
    using System.ServiceModel.Channels;
    using System.ServiceModel.Description;
    using System.Text;
    using System.Xml;

    public class Options
    {
        private string broker;
        private int port;
        private int messageCount;
        private EndpointAddress address;
        private TimeSpan timeout;
        private string content;

        public Options(string[] args)
        {
            this.broker = "127.0.0.1";
            this.port = 5672;
            this.messageCount = 1;
            this.timeout = TimeSpan.FromSeconds(0);
            Parse(args);
        }

        private void Parse(string[] args)
        {
            int argCount = args.Length;
            int current = 0;
            bool typeSelected = false;

            while ((current + 1) < argCount)
            {
                string arg = args[current];
                if (arg == "--count")
                {
                    arg = args[++current];
                    int i = Int32.Parse(arg);
                    if (i >= 0)
                    {
                        this.messageCount = i;
                    }
                }
                else if (arg == "--broker")
                {
                    this.broker = args[++current];
                }
                else if (arg == "--port")
                {
                    arg = args[++current];
                    int i = int.Parse(arg);
                    if (i > 0)
                    {
                        this.port = i;
                    }
                }
                else if (arg == "--timeout")
                {
                    arg = args[++current];
                    int i = int.Parse(arg);
                    if (i > 0)
                    {
                        this.timeout = TimeSpan.FromSeconds(i);
                    }
                }

                else if (arg == "--content")
                {
                    this.content = args[++current];
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

            address = new EndpointAddress("amqp:" + args[current]);

            if (timeout < TimeSpan.FromMilliseconds(100))
            {
                // WCF timeout of 0 really means no time for even a single message transfer
                timeout = TimeSpan.FromMilliseconds(100);
            }
        }

        public EndpointAddress Address
        {
            get { return this.address; }
        }

        public string Broker
        {
            get { return this.broker; }
        }

        public string Content
        {
            get
            {
                if (content == null)
                {
                    return String.Empty;
                }
                return content;
            }
        }


        public int Count
        {
            get { return this.messageCount; }
        }

        public int Port
        {
            get { return this.port; }
        }

        public TimeSpan Timeout
        {
            get { return this.timeout; }
        }
    }
}
