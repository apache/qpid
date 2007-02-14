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
package org.apache.qpid.client.handler;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.QueueDeleteOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;

import org.apache.log4j.Logger;

public class QueueDeleteOkMethodHandler implements StateAwareMethodListener
{
    private static final Logger _logger = Logger.getLogger(QueueDeleteOkMethodHandler.class);

    private static final QueueDeleteOkMethodHandler _instance = new QueueDeleteOkMethodHandler();

    public static QueueDeleteOkMethodHandler getInstance()
    {
        return _instance;
    }

    private QueueDeleteOkMethodHandler() {}

    public void methodReceived(AMQStateManager stateManager, AMQProtocolSession protocolSession, AMQMethodEvent evt) throws AMQException
    {
        if (_logger.isDebugEnabled())
        {
            QueueDeleteOkBody body = (QueueDeleteOkBody) evt.getMethod();
            _logger.debug("Received Queue.Delete-Ok message, message count: " + body.messageCount);
        }
    }
}

