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
import org.apache.qpid.framing.MessageRecoverBody;
import org.apache.qpid.framing.MessageOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

import org.apache.log4j.Logger;

public class MessageRecoverHandler implements StateAwareMethodListener<MessageRecoverBody>
{
    private static final Logger _logger = Logger.getLogger(MessageRecoverHandler.class);

    private static MessageRecoverHandler _instance = new MessageRecoverHandler();

    public static MessageRecoverHandler getInstance()
    {
        return _instance;
    }

    private MessageRecoverHandler() {}

    public void methodReceived (AMQStateManager stateManager, AMQMethodEvent<MessageRecoverBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        AMQChannel channel = session.getChannel(evt.getChannelId());
        _logger.debug("Recover received on protocol session " + session + " and channel " + evt.getChannelId());
        if (channel == null)
        {
            throw new AMQException("Unknown channel " + evt.getChannelId());
        }
        MessageRecoverBody body = evt.getMethod();
        if (body.requeue)
        {
            channel.requeue();
        }
        else
        {
            channel.resend(session, false);
        }
        MessageOkBody response = MessageOkBody.createMethodBody(
            session.getProtocolMajorVersion(), // AMQP major version
            session.getProtocolMinorVersion()); // AMQP minor version
        session.writeResponse(evt, response);
    }
}

