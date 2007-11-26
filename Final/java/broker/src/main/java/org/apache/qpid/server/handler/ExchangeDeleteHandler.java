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
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ExchangeDeleteBody;
import org.apache.qpid.framing.ExchangeDeleteOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.exchange.ExchangeInUseException;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class ExchangeDeleteHandler implements StateAwareMethodListener<ExchangeDeleteBody>
{
    private static final ExchangeDeleteHandler _instance = new ExchangeDeleteHandler();

    public static ExchangeDeleteHandler getInstance()
    {
        return _instance;
    }

    private ExchangeDeleteHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ExchangeDeleteBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHost virtualHost = session.getVirtualHost();
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();

        ExchangeDeleteBody body = evt.getMethod();
        try
        {
            exchangeRegistry.unregisterExchange(body.exchange, body.ifUnused);
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            AMQFrame response = ExchangeDeleteOkBody.createAMQFrame(evt.getChannelId(), (byte)8, (byte)0);
            session.writeFrame(response);
        }
        catch (ExchangeInUseException e)
        {
            // TODO: sort out consistent channel close mechanism that does all clean up etc.
        }

    }
}
