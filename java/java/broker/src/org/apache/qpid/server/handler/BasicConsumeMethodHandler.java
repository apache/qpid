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
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.framing.BasicConsumeBody;
import org.apache.qpid.framing.BasicConsumeOkBody;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.ConsumerTagNotUniqueException;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.log4j.Logger;

public class BasicConsumeMethodHandler implements StateAwareMethodListener<BasicConsumeBody>
{
    private static final Logger _log = Logger.getLogger(BasicConsumeMethodHandler.class);

    private static final BasicConsumeMethodHandler _instance = new BasicConsumeMethodHandler();

    public static BasicConsumeMethodHandler getInstance()
    {
        return _instance;
    }

    private BasicConsumeMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, QueueRegistry queueRegistry,
                               ExchangeRegistry exchangeRegistry, AMQProtocolSession session,
                               AMQMethodEvent<BasicConsumeBody> evt) throws AMQException
    {
        BasicConsumeBody body = evt.getMethod();
        final int channelId = evt.getChannelId();

        AMQChannel channel = session.getChannel(channelId);
        if (channel == null)
        {
            _log.error("Channel " + channelId + " not found");
            // TODO: either alert or error that the
        }
        else
        {
            AMQQueue queue = body.queue == null ? channel.getDefaultQueue() : queueRegistry.getQueue(body.queue);

            if(queue == null)
            {
                _log.info("No queue for '" + body.queue + "'");
            }
            try
            {
                String consumerTag = channel.subscribeToQueue(body.consumerTag, queue,  session, !body.noAck);
                if(!body.nowait)
                {
                    session.writeFrame(BasicConsumeOkBody.createAMQFrame(channelId, consumerTag));
                }

                //now allow queue to start async processing of any backlog of messages
                queue.deliverAsync();
            }
            catch(ConsumerTagNotUniqueException e)
            {
                String msg = "Non-unique consumer tag, '" + body.consumerTag + "'";
                session.writeFrame(ConnectionCloseBody.createAMQFrame(channelId, AMQConstant.NOT_ALLOWED.getCode(), msg, BasicConsumeBody.CLASS_ID, BasicConsumeBody.METHOD_ID));
            }
        }
    }
}
