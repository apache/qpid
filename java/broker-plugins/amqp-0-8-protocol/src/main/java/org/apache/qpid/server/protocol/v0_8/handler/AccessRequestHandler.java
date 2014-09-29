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
package org.apache.qpid.server.protocol.v0_8.handler;


import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AccessRequestBody;
import org.apache.qpid.framing.AccessRequestOkBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;

/**
 * @author Apache Software Foundation
 *
 *
 */
public class AccessRequestHandler implements StateAwareMethodListener<AccessRequestBody>
{
    private static final AccessRequestHandler _instance = new AccessRequestHandler();


    public static AccessRequestHandler getInstance()
    {
        return _instance;
    }

    private AccessRequestHandler()
    {
    }

    public void methodReceived(final AMQProtocolSession<?> connection,
                               AccessRequestBody body,
                               int channelId) throws AMQException
    {
        final AMQChannel channel = connection.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, connection.getMethodRegistry());
        }

        MethodRegistry methodRegistry = connection.getMethodRegistry();

        if(ProtocolVersion.v0_91.equals(connection.getProtocolVersion()) )
        {
            throw new AMQException(AMQConstant.COMMAND_INVALID, "AccessRequest not present in AMQP versions other than 0-8, 0-9");
        }

        // We don't implement access control class, but to keep clients happy that expect it
        // always use the "0" ticket.
        AccessRequestOkBody response = methodRegistry.createAccessRequestOkBody(0);
        channel.sync();
        connection.writeFrame(response.generateFrame(channelId));
    }
}
