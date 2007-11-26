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
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;

/**
 * Generates queue names for queues declared with no name.
 *
 */
class QueueNameGenerator extends ClusterMethodHandler<QueueDeclareBody>
{
    private final LocalQueueDeclareHandler _handler;

    QueueNameGenerator(LocalQueueDeclareHandler handler)
    {
        _handler = handler;
    }

    protected void peer(AMQStateManager stateMgr, AMQMethodEvent<QueueDeclareBody> evt) throws AMQException
    {
    }

    protected void client(AMQStateManager stateMgr,  AMQMethodEvent<QueueDeclareBody> evt)
            throws AMQException
    {
        setName(evt.getMethod());//need to set the name before propagating this method
    }

    protected void setName(QueueDeclareBody body)
    {
        if (body.queue == null)
        {
            body.queue = _handler.createName();
        }
    }
}

