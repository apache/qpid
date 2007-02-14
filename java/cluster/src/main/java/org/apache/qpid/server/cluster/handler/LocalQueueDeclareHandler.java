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
package org.apache.qpid.server.cluster.handler;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.cluster.ClusteredProtocolSession;
import org.apache.qpid.server.cluster.GroupManager;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.server.cluster.MemberHandle;
import org.apache.qpid.server.handler.QueueDeclareHandler;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.ClusteredQueue;
import org.apache.qpid.server.queue.PrivateQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.RemoteQueueProxy;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class LocalQueueDeclareHandler extends QueueDeclareHandler
{
    private static final Logger _logger = Logger.getLogger(LocalQueueDeclareHandler.class);
    private final GroupManager _groupMgr;

    LocalQueueDeclareHandler(GroupManager groupMgr)
    {
        _groupMgr = groupMgr;
    }

    protected AMQShortString createName()
    {
        return new AMQShortString(super.createName().toString() + "@" + _groupMgr.getLocal().getDetails());
    }

    protected AMQQueue createQueue(QueueDeclareBody body, VirtualHost virtualHost, AMQProtocolSession session) throws AMQException
    {
        //is it private or shared:
        if (body.exclusive)
        {
            if (ClusteredProtocolSession.isPeerSession(session))
            {
                //need to get peer from the session...
                MemberHandle peer = ClusteredProtocolSession.getSessionPeer(session);
                _logger.debug(new LogMessage("Creating proxied queue {0} on behalf of {1}", body.queue, peer));
                return new RemoteQueueProxy(peer, _groupMgr, body.queue, body.durable, new AMQShortString(peer.getDetails()), body.autoDelete, virtualHost);
            }
            else
            {
                _logger.debug(new LogMessage("Creating local private queue {0}", body.queue));
                return new PrivateQueue(_groupMgr, body.queue, body.durable, session.getContextKey(), body.autoDelete, virtualHost);
            }
        }
        else
        {
            _logger.debug(new LogMessage("Creating local shared queue {0}", body.queue));
            return new ClusteredQueue(_groupMgr, body.queue, body.durable, null, body.autoDelete, virtualHost);
        }
    }
}
