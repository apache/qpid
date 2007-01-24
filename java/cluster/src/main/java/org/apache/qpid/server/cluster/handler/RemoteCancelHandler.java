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
import org.apache.qpid.framing.BasicCancelBody;
import org.apache.qpid.server.cluster.ClusteredProtocolSession;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.ClusteredQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class RemoteCancelHandler implements StateAwareMethodListener<BasicCancelBody>
{
    private final Logger _logger = Logger.getLogger(RemoteCancelHandler.class);

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<BasicCancelBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHost virtualHost = session.getVirtualHost();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();


        //By convention, consumers setup between brokers use the queue name as the consumer tag:
        AMQQueue queue = queueRegistry.getQueue(evt.getMethod().consumerTag);
        if (queue instanceof ClusteredQueue)
        {
            ((ClusteredQueue) queue).removeRemoteSubscriber(ClusteredProtocolSession.getSessionPeer(session));
        }
        else
        {
            _logger.warn("Got remote cancel request for non-clustered queue: " + queue);
        }
    }
}
