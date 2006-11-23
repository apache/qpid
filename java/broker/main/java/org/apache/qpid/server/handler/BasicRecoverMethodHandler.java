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
package org.apache.qpid.server.handler;

import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.framing.BasicRecoverBody;
import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;

public class BasicRecoverMethodHandler implements StateAwareMethodListener<BasicRecoverBody>
{
    private static final Logger _logger = Logger.getLogger(BasicRecoverMethodHandler.class);

    private static final BasicRecoverMethodHandler _instance = new BasicRecoverMethodHandler();

    public static BasicRecoverMethodHandler getInstance()
    {
        return _instance;
    }

    public void methodReceived(AMQStateManager stateManager, QueueRegistry queueRegistry,
                               ExchangeRegistry exchangeRegistry, AMQProtocolSession protocolSession,
                               AMQMethodEvent<BasicRecoverBody> evt) throws AMQException
    {
        _logger.debug("Recover received on protocol session " + protocolSession + " and channel " + evt.getChannelId());        
        AMQChannel channel = protocolSession.getChannel(evt.getChannelId());
        if (channel == null)
        {
            throw new AMQException("Unknown channel " + evt.getChannelId());
        }
        channel.resend(protocolSession);
    }
}
