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

import org.apache.qpid.AMQException;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.MessageCloseBody;
import org.apache.qpid.framing.MessageOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;

import org.apache.log4j.Logger;

public class MessageCloseMethodHandler implements StateAwareMethodListener
{
    private static final Logger _logger = Logger.getLogger(AMQProtocolSession.class);

    private static MessageCloseMethodHandler _instance = new MessageCloseMethodHandler();
    
    public static MessageCloseMethodHandler getInstance()
    {
        return _instance;
    }

    private MessageCloseMethodHandler() {}

    public void methodReceived (AMQStateManager stateManager, AMQProtocolSession protocolSession, AMQMethodEvent evt) throws AMQException
    {
		MessageCloseBody body = (MessageCloseBody)evt.getMethod();
		String referenceId = new String(body.getReference());
		
		protocolSession.deliverMessageToAMQSession(evt.getChannelId(), referenceId);
		_logger.debug("Method Close Body received, notify session to accept unprocessed message");

// TODO: Fix this - the MethodOks are never being sent, find a way to send them when the JMS
// Acknowledgement mode is appropriate.
        // Be aware of possible changes to parameter order as versions change.
//         final AMQMethodBody methodBody = MessageOkBody.createMethodBody(
//             protocolSession.getProtocolMajorVersion(), // AMQP major version
//             protocolSession.getProtocolMinorVersion()); // AMQP minor version
//         protocolSession.writeResponse(evt.getChannelId(), evt.getRequestId(), methodBody);
    }
}

