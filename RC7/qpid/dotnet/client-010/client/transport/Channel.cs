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
using org.apache.qpid.transport.network;
using org.apache.qpid.transport.util;

namespace org.apache.qpid.transport
{
    /// <summary> 
    /// Channel
    /// </summary>
    public class Channel : Invoker, ProtocolDelegate<Object>
    {
        private static readonly Logger log = Logger.get(typeof (Channel));

        private readonly Connection _connection;
        private readonly int _channel;
        private readonly MethodDelegate<Channel> _methoddelegate;
        private readonly SessionDelegate _sessionDelegate;
        // session may be null
        private Session _session;

        public Channel(Connection connection, int channel, SessionDelegate sessionDelegate)
        {
            _connection = connection;
            _channel = channel;
            _methoddelegate = new ChannelDelegate();
            _sessionDelegate = sessionDelegate;
        }

        public Connection Connection
        {
            get { return _connection; }
        }

        // Invoked when a network event is received
        public void On_ReceivedEvent(object sender, ReceivedPayload<ProtocolEvent> payload)
        {
            if (payload.Payload.Channel == _channel)
            {
                payload.Payload.ProcessProtocolEvent(null, this);
            }
        }

        #region ProtocolDelegate<T>

        public void Init(Object v, ProtocolHeader hdr)
        {
            _connection.ConnectionDelegate.init(this, hdr);
        }

        public void Control(Object v, Method method)
        {
            switch (method.EncodedTrack)
            {
                case Frame.L1:
                    method.dispatch(this, _connection.ConnectionDelegate);
                    break;
                case Frame.L2:
                    method.dispatch(this, _methoddelegate);
                    break;
                case Frame.L3:
                    method.ProcessProtocolEvent(_session, _sessionDelegate);
                    break;
                default:
                    throw new Exception("unknown track: " + method.EncodedTrack);
            }
        }

        public void Command(Object v, Method method)
        {
            method.ProcessProtocolEvent(_session, _sessionDelegate);
        }

        public void Error(Object v, ProtocolError error)
        {
            throw new Exception(error.Message);
        }

        #endregion

        public void exception(Exception t)
        {
            _session.exception(t);
        }

        public void closedFromConnection()
        {
            log.debug("channel closed: ", this);
            if (_session != null)
            {
                _session.closed();
            }
        }

        public void closed()
        {
            log.debug("channel closed: ", this);
            if (_session != null)
            {
                _session.closed();
            }
            _connection.removeChannel(_channel);
        }

        public int EncodedChannel
        {
            get { return _channel; }
        }

        public Session Session
        {
            get { return _session; }
            set { _session = value; }
        }

        public void closeCode(ConnectionClose close)
        {
            if (_session != null)
            {
                _session.closeCode(close);
            }
        }

        private void emit(ProtocolEvent pevent)
        {
            pevent.Channel = _channel;
            _connection.send(pevent);
        }

        public void method(Method m)
        {
            emit(m);

            if (!m.Batch)
            {
                _connection.flush();
            }
        }

        protected override void invoke(Method m)
        {
            method(m);
        }

        public override Future invoke(Method m, Future future)
        {
            throw new Exception("UnsupportedOperation");
        }

        public String toString()
        {
            return String.Format("{0}:{1}", _connection, _channel);
        }
    }
}