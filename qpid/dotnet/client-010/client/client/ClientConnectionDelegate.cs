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

using System;
using System.Threading;
using org.apache.qpid.transport;
using org.apache.qpid.transport.util;

namespace org.apache.qpid.client
{
    internal class ClientConnectionDelegate : ClientDelegate
    {
        private static readonly Logger log = Logger.get(typeof (ClientConnectionDelegate));
        private readonly Client _client;

        public ClientConnectionDelegate(Client client)
        {
            _client = client;
        }

        public override SessionDelegate getSessionDelegate()
        {
            return new ClientSessionDelegate();
        }

        public override void exception(Exception t)
        {
            throw t;
        }

        public override void closed()
        {
            log.debug("Delegate closed");
            lock (_client.CloseOk)
            {
                try
                {
                    _client.Closed = true;
                    Monitor.PulseAll(_client.CloseOk);
                }
                catch (Exception e)
                {
                    throw new SystemException("Error when closing client", e);
                }
            }
        }

        public new void connectionClose(Channel context, ConnectionClose connectionClose)
        {
            base.connectionClose(context, connectionClose);
            ErrorCode errorCode = ErrorCode.getErrorCode((int) connectionClose.getReplyCode());
            if (_client.ClosedListener == null && errorCode.Code != (int) QpidErrorCode.NO_ERROR)
            {
                throw new Exception ("Server closed the connection: Reason " +
                                       connectionClose.getReplyText());
            }           
                _client.ClosedListener.onClosed(errorCode, connectionClose.getReplyText(), null);                   
        }
    }
}
