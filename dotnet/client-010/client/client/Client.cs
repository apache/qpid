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
using System.Text;
using System.Threading;
using org.apache.qpid.transport;
using org.apache.qpid.transport.network.io;
using org.apache.qpid.transport.util;

namespace org.apache.qpid.client
{
    public class Client : ClientInterface
    {
        private Connection _conn;
        private ClientSession _session; 
        private static readonly Logger _log = Logger.get(typeof (Client));
        private const long timeout = 60000;
        private bool _closed;
        private readonly Object _closeOK;
        private ClosedListener _closedListner;

        public bool Closed
        {
            get { return _closed; }
            set { _closed = value; }
        }

        public Object CloseOk
        {
            get { return _closeOK; }
        }

        public Client()
        {
            _closed = false;
            _closeOK = new object();
        }

        #region Interface ClientInterface

        public void connect(String host, int port, String virtualHost, String username, String password)
        {
            _log.debug(String.Format("Client Connecting to host {0}; port {1}; virtualHost {2}; username {3}", host,
                                     port, virtualHost, username));
            ConnectionDelegate connectionDelegate = new ClientConnectionDelegate(this);
            ManualResetEvent negotiationComplete = new ManualResetEvent(false);
            connectionDelegate.setCondition(negotiationComplete);
            connectionDelegate.VirtualHost = virtualHost;
            _conn = IoTransport.connect(host, port, connectionDelegate);
            
            _conn.send(new ProtocolHeader(1, 0, 10));
            negotiationComplete.WaitOne();
        }


        public void close()
        {
            Channel ch = _conn.getChannel(0);
            ch.connectionClose(ConnectionCloseCode.NORMAL, "client is closing");
            lock (CloseOk)
            {
                long start = DateTime.Now.Millisecond;
                long elapsed = 0;
                while (!Closed && elapsed < timeout)
                {
                    Monitor.Wait(CloseOk, (int) (timeout - elapsed));
                    elapsed = DateTime.Now.Millisecond - start;
                }
                if (!Closed)
                {
                    throw new Exception("Timed out when closing connection");
                }
                _conn.close();
            }
        }

        public ClientSession createSession(long expiryInSeconds)
        {
            Channel ch = _conn.getChannel();
            ClientSession ssn = new ClientSession(Encoding.UTF8.GetBytes(UUID.randomUUID().ToString()));
            ssn.attach(ch);
            ssn.sessionAttach(ssn.getName());
            ssn.sessionRequestTimeout(expiryInSeconds);
            return ssn;
        }

        public ClosedListener ClosedListener
        {
            set { _closedListner = value; }
            get { return _closedListner; }
        }       

        #endregion
    }
}
