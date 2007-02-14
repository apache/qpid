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
import org.apache.qpid.framing.BasicCancelOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;

/**
 * @author Apache Software Foundation
 */
public class BasicCancelOkMethodHandler implements StateAwareMethodListener
{
     private static final Logger _logger = Logger.getLogger(BasicCancelOkMethodHandler.class);
     private static final BasicCancelOkMethodHandler _instance = new BasicCancelOkMethodHandler();

     public static BasicCancelOkMethodHandler getInstance()
     {
         return _instance;
     }

     private BasicCancelOkMethodHandler()
     {
     }

     public void methodReceived(AMQStateManager stateManager, AMQProtocolSession protocolSession, AMQMethodEvent evt) throws AMQException
     {
         _logger.debug("New BasicCancelOk method received");
         BasicCancelOkBody body = (BasicCancelOkBody) evt.getMethod();
         protocolSession.confirmConsumerCancelled(evt.getChannelId(), body.consumerTag);                  
     }
}
