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
package org.apache.qpid.client.handler;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.ExchangeBoundOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;

/**
 * @author Apache Software Foundation
 */
public class ExchangeBoundOkMethodHandler implements StateAwareMethodListener
{
     private static final Logger _logger = Logger.getLogger(ExchangeBoundOkMethodHandler.class);
     private static final ExchangeBoundOkMethodHandler _instance = new ExchangeBoundOkMethodHandler();

     public static ExchangeBoundOkMethodHandler getInstance()
     {
         return _instance;
     }

     private ExchangeBoundOkMethodHandler()
     {
     }

     public void methodReceived(AMQStateManager stateManager, AMQProtocolSession protocolSession, AMQMethodEvent evt) throws AMQException
     {
         if (_logger.isDebugEnabled())
         {
            ExchangeBoundOkBody body = (ExchangeBoundOkBody) evt.getMethod();
            _logger.debug("Received Exchange.Bound-Ok message, response code: " + body.replyCode + " text: " +
                          body.replyText);
         }
     }
}

