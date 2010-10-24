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
    using Apache.Qpid.Channel;
    using Apache.Qpid.AmqpTypes;

    class Spout
    {
         static void Main(string[] args)
        {
            try
            {
                Options options = new Options(args);

                AmqpBinaryBinding binding = new AmqpBinaryBinding();
                binding.BrokerHost = options.Broker;
                binding.BrokerPort = options.Port;
                binding.TransferMode = TransferMode.Streamed;

                IChannelFactory<IOutputChannel> factory = binding.BuildChannelFactory<IOutputChannel>();

                factory.Open();
                try
                {
                    System.ServiceModel.EndpointAddress addr = options.Address;
                    IOutputChannel sender = factory.CreateChannel(addr);
                    sender.Open();

                    MyRawBodyWriter.Initialize(options.Content);
                    DateTime end = DateTime.Now.Add(options.Timeout);
                    System.ServiceModel.Channels.Message message;

                    for (int count = 0; ((count < options.Count) || (options.Count == 0)) &&
                        ((options.Timeout == TimeSpan.Zero) || (end.CompareTo(DateTime.Now) > 0)); count++)
                    {
                        message = Message.CreateMessage(MessageVersion.None, "", new MyRawBodyWriter());
                        AmqpProperties props = new AmqpProperties();
                        props.ContentType = "text/plain";

                        string id = Guid.NewGuid().ToString() + ":" + count;
                        props.PropertyMap.Add("spout-id", new AmqpString(id));

                        message.Properties["AmqpProperties"] = props;
                        sender.Send(message);
                    }
                }
                finally
                {
                    factory.Close();
                }
            }
            catch (Exception e)
            {
                Console.WriteLine("Spout: " + e);
            }
        }


        public class MyRawBodyWriter : BodyWriter
        {
            static byte[] body;

            public MyRawBodyWriter()
                : base(false)
            {
            }

            public static void Initialize(String content)
            {
                body = Encoding.UTF8.GetBytes(content);
            }

            // invoked by the binary encoder when the message is written
            protected override void OnWriteBodyContents(XmlDictionaryWriter writer)
            {
                writer.WriteStartElement("Binary");
                writer.WriteBase64(body, 0, body.Length);
            }
        }
    }
}
