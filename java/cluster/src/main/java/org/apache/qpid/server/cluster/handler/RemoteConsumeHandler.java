/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.cluster.handler;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicConsumeBody;
import org.apache.qpid.framing.BasicConsumeOkBody;
import org.apache.qpid.server.cluster.ClusteredProtocolSession;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.ClusteredQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

/**
 * Handles consume requests from other cluster members.
 *
 */
public class RemoteConsumeHandler implements StateAwareMethodListener<BasicConsumeBody>
{
    private final Logger _logger = Logger.getLogger(RemoteConsumeHandler.class);

    public void methodReceived(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges, AMQProtocolSession session, AMQMethodEvent<BasicConsumeBody> evt) throws AMQException
    {
        AMQQueue queue = queues.getQueue(evt.getMethod().queue);
        if (queue instanceof ClusteredQueue)
        {
            ((ClusteredQueue) queue).addRemoteSubcriber(ClusteredProtocolSession.getSessionPeer(session));
            session.writeFrame(BasicConsumeOkBody.createAMQFrame(evt.getChannelId(), evt.getMethod().queue));
        }
        else
        {
            _logger.warn("Got remote consume request for non-clustered queue: " + queue);
        }
    }
}
