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

import org.apache.qpid.framing.*;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.AMQException;

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

    public void methodReceived(AMQStateManager stateManager, AccessRequestBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();

        MethodRegistry methodRegistry = session.getMethodRegistry();

        // We don't implement access control class, but to keep clients happy that expect it
        // always use the "0" ticket.
        AccessRequestOkBody response = methodRegistry.createAccessRequestOkBody(0);

        session.writeFrame(response.generateFrame(channelId));
    }
}
