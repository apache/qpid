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
package org.apache.qpid.server.handler;

import org.apache.log4j.Logger;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ChannelFlowBody;
import org.apache.qpid.framing.ChannelFlowOkBody;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.AMQException;

public class ChannelFlowHandler implements StateAwareMethodListener<ChannelFlowBody>
{
    private static final Logger _logger = Logger.getLogger(ChannelFlowHandler.class);

    private static ChannelFlowHandler _instance = new ChannelFlowHandler();

    public static ChannelFlowHandler getInstance()
    {
        return _instance;
    }

    private ChannelFlowHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, QueueRegistry queueRegistry,
                               ExchangeRegistry exchangeRegistry, AMQProtocolSession protocolSession,
                               AMQMethodEvent<ChannelFlowBody> evt) throws AMQException
    {
        ChannelFlowBody body = evt.getMethod();

        AMQChannel channel = protocolSession.getChannel(evt.getChannelId());
        channel.setSuspended(!body.active);
        _logger.debug("Channel.Flow for channel " + evt.getChannelId() + ", active=" + body.active);

        AMQFrame response = ChannelFlowOkBody.createAMQFrame(evt.getChannelId(), body.active);
        protocolSession.writeFrame(response);
    }}
