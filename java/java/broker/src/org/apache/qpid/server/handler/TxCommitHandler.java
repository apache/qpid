/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.handler;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.TxCommitBody;
import org.apache.qpid.framing.TxCommitOkBody;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

public class TxCommitHandler implements StateAwareMethodListener<TxCommitBody>
{
    private static TxCommitHandler _instance = new TxCommitHandler();

    public static TxCommitHandler getInstance()
    {
        return _instance;
    }

    private TxCommitHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, QueueRegistry queueRegistry,
                               ExchangeRegistry exchangeRegistry, AMQProtocolSession protocolSession,
                               AMQMethodEvent<TxCommitBody> evt) throws AMQException
    {

        try{
            AMQChannel channel = protocolSession.getChannel(evt.getChannelId());
            channel.commit();
            protocolSession.writeFrame(TxCommitOkBody.createAMQFrame(evt.getChannelId()));
            channel.processReturns(protocolSession);            
        }catch(AMQException e){
            throw evt.getMethod().getChannelException(e.getErrorCode(), "Failed to commit: " + e.getMessage());
        }
    }
}
