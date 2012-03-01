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

namespace Apache.Qpid.Channel
{
    using System;

    /// <summary>
    /// Specifies the types of trasport-level and message-level security used by
    /// an endpoint configured with an AmqpBinding.
    /// </summary>
    public sealed class AmqpSecurity
    {
        private AmqpSecurityMode mode;
        private AmqpTransportSecurity transportSecurity;

        internal AmqpSecurity()
        {
            this.mode = AmqpSecurityMode.None;
        }

        internal AmqpSecurity(AmqpTransportSecurity tsec)
        {
            if (tsec == null)
            {
                throw new ArgumentNullException("AmqpTransportSecurity");
            }

            this.mode = AmqpSecurityMode.Transport;
            this.transportSecurity = tsec;
        }

        /// <summary>
        /// gets or sets the security mode.
        /// </summary>
        public AmqpSecurityMode Mode
        {
            get { return this.mode; }
            set {this.mode = value; }
        }

        /// <summary>
        /// gets the security object that controls encryption
        /// and authentication parameters for the AMQP transport.
        /// </summary>
        public AmqpTransportSecurity Transport
        {
            get
            {
                if (this.transportSecurity == null)
                {
                    this.transportSecurity = new AmqpTransportSecurity();
                }

                return this.transportSecurity;
            }
        }
    }
}
