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

import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.BasicQosBody;
import org.apache.qpid.framing.BasicQosOkBody;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.AMQException;

public class BasicQosHandler implements StateAwareMethodListener<BasicQosBody>
{
    private static final BasicQosHandler _instance = new BasicQosHandler();

    public static BasicQosHandler getInstance()
    {
        return _instance;
    }

    public void methodReceived(AMQStateManager stateMgr, QueueRegistry queues, ExchangeRegistry exchanges,
                               AMQProtocolSession session, AMQMethodEvent<BasicQosBody> evt) throws AMQException
    {
        session.getChannel(evt.getChannelId()).setPrefetchCount(evt.getMethod().prefetchCount);
        session.writeFrame(new AMQFrame(evt.getChannelId(), new BasicQosOkBody()));
    }
}
