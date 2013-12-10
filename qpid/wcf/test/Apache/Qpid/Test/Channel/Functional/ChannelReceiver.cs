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

    public class ChannelReceiver : ChannelEntity
    {
        public ChannelReceiver(ChannelContextParameters contextParameters, Binding channelBinding)
            : base(contextParameters, channelBinding)
        {
        }

        public override void Run(string listenUri)
        {            
            IChannelListener<IInputChannel> listener = this.Binding.BuildChannelListener<IInputChannel>(new Uri(listenUri));
            listener.Open();

            if (this.Parameters.WaitForChannel)
            {
                this.WaitForChannel(listener, this.Parameters.AsyncWaitForChannel, this.Parameters.WaitForChannelTimeout);
            }

            this.AcceptChannelAndReceive(listener);

            if (listener.State != CommunicationState.Closed)
            {
                listener.Close();
            }
        }

        private void AcceptChannelAndReceive(IChannelListener<IInputChannel> listener)
        {
            IInputChannel channel;
            TransactionScope transactionToAbortOnAccept = null;

            if (this.Parameters.AbortTxDatagramAccept)
            {
                transactionToAbortOnAccept = new TransactionScope(TransactionScopeOption.RequiresNew);
            }

            if (this.Parameters.AsyncAccept)
            {
                IAsyncResult result = listener.BeginAcceptChannel(null, null);
                channel = listener.EndAcceptChannel(result);
            }
            else
            {
                channel = listener.AcceptChannel();
            }

            if (this.Parameters.AbortTxDatagramAccept)
            {                
                transactionToAbortOnAccept.Dispose();
            }

            channel.Open();
            Message message;

            if (this.Parameters.CloseListenerEarly)
            {
                listener.Close();
            }

            try
            {
                using (TransactionScope ts = new TransactionScope(TransactionScopeOption.RequiresNew))
                {
                    Message firstMessage = channel.Receive(this.Parameters.ReceiveTimeout);

                    lock (this.Results)
                    {                        
                        this.Results.Add(String.Format("Received message with Action '{0}'", firstMessage.Headers.Action));
                    }

                    ts.Complete();
                }
            }
            catch (TimeoutException)
            {
                lock (this.Results)
                {
                    this.Results.Add("Receive timed out.");
                }
                
                channel.Abort();
                return;
            }

            AutoResetEvent doneReceiving = new AutoResetEvent(false);
            int threadsCompleted = 0;

            for (int i = 0; i < this.Parameters.NumberOfThreads; ++i)
            {
                ThreadPool.QueueUserWorkItem(new WaitCallback(delegate(object unused)
                {
                    do
                    {
                        if (this.Parameters.ReceiverShouldAbort)
                        {
                            this.ReceiveMessage(channel, false);
                            Thread.Sleep(200);
                        }

                        message = this.ReceiveMessage(channel, true);
                    }
                    while (message != null);
                    
                    if (Interlocked.Increment(ref threadsCompleted) == this.Parameters.NumberOfThreads)
                    {
                        doneReceiving.Set();
                    }
                }));
            }

            TimeSpan threadTimeout = TimeSpan.FromMinutes(2.0);
            if (!doneReceiving.WaitOne(threadTimeout, false))
            {
                this.Results.Add(String.Format("Threads did not complete within {0}.", threadTimeout));
            }

            channel.Close();
        }

        private Message ReceiveMessage(IInputChannel channel, bool commit)
        {
            Message message = null;

            using (TransactionScope ts = new TransactionScope(TransactionScopeOption.Required))
            {
                bool messageDetected = false;
                if (this.Parameters.AsyncWaitForMessage)
                {
                    IAsyncResult result = channel.BeginWaitForMessage(this.Parameters.WaitForMessageTimeout, null, null);
                    messageDetected = channel.EndWaitForMessage(result);
                }
                else
                {
                    messageDetected = channel.WaitForMessage(this.Parameters.WaitForMessageTimeout);
                }

                if (this.Parameters.WaitForMessage)
                {
                    lock (this.Results)
                    {
                        this.Results.Add(String.Format("WaitForMessage returned {0}", messageDetected));
                    }                    
                }

                if (messageDetected)
                {
                    if (this.Parameters.AsyncReceive)
                    {
                        if (this.Parameters.TryReceive)
                        {
                            IAsyncResult result = channel.BeginTryReceive(this.Parameters.ReceiveTimeout, null, null);
                            bool ret = channel.EndTryReceive(result, out message);

                            lock (this.Results)
                            {
                                this.Results.Add(String.Format("TryReceive returned {0}", ret));
                            }                            
                        }
                        else
                        {
                            try
                            {
                                IAsyncResult result = channel.BeginReceive(this.Parameters.ReceiveTimeout, null, null);
                                message = channel.EndReceive(result);
                            }
                            catch (TimeoutException)
                            {
                                message = null;
                            }
                        }
                    }
                    else
                    {
                        if (this.Parameters.TryReceive)
                        {
                            bool ret = channel.TryReceive(this.Parameters.ReceiveTimeout, out message);                            

                            lock (this.Results)
                            {
                                this.Results.Add(String.Format("TryReceive returned {0}", ret));
                            }
                        }
                        else
                        {
                            try
                            {
                                message = channel.Receive(this.Parameters.ReceiveTimeout);
                            }
                            catch (TimeoutException)
                            {
                                message = null;
                            }
                        }
                    }
                }
                else
                {
                    if (this.Parameters.TryReceive)
                    {
                        bool ret = false;
                        if (this.Parameters.AsyncReceive)
                        {
                            IAsyncResult result = channel.BeginTryReceive(this.Parameters.ReceiveTimeout, null, null);
                            if (this.Parameters.TryReceiveNullIAsyncResult)
                            {
                                try
                                {
                                    channel.EndTryReceive(null, out message);
                                }
                                catch (Exception e)
                                {
                                    lock (this.Results)
                                    {
                                        this.Results.Add(String.Format("TryReceive threw {0}", e.GetType().Name));
                                    }                                    
                                }
                            }

                            ret = channel.EndTryReceive(result, out message);
                        }
                        else
                        {
                            ret = channel.TryReceive(this.Parameters.ReceiveTimeout, out message);
                        }

                        lock (this.Results)
                        {
                            this.Results.Add(String.Format("TryReceive returned {0}", ret));
                            this.Results.Add(String.Format("Message was {0}", (message == null ? "null" : "not null")));
                        }
                    }

                    message = null;
                }

                if (commit && message != null)
                {
                    lock (this.Results)
                    {
                        this.Results.Add(String.Format("Received message with Action '{0}'", message.Headers.Action));
                    }

                    ts.Complete();
                }
                else
                {
                    Transaction.Current.Rollback();
                }
            }

            return message;
        }
    }
}
