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

    public class ChannelContextParameters
    {
        public ChannelContextParameters()
        {
            this.NumberOfMessages = 5;
            this.NumberOfThreads = 1;
            this.ReceiveTimeout = TimeSpan.FromSeconds(10.0);
            this.WaitForSender = true;
            this.UseAcceptChannelTimeout = true;
            this.CreateChannel = true;
            this.DoneSendingTimeout = TimeSpan.FromSeconds(10);
            this.TransactionScopeTimeout = TimeSpan.FromMinutes(1);
            this.AcceptChannelTimeout = TimeSpan.FromSeconds(10);
            this.OpenTimeout = TimeSpan.FromSeconds(10);
            this.ClientCommitDelay = TimeSpan.Zero;
            this.WaitForChannelTimeout = TimeSpan.FromSeconds(5);
            this.WaitForMessageTimeout = TimeSpan.FromSeconds(5);
        }

        public int NumberOfMessages
        {
            get;
            set;
        }

        public int NumberOfThreads
        {
            get;
            set;
        }

        public TimeSpan ReceiveTimeout
        {
            get;
            set;
        }

        public bool SenderShouldAbort
        {
            get;
            set;
        }

        public bool ReceiverShouldAbort
        {
            get;
            set;
        }

        public bool AsyncSend
        {
            get;
            set;
        }

        public bool AsyncReceive
        {
            get;
            set;
        }

        public bool SendWithoutTransaction
        {
            get;
            set;
        }

        public bool ReceiveWithoutTransaction
        {
            get;
            set;
        }

        public bool SendWithMultipleTransactions
        {
            get;
            set;
        }

        public bool ReceiveWithMultipleTransactions
        {
            get;
            set;
        }

        public bool CloseBeforeReceivingAll
        {
            get;
            set;
        }

        public bool WaitForSender
        {
            get;
            set;
        }

        public TimeSpan DoneSendingTimeout
        {
            get;
            set;
        }

        public TimeSpan TransactionScopeTimeout
        {
            get;
            set;
        }

        public TimeSpan AcceptChannelTimeout
        {
            get;
            set;
        }

        public TimeSpan OpenTimeout
        {
            get;
            set;
        }

        public bool UseAcceptChannelTimeout
        {
            get;
            set;
        }

        public bool CreateChannel
        {
            get;
            set;
        }

        public TimeSpan ClientCommitDelay
        {
            get;
            set;
        }

        public bool AsyncAccept
        {
            get;
            set;
        }

        public bool CloseListenerEarly
        {
            get;
            set;
        }

        public bool AbortTxDatagramAccept
        {
            get;
            set;
        }

        public bool WaitForChannel
        {
            get;
            set;
        }

        public TimeSpan WaitForChannelTimeout
        {
            get;
            set;
        }

        public bool AsyncWaitForChannel
        {
            get;
            set;
        }

        public bool WaitForMessage
        {
            get;
            set;
        }

        public TimeSpan WaitForMessageTimeout
        {
            get;
            set;
        }

        public bool AsyncWaitForMessage
        {
            get;
            set;
        }

        public bool TryReceive
        {
            get;
            set;
        }

        public bool TryReceiveNullIAsyncResult
        {
            get;
            set;
        }
    }
}
