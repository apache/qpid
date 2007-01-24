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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicAckBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.log4j.Logger;

public class BasicAckMethodHandler implements StateAwareMethodListener<BasicAckBody>
{
    private static final Logger _log = Logger.getLogger(BasicAckMethodHandler.class);

    private static final BasicAckMethodHandler _instance = new BasicAckMethodHandler();

    public static BasicAckMethodHandler getInstance()
    {
        return _instance;
    }

    private BasicAckMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<BasicAckBody> evt) throws AMQException
    {
        AMQProtocolSession protocolSession = stateManager.getProtocolSession();
        
        if (_log.isDebugEnabled())
        {
            _log.debug("Ack received on channel " + evt.getChannelId());
        }
        BasicAckBody body = evt.getMethod();
        final AMQChannel channel = protocolSession.getChannel(evt.getChannelId());
        // this method throws an AMQException if the delivery tag is not known
        channel.acknowledgeMessage(body.deliveryTag, body.multiple);
    }
}
