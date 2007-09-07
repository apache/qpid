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
import org.apache.qpid.framing.BasicRejectBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.ack.UnacknowledgedMessage;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.log4j.Logger;

public class BasicRejectMethodHandler implements StateAwareMethodListener<BasicRejectBody>
{
    private static final Logger _logger = Logger.getLogger(BasicRejectMethodHandler.class);

    private static BasicRejectMethodHandler _instance = new BasicRejectMethodHandler();

    public static BasicRejectMethodHandler getInstance()
    {
        return _instance;
    }

    private BasicRejectMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<BasicRejectBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();

        int channelId = evt.getChannelId();

//        if (_logger.isDebugEnabled())
//        {
//            _logger.debug("Rejecting:" + evt.getMethod().deliveryTag +
//                          ": Requeue:" + evt.getMethod().requeue +
////                              ": Resend:" + evt.getMethod().resend +
//                          " on channel:" + channelId);
//        }

        AMQChannel channel = session.getChannel(channelId);

        if (channel == null)
        {
            throw evt.getMethod().getChannelNotFoundException(channelId);
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Rejecting:" + evt.getMethod().deliveryTag +
                          ": Requeue:" + evt.getMethod().requeue +
//                              ": Resend:" + evt.getMethod().resend +
                          " on channel:" + channel.debugIdentity());
        }

        long deliveryTag = evt.getMethod().deliveryTag;

        UnacknowledgedMessage message = channel.getUnacknowledgedMessageMap().get(deliveryTag);

        if (message == null)
        {
            _logger.warn("Dropping reject request as message is null for tag:" + deliveryTag);
//            throw evt.getMethod().getChannelException(AMQConstant.NOT_FOUND, "Delivery Tag(" + deliveryTag + ")not known");
        }
        else
        {

            if (_logger.isTraceEnabled())
            {
                _logger.trace("Rejecting: DT:" + deliveryTag + "-" + message.message.debugIdentity() +
                              ": Requeue:" + evt.getMethod().requeue +
//                              ": Resend:" + evt.getMethod().resend +
                              " on channel:" + channel.debugIdentity());
            }

            // If we haven't requested message to be resent to this consumer then reject it from ever getting it.
//            if (!evt.getMethod().resend)
            {
                message.message.reject(message.message.getDeliveredSubscription(message.queue));
            }

            if (evt.getMethod().requeue)
            {
                channel.requeue(deliveryTag);
            }
            else
            {
                _logger.warn("Dropping message as requeue not required and there is no dead letter queue");
//                message.queue = channel.getDefaultDeadLetterQueue();
//                channel.requeue(deliveryTag);
            }
        }
    }
}
