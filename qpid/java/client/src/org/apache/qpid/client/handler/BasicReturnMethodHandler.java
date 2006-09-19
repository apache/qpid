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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.protocol.AMQMethodEvent;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.BasicReturnBody;

public class BasicReturnMethodHandler implements StateAwareMethodListener
{
    private static final Logger _logger = Logger.getLogger(BasicReturnMethodHandler.class);

    private static final BasicReturnMethodHandler _instance = new BasicReturnMethodHandler();

    public static BasicReturnMethodHandler getInstance()
    {
        return _instance;
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent evt) throws AMQException
    {
        _logger.debug("New JmsBounce method received");
        final UnprocessedMessage msg = new UnprocessedMessage();
        msg.deliverBody = null;
        msg.bounceBody = (BasicReturnBody) evt.getMethod();
        msg.channelId = evt.getChannelId();

        evt.getProtocolSession().unprocessedMessageReceived(msg);
    }
}