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

    class Drain
    {
        // delimit multiple values
        private static void Append(StringBuilder sb, string s)
        {
            if (sb.Length > 0)
            {
                sb.Append(", ");
            }

            sb.Append(s);
        }

        private static string MessagePropertiesAsString(AmqpProperties props)
        {
            StringBuilder sb = new StringBuilder();

            if (props.PropertyMap != null)
            {
                foreach (KeyValuePair<string, AmqpType> kvp in props.PropertyMap)
                {
                    string propval;
                    if (kvp.Value is AmqpString)
                    {
                        AmqpString amqps = (AmqpString)kvp.Value;
                        propval = amqps.Value;
                    }
                    else
                    {
                        propval = kvp.Value.ToString();
                    }

                    Append(sb, kvp.Key + ":" + propval);
                }
            }

            return sb.ToString();
        }

        private static string MessageContentAsString(Message msg, AmqpProperties props)
        {
            // AmqpBinaryBinding provides message content as a single XML "Binary" element
            XmlDictionaryReader reader = msg.GetReaderAtBodyContents();
            while (!reader.HasValue)
            {
                reader.Read();
                if (reader.EOF)
                {
                    throw new InvalidDataException("empty reader for message");
                }
            }

            byte[] rawdata = reader.ReadContentAsBase64();

            string ct = props.ContentType;
            if (ct != null)
            {
                if (ct.Equals("amqp/map"))
                {
                    return "mapdata (coming soon)";
                }
            }

            return Encoding.UTF8.GetString(rawdata);
        }

        static void Main(string[] args)
        {
            try
            {
                Options options = new Options(args);

                AmqpBinaryBinding binding = new AmqpBinaryBinding();
                binding.BrokerHost = options.Broker;
                binding.BrokerPort = options.Port;
                binding.TransferMode = TransferMode.Streamed;

                IChannelFactory<IInputChannel> factory = binding.BuildChannelFactory<IInputChannel>();

                factory.Open();
                try
                {
                    System.ServiceModel.EndpointAddress addr = options.Address;
                    IInputChannel receiver = factory.CreateChannel(addr);
                    receiver.Open();

                    TimeSpan timeout = options.Timeout;
                    System.ServiceModel.Channels.Message message;

                    while (receiver.TryReceive(timeout, out message))
                    {
                        AmqpProperties props = (AmqpProperties)message.Properties["AmqpProperties"];

                        Console.WriteLine("Message(properties=" + 
                            MessagePropertiesAsString(props) + 
                            ", content='" + 
                            MessageContentAsString(message, props) +
                            "')");
                    }
                }
                finally
                {
                    factory.Close();
                }
            }
            catch (Exception e)
            {
                Console.WriteLine("Drain: " + e);
            }
        }
    }
}
