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

import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.*;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.HashMap;

/**
 * Maintains the default queue names for a channel, and alters subsequent frames where necessary
 * to use this (i.e. when no queue is explictly specified).
 *
 */
class ChannelQueueManager
{
    private static final Logger _logger = Logger.getLogger(ChannelQueueManager.class);
    private final Map<Integer, AMQShortString> _channelQueues = new HashMap<Integer, AMQShortString>();

    ClusterMethodHandler<QueueDeclareBody> createQueueDeclareHandler()
    {
        return new QueueDeclareHandler();
    }

    ClusterMethodHandler<QueueDeleteBody> createQueueDeleteHandler()
    {
        return new QueueDeleteHandler();
    }

    ClusterMethodHandler<QueueBindBody> createQueueBindHandler()
    {
        return new QueueBindHandler();
    }

    ClusterMethodHandler<BasicConsumeBody> createBasicConsumeHandler()
    {
        return new BasicConsumeHandler();
    }

    private void set(int channel, AMQShortString queue)
    {
        _channelQueues.put(channel, queue);
        _logger.info(new LogMessage("Set default queue for {0} to {1}", channel, queue));
    }

    private AMQShortString get(int channel)
    {
        AMQShortString queue = _channelQueues.get(channel);
        _logger.info(new LogMessage("Default queue for {0} is {1}", channel, queue));
        return queue;
    }

    private class QueueDeclareHandler extends ClusterMethodHandler<QueueDeclareBody>
    {
        protected void peer(AMQStateManager stateMgr, AMQMethodEvent<QueueDeclareBody> evt) throws AMQException
        {
        }

        protected void client(AMQStateManager stateMgr, AMQMethodEvent<QueueDeclareBody> evt) throws AMQException
        {
            set(evt.getChannelId(), evt.getMethod().queue);
        }
    }
    private class QueueBindHandler extends ClusterMethodHandler<QueueBindBody>
    {
        protected void peer(AMQStateManager stateMgr, AMQMethodEvent<QueueBindBody> evt) throws AMQException
        {
        }

        protected void client(AMQStateManager stateMgr, AMQMethodEvent<QueueBindBody> evt) throws AMQException
        {
            if(evt.getMethod().queue == null)
            {
                evt.getMethod().queue = get(evt.getChannelId());
            }
        }
    }
    private class QueueDeleteHandler extends ClusterMethodHandler<QueueDeleteBody>
    {
        protected void peer(AMQStateManager stateMgr, AMQMethodEvent<QueueDeleteBody> evt) throws AMQException
        {
        }

        protected void client(AMQStateManager stateMgr, AMQMethodEvent<QueueDeleteBody> evt) throws AMQException
        {
            if(evt.getMethod().queue == null)
            {
                evt.getMethod().queue = get(evt.getChannelId());
            }
        }
    }

    private class BasicConsumeHandler extends ClusterMethodHandler<BasicConsumeBody>
    {
        protected void peer(AMQStateManager stateMgr,  AMQMethodEvent<BasicConsumeBody> evt) throws AMQException
        {
        }

        protected void client(AMQStateManager stateMgr, AMQMethodEvent<BasicConsumeBody> evt) throws AMQException
        {
            if(evt.getMethod().queue == null)
            {
                evt.getMethod().queue = get(evt.getChannelId());
            }
        }
    }

}
