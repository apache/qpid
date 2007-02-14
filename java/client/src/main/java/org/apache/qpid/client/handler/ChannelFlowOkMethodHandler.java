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
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.ChannelFlowOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;

public class ChannelFlowOkMethodHandler implements StateAwareMethodListener
{
     private static final Logger _logger = Logger.getLogger(ChannelFlowOkMethodHandler.class);
     private static final ChannelFlowOkMethodHandler _instance = new ChannelFlowOkMethodHandler();

     public static ChannelFlowOkMethodHandler getInstance()
     {
         return _instance;
     }

     private ChannelFlowOkMethodHandler()
     {
     }

     public void methodReceived(AMQStateManager stateManager, AMQProtocolSession protocolSession, AMQMethodEvent evt) throws AMQException
     {
         ChannelFlowOkBody method = (ChannelFlowOkBody) evt.getMethod();
         _logger.debug("Received Channel.Flow-Ok message, active = " + method.active);
     }
}
