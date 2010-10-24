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

/*
 * A simple Hello world program that sends and receives a message
 * to and from an AMQP broker.  The text content is sent as UTF8
 * in "raw" form on the wire (so that it matches the C++ client 
 * sample).
 * 
 * This program requires that the source queue exists and has
 * an explicit or implicit binding to the target.  The following
 * commands work in the default case:
 * 
 *   python qpid-config add queue my_topic_node
 *   python qpid-config bind amq.topic my_topic_node "*"
 *   
 */

namespace Apache.Qpid.Samples.Channel.HelloWorld
{
    using System;
    using System.ServiceModel;
    using System.ServiceModel.Channels;
    using System.ServiceModel.Description;
    using System.Text;
    using System.Xml;
    using Apache.Qpid.Channel;

    public class HelloWorld
    {  
        static void Main(string[] args)
        {
            String broker = "localhost";
            int port = 5672;
            String target = "amq.topic";
            String source = "my_topic_node";

            if (args.Length > 0)
            {
                broker = args[0];
            }

            if (args.Length > 1)
            {
                port = int.Parse(args[1]);
            }

            if (args.Length > 2)
            {
                target = args[2];
            }

            if (args.Length > 3)
            {
                source = args[3];
            }

            AmqpBinaryBinding binding = new AmqpBinaryBinding();
            binding.BrokerHost = broker;
            binding.BrokerPort = port;

            IChannelFactory<IInputChannel> receiverFactory = binding.BuildChannelFactory<IInputChannel>();
            receiverFactory.Open();
            IInputChannel receiver = receiverFactory.CreateChannel(new EndpointAddress("amqp:" + source));
            receiver.Open();

            IChannelFactory<IOutputChannel> senderFactory = binding.BuildChannelFactory<IOutputChannel>();
            senderFactory.Open();
            IOutputChannel sender = senderFactory.CreateChannel(new EndpointAddress("amqp:" + target));
            sender.Open();

            sender.Send(Message.CreateMessage(MessageVersion.None, "", new HelloWorldBinaryBodyWriter()));

            Message message = receiver.Receive();
            XmlDictionaryReader reader = message.GetReaderAtBodyContents();
            while (!reader.HasValue)
            {
                reader.Read();
            }

            byte[] binaryContent = reader.ReadContentAsBase64();
            string text = Encoding.UTF8.GetString(binaryContent);

            Console.WriteLine(text);

            senderFactory.Close();
            receiverFactory.Close();
        }
    }

    public class HelloWorldBinaryBodyWriter : BodyWriter
    {
        public HelloWorldBinaryBodyWriter() : base (true) {}

        protected override void OnWriteBodyContents(XmlDictionaryWriter writer)
        {
            byte[] binaryContent = Encoding.UTF8.GetBytes("Hello world!");
            writer.WriteStartElement("Binary");
            writer.WriteBase64(binaryContent, 0, binaryContent.Length);
        }
    }
}
