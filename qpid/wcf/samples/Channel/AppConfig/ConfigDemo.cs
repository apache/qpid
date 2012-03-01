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


namespace Apache.Qpid.Samples.Channel.Config
{
    using System;
    using System.Collections;
    using System.Collections.Generic;
    using System.Configuration;
    using System.Diagnostics;
    using System.IO;
    using System.ServiceModel;
    using System.ServiceModel.Channels;
    using System.ServiceModel.Description;
    using System.Threading;
    using System.Text;
    using System.Xml;
    using Apache.Qpid.Channel;

    [ServiceContract]
    public interface IMessageProcessor
    {
        [OperationContract(IsOneWay = true, Action = "Process")]
        void Process(string s);
    }

    public class DemoService : IMessageProcessor
    {
        private static ManualResetEvent messageArrived;

        public DemoService()
        {
        }

        public static ManualResetEvent MessageArrived
        {
            get
            {
                if (messageArrived == null)
                {
                    messageArrived = new ManualResetEvent(false);
                }

                return messageArrived;
            }
        }

        public void Process(string s)
        {
            Console.WriteLine("DemoService got message: {0}", s);
            MessageArrived.Set();
        }
    }


    public class ConfigDemo
    {
        static string demoQueueName = "wcf_config_demo";

        static void BindingDemo(string bindingName, string queueName)
        {
            AmqpBinding binding = new AmqpBinding(bindingName);
            
            Uri inUri = new Uri("amqp:" + queueName);
            EndpointAddress outEndpoint = new EndpointAddress("amqp:amq.direct?routingkey=" + queueName);

            ServiceHost serviceHost = new ServiceHost(typeof(DemoService));
            serviceHost.AddServiceEndpoint(typeof(IMessageProcessor), binding, inUri);
            serviceHost.Open();

            ChannelFactory<IMessageProcessor> cf = new ChannelFactory<IMessageProcessor>(binding, outEndpoint);
            cf.Open();
            IMessageProcessor proxy = cf.CreateChannel();

            // client and service are ready, so send a message
            DemoService.MessageArrived.Reset();
            Console.WriteLine("sending a message via " + bindingName);
            proxy.Process("a sample message via " + bindingName);

            // wait until it is safe to close down the service
            DemoService.MessageArrived.WaitOne();
            cf.Close();
            serviceHost.Close();
        }


        static void Main(string[] mainArgs)
        {
            BindingDemo("amqpSampleBinding", demoQueueName);
        }
    }
}
