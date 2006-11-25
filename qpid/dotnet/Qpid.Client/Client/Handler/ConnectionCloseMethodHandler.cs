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
using log4net;
using Qpid.Client.Protocol;
using Qpid.Client.State;
using Qpid.Framing;

namespace Qpid.Client.Handler
{
    public class ConnectionCloseMethodHandler : IStateAwareMethodListener
    {
        private static readonly ILog _logger = LogManager.GetLogger(typeof(ConnectionCloseMethodHandler));

        public void MethodReceived(AMQStateManager stateManager, AMQMethodEvent evt)
        {
            _logger.Debug("ConnectionClose frame received");
            ConnectionCloseBody method = (ConnectionCloseBody) evt.Method;

            int errorCode = method.ReplyCode;
            String reason = method.ReplyText;

            evt.ProtocolSession.WriteFrame(ConnectionCloseOkBody.CreateAMQFrame(evt.ChannelId));
            stateManager.ChangeState(AMQState.CONNECTION_CLOSED);
            if (errorCode != 200)
            {
                _logger.Debug("Connection close received with error code " + errorCode);
                throw new AMQConnectionClosedException(errorCode, "Error: " + reason);
            }

            // this actually closes the connection in the case where it is not an error.
            evt.ProtocolSession.CloseProtocolSession();
        }
    }
}
