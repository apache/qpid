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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQConnectionClosedException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.client.protocol.AMQMethodEvent;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.client.AMQAuthenticationException;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionCloseOkBody;
import org.apache.qpid.framing.AMQShortString;

public class ConnectionCloseMethodHandler implements StateAwareMethodListener
{
    private static final Logger _logger = Logger.getLogger(ConnectionCloseMethodHandler.class);

    private static ConnectionCloseMethodHandler _handler = new ConnectionCloseMethodHandler();

    public static ConnectionCloseMethodHandler getInstance()
    {
        return _handler;
    }

    private ConnectionCloseMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent evt) throws AMQException
    {
        _logger.info("ConnectionClose frame received");
        ConnectionCloseBody method = (ConnectionCloseBody) evt.getMethod();

        // does it matter
        //stateManager.changeState(AMQState.CONNECTION_CLOSING);

        int errorCode = method.replyCode;
        AMQShortString reason = method.replyText;

        // TODO: check whether channel id of zero is appropriate
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        evt.getProtocolSession().writeFrame(ConnectionCloseOkBody.createAMQFrame((short)0, (byte)8, (byte)0));

        if (errorCode != 200)
        {
            if(errorCode == AMQConstant.NOT_ALLOWED.getCode())
            {
                _logger.info("Authentication Error:"+Thread.currentThread().getName());

                evt.getProtocolSession().closeProtocolSession();

                 //todo this is a bit of a fudge (could be conssidered such as each new connection needs a new state manager or at least a fresh state.
                 stateManager.changeState(AMQState.CONNECTION_NOT_STARTED);

                throw new AMQAuthenticationException(errorCode, reason == null ? null : reason.toString());
            }
            else
            {
                _logger.info("Connection close received with error code " + errorCode);


                throw new AMQConnectionClosedException(errorCode, "Error: " + reason);
            }
        }

        // this actually closes the connection in the case where it is not an error.

        evt.getProtocolSession().closeProtocolSession();

        stateManager.changeState(AMQState.CONNECTION_CLOSED);
    }
}
