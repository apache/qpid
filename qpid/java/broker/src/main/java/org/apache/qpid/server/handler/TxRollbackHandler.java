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
import org.apache.qpid.framing.TxRollbackBody;
import org.apache.qpid.framing.TxRollbackOkBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

public class TxRollbackHandler implements StateAwareMethodListener<TxRollbackBody>
{
    private static TxRollbackHandler _instance = new TxRollbackHandler();

    public static TxRollbackHandler getInstance()
    {
        return _instance;
    }

    private TxRollbackHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, TxRollbackBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();

        try
        {
            AMQChannel channel = session.getChannel(channelId);

            if (channel == null)
            {
                throw body.getChannelNotFoundException(channelId);
            }

            channel.rollback();

            MethodRegistry methodRegistry = session.getMethodRegistry();
            AMQMethodBody responseBody = methodRegistry.createTxRollbackOkBody();
            session.writeFrame(responseBody.generateFrame(channelId));

            
            //Now resend all the unacknowledged messages back to the original subscribers.
            //(Must be done after the TxnRollback-ok response).
            // Why, are we not allowed to send messages back to client before the ok method?
            channel.resend(false);
        }
        catch (AMQException e)
        {
            throw body.getChannelException(e.getErrorCode(), "Failed to rollback: " + e.getMessage());
        }
    }
}
