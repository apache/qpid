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
import org.apache.qpid.framing.ExchangeDeleteBody;
import org.apache.qpid.framing.ExchangeDeleteOkBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.ExchangeIsAlternateException;
import org.apache.qpid.server.virtualhost.RequiredExchangeException;
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

    public void methodReceived(AMQStateManager stateManager, ExchangeDeleteBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHost virtualHost = session.getVirtualHost();
        final AMQChannel channel = session.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }
        channel.sync();
        try
        {
            final String exchangeName = body.getExchange() == null ? null : body.getExchange().toString();

            final Exchange exchange = virtualHost.getExchange(exchangeName);
            if(exchange == null)
            {
                throw body.getChannelException(AMQConstant.NOT_FOUND, "No such exchange: " + body.getExchange());
            }

            virtualHost.removeExchange(exchange, !body.getIfUnused());

            ExchangeDeleteOkBody responseBody = session.getMethodRegistry().createExchangeDeleteOkBody();

            session.writeFrame(responseBody.generateFrame(channelId));
        }

        catch (ExchangeIsAlternateException e)
        {
            throw body.getChannelException(AMQConstant.NOT_ALLOWED, "Exchange in use as an alternate exchange");

        }
        catch (RequiredExchangeException e)
        {
            throw body.getChannelException(AMQConstant.NOT_ALLOWED, "Exchange '"+body.getExchange()+"' cannot be deleted");
        }
    }
}
