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

import org.apache.qpid.AMQConnectionClosedException;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQAuthenticationException;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionCloseOkBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionCloseMethodHandler implements StateAwareMethodListener<ConnectionCloseBody>
{
    private static final Logger _logger = LoggerFactory.getLogger(ConnectionCloseMethodHandler.class);

    private static ConnectionCloseMethodHandler _handler = new ConnectionCloseMethodHandler();

    public static ConnectionCloseMethodHandler getInstance()
    {
        return _handler;
    }

    private ConnectionCloseMethodHandler()
    { }

    public void methodReceived(AMQStateManager stateManager, ConnectionCloseBody method, int channelId)
                throws AMQException
    {
        _logger.info("ConnectionClose frame received");
        final AMQProtocolSession session = stateManager.getProtocolSession();
        

        // does it matter
        // stateManager.changeState(AMQState.CONNECTION_CLOSING);

        AMQConstant errorCode = AMQConstant.getConstant(method.getReplyCode());
        AMQShortString reason = method.getReplyText();

        try
        {

            ConnectionCloseOkBody closeOkBody = session.getMethodRegistry().createConnectionCloseOkBody();
            // TODO: check whether channel id of zero is appropriate
            // Be aware of possible changes to parameter order as versions change.
            session.writeFrame(closeOkBody.generateFrame(0));

            if (errorCode != AMQConstant.REPLY_SUCCESS)
            {
                if (errorCode == AMQConstant.NOT_ALLOWED || (errorCode == AMQConstant.ACCESS_REFUSED))
                {
                    _logger.info("Error :" + errorCode +":"+ Thread.currentThread().getName());

                    // todo ritchiem : Why do this here when it is going to be done in the finally block?
                    session.closeProtocolSession();

                    // todo this is a bit of a fudge (could be conssidered such as each new connection needs a new state manager or at least a fresh state.
                    stateManager.changeState(AMQState.CONNECTION_NOT_STARTED);

                    throw new AMQAuthenticationException(errorCode, reason == null ? null : reason.toString(), null);
                }
                else
                {
                    _logger.info("Connection close received with error code " + errorCode);

                    throw new AMQConnectionClosedException(errorCode, "Error: " + reason, null);
                }
            }
        }
        finally
        {
            // this actually closes the connection in the case where it is not an error.

            session.closeProtocolSession();

            // ritchiem: Doing this though will cause any waiting connection start to be released without being able to
            // see what the cause was.
            stateManager.changeState(AMQState.CONNECTION_CLOSED);
        }
    }


}
