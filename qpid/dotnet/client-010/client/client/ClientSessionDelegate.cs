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
using org.apache.qpid.transport;
using org.apache.qpid.transport.util;

namespace org.apache.qpid.client
{
    public class ClientSessionDelegate : SessionDelegate
    {
        private static readonly Logger _log = Logger.get(typeof (ClientSessionDelegate));

        //  --------------------------------------------
        //   Message methods
        // --------------------------------------------
        public override void messageTransfer(Session session, MessageTransfer xfr)
        {
            if (((ClientSession) session).MessageListeners.ContainsKey(xfr.getDestination()))
            {
                IMessageListener listener = ((ClientSession)session).MessageListeners[xfr.getDestination()];
                listener.messageTransfer( new Message(xfr));
            }
            else
            {
                _log.warn("No listener set for: {0}", xfr);
            }
        }

        public override void messageReject(Session session, MessageReject mstruct)
        {
            foreach (Range range in mstruct.getTransfers())
            {
                for (long l = range.Lower; l <= range.Upper; l++)
                {
                    _log.warn("message rejected: " + session.getCommand((int) l));
                }
            }
        }
    }
}