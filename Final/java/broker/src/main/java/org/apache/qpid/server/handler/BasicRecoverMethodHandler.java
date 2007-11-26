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
import org.apache.qpid.framing.BasicRecoverBody;
import org.apache.qpid.framing.BasicRecoverOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

public class BasicRecoverMethodHandler implements StateAwareMethodListener<BasicRecoverBody>
{
    private static final Logger _logger = Logger.getLogger(BasicRecoverMethodHandler.class);

    private static final BasicRecoverMethodHandler _instance = new BasicRecoverMethodHandler();

    public static BasicRecoverMethodHandler getInstance()
    {
        return _instance;
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<BasicRecoverBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();

        _logger.debug("Recover received on protocol session " + session + " and channel " + evt.getChannelId());
        AMQChannel channel = session.getChannel(evt.getChannelId());
        BasicRecoverBody body = evt.getMethod();

        if (channel == null)
        {
            throw body.getChannelNotFoundException(evt.getChannelId());
        }

        channel.resend(body.requeue);

        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        session.writeFrame(BasicRecoverOkBody.createAMQFrame(evt.getChannelId(), (byte) 8, (byte) 0));
    }
}
