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
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ChannelFlowBody;
import org.apache.qpid.framing.ChannelFlowOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

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

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ChannelFlowBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        ChannelFlowBody body = evt.getMethod();

        AMQChannel channel = session.getChannel(evt.getChannelId());

        if (channel == null)
        {
            throw body.getChannelNotFoundException(evt.getChannelId());
        }

        channel.setSuspended(!body.active);
        _logger.debug("Channel.Flow for channel " + evt.getChannelId() + ", active=" + body.active);

        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        AMQFrame response = ChannelFlowOkBody.createAMQFrame(evt.getChannelId(),
            (byte)8, (byte)0,	// AMQP version (major, minor)
            body.active);	// active
        session.writeFrame(response);
    }
}
