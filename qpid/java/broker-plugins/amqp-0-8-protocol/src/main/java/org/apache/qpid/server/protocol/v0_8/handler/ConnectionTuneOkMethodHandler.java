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

import org.apache.log4j.Logger;

import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ConnectionTuneOkBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.state.AMQState;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;

public class ConnectionTuneOkMethodHandler implements StateAwareMethodListener<ConnectionTuneOkBody>
{
    private static final Logger _logger = Logger.getLogger(ConnectionTuneOkMethodHandler.class);

    private static ConnectionTuneOkMethodHandler _instance = new ConnectionTuneOkMethodHandler();

    public static ConnectionTuneOkMethodHandler getInstance()
    {
        return _instance;
    }

    public void methodReceived(AMQStateManager stateManager, ConnectionTuneOkBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();

        if (_logger.isDebugEnabled())
        {
            _logger.debug(body);
        }
        stateManager.changeState(AMQState.CONNECTION_NOT_OPENED);

        session.initHeartbeats(body.getHeartbeat());

        int brokerFrameMax = stateManager.getBroker().getContextValue(Integer.class,Broker.BROKER_FRAME_SIZE);
        if(brokerFrameMax <= 0)
        {
            brokerFrameMax = Integer.MAX_VALUE;
        }

        if(body.getFrameMax() > (long) brokerFrameMax)
        {
            throw new AMQConnectionException(AMQConstant.SYNTAX_ERROR,
                                             "Attempt to set max frame size to " + body.getFrameMax()
                                             + "greater than the broker will allow: "
                                             + brokerFrameMax,
                                             body.getClazz(), body.getMethod(),
                                             body.getMajor(), body.getMinor(),null);
        }
        else if(body.getFrameMax() > 0 && body.getFrameMax() < AMQConstant.FRAME_MIN_SIZE.getCode())
        {
            throw new AMQConnectionException(AMQConstant.SYNTAX_ERROR,
                                             "Attempt to set max frame size to " + body.getFrameMax()
                                             + "which is smaller than the specification definined minimum: "
                                             + AMQConstant.FRAME_MIN_SIZE.getCode(),
                                             body.getClazz(), body.getMethod(),
                                             body.getMajor(), body.getMinor(),null);
        }
        int frameMax = body.getFrameMax() == 0 ? brokerFrameMax : (int) body.getFrameMax();
        session.setMaxFrameSize(frameMax);

        long maxChannelNumber = body.getChannelMax();
        //0 means no implied limit, except that forced by protocol limitations (0xFFFF)
        session.setMaximumNumberOfChannels( maxChannelNumber == 0 ? 0xFFFFL : maxChannelNumber);
    }
}
