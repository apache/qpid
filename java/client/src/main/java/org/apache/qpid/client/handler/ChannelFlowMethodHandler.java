package org.apache.qpid.client.handler;

import org.apache.qpid.framing.ChannelFlowBody;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.AMQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class ChannelFlowMethodHandler implements StateAwareMethodListener<ChannelFlowBody>
{
    private static final Logger _logger = LoggerFactory.getLogger(ChannelFlowMethodHandler.class);
    private static final ChannelFlowMethodHandler _instance = new ChannelFlowMethodHandler();

    public static ChannelFlowMethodHandler getInstance()
    {
        return _instance;
    }

    private ChannelFlowMethodHandler()
    { }

    public void methodReceived(AMQProtocolSession session, ChannelFlowBody body, int channelId)
            throws AMQException
    {
        session.setFlowControl(channelId, body.getActive());
    }


}
