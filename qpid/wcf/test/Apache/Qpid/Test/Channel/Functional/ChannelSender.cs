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

namespace Apache.Qpid.Test.Channel.Functional
{
    using System;
    using System.ServiceModel;
    using System.ServiceModel.Channels;
    using System.Threading;
    using System.Transactions;

    public class ChannelSender : ChannelEntity
    {
        public ChannelSender(ChannelContextParameters contextParameters, Binding channelBinding)
            : base(contextParameters, channelBinding)
        {
        }

        public override void Run(string sendTo)
        {
            IChannelFactory<IOutputChannel> factory = this.Binding.BuildChannelFactory<IOutputChannel>();
            factory.Open();            

            if (this.Parameters.CreateChannel)
            {
                IOutputChannel channel = factory.CreateChannel(new EndpointAddress(sendTo));
                this.SendMessages(channel);
            }

            factory.Close();
        }

        private void SendMessages(IOutputChannel channel)
        {
            channel.Open();

            AutoResetEvent doneSending = new AutoResetEvent(false);
            int threadsCompleted = 0;

            if (this.Parameters.NumberOfMessages > 0)
            {
                this.SendMessage(channel, "FirstMessage", true);
            }

            if (this.Parameters.NumberOfThreads == 1)
            {
                for (int j = 0; j < this.Parameters.NumberOfMessages; ++j)
                {
                    if (this.Parameters.SenderShouldAbort)
                    {
                        this.SendMessage(channel, "Message " + (j + 1), false);
                    }

                    this.SendMessage(channel, "Message " + (j + 1), true);
                }

                doneSending.Set();
            }
            else
            {
                for (int i = 0; i < this.Parameters.NumberOfThreads; ++i)
                {
                    ThreadPool.QueueUserWorkItem(new WaitCallback(delegate(object unused)
                    {
                        for (int j = 0; j < this.Parameters.NumberOfMessages / this.Parameters.NumberOfThreads; ++j)
                        {
                            if (this.Parameters.SenderShouldAbort)
                            {
                                this.SendMessage(channel, "Message", false);
                            }

                            this.SendMessage(channel, "Message", true);
                        }
                        if (Interlocked.Increment(ref threadsCompleted) == this.Parameters.NumberOfThreads)
                        {
                            doneSending.Set();
                        }
                    }));
                }
            }

            TimeSpan threadTimeout = TimeSpan.FromMinutes(2.0);
            if (!doneSending.WaitOne(threadTimeout, false))
            {
                lock (this.Results)
                {
                    this.Results.Add(String.Format("Threads did not complete within {0}.", threadTimeout));                    
                }
            }
            
            doneSending.Close();
            channel.Close();
        }

        private void SendMessage(IOutputChannel channel, string action, bool commit)
        {
            using (TransactionScope ts = new TransactionScope(TransactionScopeOption.RequiresNew))
            {
                Message message = Message.CreateMessage(MessageVersion.Default, action);

                if (this.Parameters.AsyncSend)
                {
                    IAsyncResult result = channel.BeginSend(message, null, null);
                    channel.EndSend(result);
                }
                else
                {
                    channel.Send(message);
                }

                if (commit)
                {
                    ts.Complete();
                }
                else
                {
                    Transaction.Current.Rollback();
                }
            }
        }
    }
}
