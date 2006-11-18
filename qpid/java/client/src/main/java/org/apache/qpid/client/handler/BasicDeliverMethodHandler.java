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
package org.apache.qpid.client.handler;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicDeliverBody;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.client.protocol.AMQMethodEvent;
import org.apache.qpid.client.message.UnprocessedMessage;

public class BasicDeliverMethodHandler implements StateAwareMethodListener
{
    private static final Logger _logger = Logger.getLogger(BasicDeliverMethodHandler.class);

    private static final BasicDeliverMethodHandler _instance = new BasicDeliverMethodHandler();

    public static BasicDeliverMethodHandler getInstance()
    {
        return _instance;
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent evt) throws AMQException
    {
        final UnprocessedMessage msg = new UnprocessedMessage();
        msg.deliverBody = (BasicDeliverBody) evt.getMethod();
        msg.channelId = evt.getChannelId();
        _logger.debug("New JmsDeliver method received");
        evt.getProtocolSession().unprocessedMessageReceived(msg);
    }
}
