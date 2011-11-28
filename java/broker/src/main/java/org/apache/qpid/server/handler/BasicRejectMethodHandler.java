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
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.queue.QueueEntry;
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

    public void methodReceived(AMQStateManager stateManager, BasicRejectBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();

        AMQChannel channel = session.getChannel(channelId);

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Rejecting:" + body.getDeliveryTag() +
                          ": Requeue:" + body.getRequeue() +
                          " on channel:" + channel.debugIdentity());
        }

        long deliveryTag = body.getDeliveryTag();

        QueueEntry message = channel.getUnacknowledgedMessageMap().get(deliveryTag);

        if (message == null)
        {
            _logger.warn("Dropping reject request as message is null for tag:" + deliveryTag);
        }                 
        else
        {
            if (message.isQueueDeleted())
            {
                _logger.warn("Message's Queue has already been purged, dropping message");
                message = channel.getUnacknowledgedMessageMap().remove(deliveryTag);
                if(message != null)
                {
                    message.discard();
                }
                return;
            }

            if (message.getMessage() == null)
            {
                _logger.warn("Message has already been purged, unable to Reject.");
                return;
            }


            if (_logger.isDebugEnabled())
            {
                _logger.debug("Rejecting: DT:" + deliveryTag + "-" + message.getMessage() +
                              ": Requeue:" + body.getRequeue() +
                              " on channel:" + channel.debugIdentity());
            }

            message.reject();

            if (body.getRequeue())
            {
                channel.requeue(deliveryTag);

                //this requeue represents a message rejected from the pre-dispatch queue
                //therefore we need to amend the delivery counter.
                message.decrementDeliveryCount();
            }
            else
            {
                 final boolean maxDeliveryCountEnabled = channel.isMaxDeliveryCountEnabled(deliveryTag);
                 _logger.debug("maxDeliveryCountEnabled: " + maxDeliveryCountEnabled + " deliveryTag " + deliveryTag);
                 if (maxDeliveryCountEnabled)
                 {
                     final boolean deliveredTooManyTimes = channel.isDeliveredTooManyTimes(deliveryTag);
                     _logger.debug("deliveredTooManyTimes: " + deliveredTooManyTimes + " deliveryTag " + deliveryTag);
                     if (deliveredTooManyTimes)
                     {
                         channel.deadLetter(body.getDeliveryTag());
                     }
                     else
                     {
                         //this requeue represents a message rejected because of a recover/rollback that we
                         //are not ready to DLQ. We rely on the reject command to resend from the unacked map
                         //and therefore need to increment the delivery counter so we cancel out the effect
                         //of the AMQChannel#resend() decrement.
                         message.incrementDeliveryCount();
                     }
                 }
                 else
                 {
                     channel.deadLetter(body.getDeliveryTag());
                 }
            }
        }
    }
}
