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

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.cluster.ClusteredProtocolSession;
import org.apache.qpid.AMQException;

public abstract class ClusterMethodHandler<A extends AMQMethodBody> implements StateAwareMethodListener<A>
{
    public final void methodReceived(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges, AMQProtocolSession session, AMQMethodEvent<A> evt) throws AMQException
    {
        if (ClusteredProtocolSession.isPeerSession(session))
        {
            peer(stateMgr, queues, exchanges, session, evt);
        }
        else
        {
            client(stateMgr, queues, exchanges, session, evt);
        }
    }

    protected abstract void peer(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges, AMQProtocolSession session, AMQMethodEvent<A> evt) throws AMQException;
    protected abstract void client(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges, AMQProtocolSession session, AMQMethodEvent<A> evt) throws AMQException;
}
