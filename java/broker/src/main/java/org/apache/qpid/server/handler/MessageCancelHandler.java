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
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.MessageCancelBody;
import org.apache.qpid.framing.MessageOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

//import org.apache.log4j.Logger;

public class MessageCancelHandler implements StateAwareMethodListener<MessageCancelBody>
{
    //private static final Logger _logger = Logger.getLogger(MessageCancelHandler.class);

    private static MessageCancelHandler _instance = new MessageCancelHandler();

    public static MessageCancelHandler getInstance()
    {
        return _instance;
    }

    private MessageCancelHandler() {}
     
    public void methodReceived (AMQStateManager stateManager, AMQMethodEvent<MessageCancelBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        final MessageCancelBody body = evt.getMethod();
        final AMQChannel channel = session.getChannel(evt.getChannelId());
        channel.unsubscribeConsumer(session, body.destination);
        
        // Be aware of possible changes to parameter order as versions change.
        final AMQMethodBody methodBody = MessageOkBody.createMethodBody(
            session.getProtocolMajorVersion(), // AMQP major version
            session.getProtocolMinorVersion()); // AMQP minor version
        session.writeResponse(evt, methodBody);
    }
}

