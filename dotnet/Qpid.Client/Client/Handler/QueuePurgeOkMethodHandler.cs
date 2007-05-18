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
using log4net;
using Qpid.Client.Message;
using Qpid.Client.Protocol;
using Qpid.Client.State;
using Qpid.Framing;

namespace Qpid.Client.Handler
{
    public class QueuePurgeOkMethodHandler : IStateAwareMethodListener
    {

        private static readonly ILog _logger = LogManager.GetLogger(typeof(QueuePurgeOkMethodHandler));

        public void MethodReceived(AMQStateManager stateManager, AMQMethodEvent evt)
        {
            QueuePurgeOkBody body = (QueuePurgeOkBody)evt.Method;
            if (body != null)
            {
                _logger.InfoFormat("Received Queue.Purge-Ok message, message count {0}", body.MessageCount);
            }
        }

    }
}
