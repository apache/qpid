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
    using System.Collections.Generic;
    using System.ServiceModel.Channels;

    public abstract class ChannelEntity
    {
        public ChannelEntity(ChannelContextParameters contextParameters, Binding channelBinding)
        {
            this.Parameters = contextParameters;
            this.Binding = channelBinding;
            this.Results = new List<string>();
        }

        protected ChannelContextParameters Parameters
        {
            get;
            set;
        }

        protected Binding Binding
        {
            get;
            set;
        }

        public List<string> Results
        {
            get;
            set;
        }

        public abstract void Run(string serviceUri);

        protected void WaitForChannel(IChannelListener listener, bool async, TimeSpan timeout)
        {
            bool ret = false;

            if (async)
            {
                IAsyncResult result = listener.BeginWaitForChannel(timeout, null, null);
                ret = listener.EndWaitForChannel(result);
            }
            else
            {
                ret = listener.WaitForChannel(timeout);
            }
            
            this.Results.Add(String.Format("WaitForChannel returned {0}", ret));
        }
    }
}
