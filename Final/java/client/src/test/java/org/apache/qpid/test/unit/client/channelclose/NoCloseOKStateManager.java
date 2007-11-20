/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.test.unit.client.channelclose;

import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.handler.ConnectionStartMethodHandler;
import org.apache.qpid.client.handler.ConnectionCloseMethodHandler;
import org.apache.qpid.client.handler.ConnectionTuneMethodHandler;
import org.apache.qpid.client.handler.ConnectionSecureMethodHandler;
import org.apache.qpid.client.handler.ConnectionOpenOkMethodHandler;
import org.apache.qpid.client.handler.ChannelCloseOkMethodHandler;
import org.apache.qpid.client.handler.BasicDeliverMethodHandler;
import org.apache.qpid.client.handler.BasicReturnMethodHandler;
import org.apache.qpid.client.handler.BasicCancelOkMethodHandler;
import org.apache.qpid.client.handler.ChannelFlowOkMethodHandler;
import org.apache.qpid.client.handler.QueueDeleteOkMethodHandler;
import org.apache.qpid.client.handler.ExchangeBoundOkMethodHandler;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.framing.ConnectionStartBody;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionOpenOkBody;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ChannelCloseOkBody;
import org.apache.qpid.framing.BasicDeliverBody;
import org.apache.qpid.framing.BasicReturnBody;
import org.apache.qpid.framing.BasicCancelOkBody;
import org.apache.qpid.framing.ChannelFlowOkBody;
import org.apache.qpid.framing.QueueDeleteOkBody;
import org.apache.qpid.framing.ExchangeBoundOkBody;

import java.util.Map;
import java.util.HashMap;

public class NoCloseOKStateManager extends AMQStateManager
{
    public NoCloseOKStateManager(AMQProtocolSession protocolSession)
    {
        super(protocolSession);
    }

    protected void registerListeners()
    {
        Map frame2handlerMap = new HashMap();

        // we need to register a map for the null (i.e. all state) handlers otherwise you get
        // a stack overflow in the handler searching code when you present it with a frame for which
        // no handlers are registered
        //
        _state2HandlersMap.put(null, frame2handlerMap);

        frame2handlerMap = new HashMap();
        frame2handlerMap.put(ConnectionStartBody.class, ConnectionStartMethodHandler.getInstance());
        frame2handlerMap.put(ConnectionCloseBody.class, ConnectionCloseMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_NOT_STARTED, frame2handlerMap);

        frame2handlerMap = new HashMap();
        frame2handlerMap.put(ConnectionTuneBody.class, ConnectionTuneMethodHandler.getInstance());
        frame2handlerMap.put(ConnectionSecureBody.class, ConnectionSecureMethodHandler.getInstance());
        frame2handlerMap.put(ConnectionCloseBody.class, ConnectionCloseMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_NOT_TUNED, frame2handlerMap);

        frame2handlerMap = new HashMap();
        frame2handlerMap.put(ConnectionOpenOkBody.class, ConnectionOpenOkMethodHandler.getInstance());
        frame2handlerMap.put(ConnectionCloseBody.class, ConnectionCloseMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_NOT_OPENED, frame2handlerMap);

        //
        // ConnectionOpen handlers
        //
        frame2handlerMap = new HashMap();
        // Use Test Handler for Close methods to not send Close-OKs
        frame2handlerMap.put(ChannelCloseBody.class, ChannelCloseMethodHandlerNoCloseOk.getInstance());

        frame2handlerMap.put(ChannelCloseOkBody.class, ChannelCloseOkMethodHandler.getInstance());
        frame2handlerMap.put(ConnectionCloseBody.class, ConnectionCloseMethodHandler.getInstance());
        frame2handlerMap.put(BasicDeliverBody.class, BasicDeliverMethodHandler.getInstance());
        frame2handlerMap.put(BasicReturnBody.class, BasicReturnMethodHandler.getInstance());
        frame2handlerMap.put(BasicCancelOkBody.class, BasicCancelOkMethodHandler.getInstance());
        frame2handlerMap.put(ChannelFlowOkBody.class, ChannelFlowOkMethodHandler.getInstance());
        frame2handlerMap.put(QueueDeleteOkBody.class, QueueDeleteOkMethodHandler.getInstance());
        frame2handlerMap.put(ExchangeBoundOkBody.class, ExchangeBoundOkMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_OPEN, frame2handlerMap);
    }


}
