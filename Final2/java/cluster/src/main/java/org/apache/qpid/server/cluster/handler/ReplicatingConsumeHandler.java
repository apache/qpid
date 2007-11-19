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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicConsumeBody;
import org.apache.qpid.server.cluster.BroadcastPolicy;
import org.apache.qpid.server.cluster.GroupManager;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.handler.BasicConsumeMethodHandler;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class ReplicatingConsumeHandler extends ReplicatingHandler<BasicConsumeBody>
{
    ReplicatingConsumeHandler(GroupManager groupMgr)
    {
        this(groupMgr, null);
    }

    ReplicatingConsumeHandler(GroupManager groupMgr, BroadcastPolicy policy)
    {
        super(groupMgr, base(), policy);
    }

    protected void replicate(AMQStateManager stateManager, AMQMethodEvent<BasicConsumeBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHost virtualHost = session.getVirtualHost();
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();

        //only replicate if the queue in question is a shared queue
        if (isShared(queueRegistry.getQueue(evt.getMethod().queue)))
        {
            super.replicate(stateManager, evt);
        }
        else
        {
            _logger.info(new LogMessage("Handling consume for private queue ({0}) locally", evt.getMethod()));
            local(stateManager, evt);
            _logger.info(new LogMessage("Handled consume for private queue ({0}) locally", evt.getMethod()));

        }
    }

    protected boolean isShared(AMQQueue queue)
    {
        return queue != null && queue.isShared();
    }

    static StateAwareMethodListener<BasicConsumeBody> base()
    {
        return new PeerHandler<BasicConsumeBody>(peer(), client());
    }

    static StateAwareMethodListener<BasicConsumeBody> peer()
    {
        return new RemoteConsumeHandler();
    }

    static StateAwareMethodListener<BasicConsumeBody> client()
    {
        return BasicConsumeMethodHandler.getInstance();
    }
}
