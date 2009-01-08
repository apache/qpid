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
using System.IO;
using System.Threading;
using System.Xml;
using common.org.apache.qpid.transport.util;
using log4net.Config;
using NUnit.Framework;
using org.apache.qpid.client;
using org.apache.qpid.transport;
using org.apache.qpid.transport.util;

namespace test.interop
{
    [TestFixture]

    public class TestCase
    {       
        private readonly Dictionary<string,string> _properties = new Dictionary<string, string>();
        private  Client _client;

        [TestFixtureSetUp] 
        public void Init()
        {
            XmlConfigurator.Configure(new FileInfo("/log.xml"));
            // populate default properties
            _properties.Add("UserName", "guest");
            _properties.Add("Password", "guest");
            _properties.Add("Host", "localhost");
            _properties.Add("Port", "5672");
            _properties.Add("VirtualHost", "test");
             //Read the test config file  
            XmlTextReader reader = new XmlTextReader(Environment.CurrentDirectory + "/test.config");
            while (reader.Read())
            {                
                // if node type is an element
                if (reader.NodeType == XmlNodeType.Element && reader.Name.Equals("add"))
                {
                    if (_properties.ContainsKey(reader.GetAttribute("key")))
                    {
                        _properties[reader.GetAttribute("key")] = reader.GetAttribute("value");
                    }
                    else
                    {
                        _properties.Add(reader.GetAttribute("key"), reader.GetAttribute("value"));    
                    }
                    
                }               
            }
            // create a client and connect to the broker
            _client = new Client();
            _client.connect(Properties["Host"], Convert.ToInt16(Properties["Port"]), Properties["VirtualHost"],
                           Properties["UserName"], Properties["Password"]);           
   
        }

        [TestFixtureTearDown]
        public void Cleanup()
        {
         _client.close();
        }

        public Client Client
        {
            get{ return _client;}
        }

        public Dictionary<string,string> Properties
        {
            get { return _properties; }
        }


        public class SyncListener : IMessageListener
        {
            private static readonly Logger _log = Logger.get(typeof(SyncListener));
            private readonly CircularBuffer<IMessage> _buffer;
            private readonly RangeSet _range = new RangeSet();
            private readonly ClientSession _session;

            public SyncListener(ClientSession session, CircularBuffer<IMessage> buffer)
            {
                _buffer = buffer;
                _session = session;
            }

            public void messageTransfer(IMessage m)
            {
                _range.clear();
                _range.add(m.Id);
                _session.messageAccept(_range);
                _buffer.Enqueue(m);
            }
        }
    }
}
