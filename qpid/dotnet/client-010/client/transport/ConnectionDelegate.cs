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
using System.Threading;
using Logger = org.apache.qpid.transport.util.Logger;

namespace org.apache.qpid.transport
{
    /// <summary> 
    /// ConnectionDelegate
    /// 
    /// Currently only implemented client specific methods
    /// </summary>
    public abstract class ConnectionDelegate : MethodDelegate<Channel>
    {
        private static readonly Logger log = Logger.get(typeof(ConnectionDelegate));
        private String _virtualHost;

        private ManualResetEvent _negotiationComplete;

        public abstract SessionDelegate getSessionDelegate();

        public abstract void exception(Exception t);

        public abstract void closed();

        public void setCondition(ManualResetEvent negotiationComplete)
        {
            _negotiationComplete = negotiationComplete;
        }

        public virtual void init(Channel ch, ProtocolHeader hdr)
        {
            ch.Connection.send(new ProtocolHeader((byte)1, hdr.Major, hdr.Minor));
            List<Object> plain = new List<Object>();
            plain.Add("PLAIN");
            List<Object> utf8 = new List<Object>();
            utf8.Add("utf8");
            ch.connectionStart(null, plain, utf8);
        }

        public String VirtualHost
        {
            get { return _virtualHost; }
            set { _virtualHost = value; }
        }

        // ----------------------------------------------
        //           Client side
        //-----------------------------------------------
        public override void connectionStart(Channel context, ConnectionStart mstruct)
        {            
            Dictionary<String, Object> props = new Dictionary<String, Object>();
            context.connectionStartOk(props, null, null, "utf8");
        }

        public override void connectionSecure(Channel context, ConnectionSecure mstruct)
        {      // todo SASL          
            context.connectionSecureOk(new byte[0]);
        }

        public override void connectionTune(Channel context, ConnectionTune mstruct)
        {
            context.Connection.ChannelMax = mstruct.getChannelMax();
            context.connectionTuneOk(mstruct.getChannelMax(), mstruct.getMaxFrameSize(), mstruct.getHeartbeatMax());
            context.connectionOpen(_virtualHost, null, Option.INSIST);
        }

        public override void connectionOpenOk(Channel context, ConnectionOpenOk mstruct)
        {
            List<Object> knownHosts = mstruct.getKnownHosts();
            if (_negotiationComplete != null)
            {
                _negotiationComplete.Set();
            }
        }

        public override void connectionRedirect(Channel context, ConnectionRedirect mstruct)
        {
            // not going to bother at the moment
        }

        public override void connectionClose(Channel ch, ConnectionClose close)
        {
            ch.Connection.closeCode(close);
            ch.connectionCloseOk();
        }
    }
}