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
package org.apache.qpid.client.protocol.amqp_8_0;

import org.apache.qpid.framing.*;
import org.apache.qpid.framing.amqp_8_0.*;
import org.apache.qpid.client.protocol.ProtocolOutputHandler;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.listener.SpecificMethodFrameListener;
import org.apache.qpid.AMQTimeoutException;
import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;

import org.apache.mina.common.ByteBuffer;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArraySet;

public class ProtocolOutputHandler_8_0 implements ProtocolOutputHandler
{
    private static final AMQMethodFactory METHOD_FACTORY = new AMQMethodFactory_8_0() ;


    private static final Map<Class<? extends AMQMethodBody>, Class<? extends AMQMethodBody>> REQUSET_RESPONSE_METHODBODY_MAP =
            new HashMap<Class<? extends AMQMethodBody>, Class<? extends AMQMethodBody>>();

    static
    {
        // Basic Class
        REQUSET_RESPONSE_METHODBODY_MAP.put(BasicCancelBody.class, BasicCancelOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(BasicConsumeBody.class, BasicConsumeOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(BasicQosBody.class, BasicQosOkBody.class);
        // GET ???
        REQUSET_RESPONSE_METHODBODY_MAP.put(BasicRecoverBody.class, BasicRecoverOkBody.class);

        // Channel Class
        REQUSET_RESPONSE_METHODBODY_MAP.put(ChannelCloseBody.class, ChannelCloseOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(ChannelFlowBody.class, ChannelFlowOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(ChannelOpenBody.class, ChannelOpenOkBody.class);

        // Connection Class
        REQUSET_RESPONSE_METHODBODY_MAP.put(ConnectionOpenBody.class, ConnectionOpenOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(ConnectionSecureBody.class, ConnectionSecureOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(ConnectionStartBody.class, ConnectionStartOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(ConnectionTuneBody.class, ConnectionTuneOkBody.class);

        // Exchange Class
        REQUSET_RESPONSE_METHODBODY_MAP.put(ExchangeBoundBody.class, ExchangeBoundOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(ExchangeDeclareBody.class, ExchangeDeclareOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(ExchangeDeleteBody.class, ExchangeDeleteOkBody.class);

        // Queue Class
        REQUSET_RESPONSE_METHODBODY_MAP.put(QueueBindBody.class, QueueBindOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(QueueDeclareBody.class, QueueDeclareOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(QueueDeleteBody.class, QueueDeleteOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(QueuePurgeBody.class, QueuePurgeOkBody.class);

        // Tx Class
        REQUSET_RESPONSE_METHODBODY_MAP.put(TxCommitBody.class, TxCommitOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(TxRollbackBody.class, TxRollbackOkBody.class);
        REQUSET_RESPONSE_METHODBODY_MAP.put(TxSelectBody.class, TxSelectOkBody.class);

    }





    private final AMQProtocolSession _session;
    private static final long DEFAULT_TIMEOUT = 30000;
    private final CopyOnWriteArraySet<SpecificMethodFrameListener> _frameListeners =
            new CopyOnWriteArraySet<SpecificMethodFrameListener>();

    public ProtocolOutputHandler_8_0(AMQProtocolSession amqProtocolSession)
    {
        _session = amqProtocolSession;
    }




    private void writeFrame(AMQDataBlock frame)
    {
        _session.writeFrame(frame);
    }

    public void sendCommand(int channelId, AMQMethodBody command)
    {
        _session.writeFrame(new AMQFrame(channelId,command));
    }

    public AMQMethodBody sendCommandReceiveResponse(int channelId, AMQMethodBody command) throws AMQException
    {
        return sendCommandReceiveResponse(channelId, command, REQUSET_RESPONSE_METHODBODY_MAP.get(command.getClass()));
    }

    public AMQMethodBody sendCommandReceiveResponse(int channelId, AMQMethodBody command, long timeout) throws AMQException
    {
        return sendCommandReceiveResponse(channelId, command, REQUSET_RESPONSE_METHODBODY_MAP.get(command.getClass()), timeout);
    }

    public <T extends AMQMethodBody> T sendCommandReceiveResponse(int channelId, AMQMethodBody command, Class<T> responseClass, long timeout) throws AMQException
    {
        AMQFrame frame = new AMQFrame(channelId,command);
        return writeCommandFrameAndWaitForReply(frame,
                                                new SpecificMethodFrameListener<T>(channelId, responseClass), timeout);
    }

    private <T extends AMQMethodBody> T writeCommandFrameAndWaitForReply(AMQFrame frame, SpecificMethodFrameListener<T> listener, long timeout) throws AMQException
    {
        try
         {
             _frameListeners.add(listener);
             _session.writeFrame(frame);

             AMQMethodEvent<T> e = listener.blockForFrame(timeout);
             return e.getMethod();
             // When control resumes before this line, a reply will have been received
             // that matches the criteria defined in the blocking listener
         }
         finally
         {
             // If we don't removeKey the listener then no-one will
             _frameListeners.remove(listener);
         }


    }

    public <T extends AMQMethodBody> T sendCommandReceiveResponse(int channelId, AMQMethodBody command, Class<T> responseClass) throws AMQException
    {
        return sendCommandReceiveResponse(channelId, command, responseClass, DEFAULT_TIMEOUT);
    }

    public AMQMethodFactory getAMQMethodFactory()
    {
        return METHOD_FACTORY;
    }


    public void publishMessage(int channelId, AMQShortString exchangeName, AMQShortString routingKey, boolean immediate, boolean mandatory, ByteBuffer payload, CommonContentHeaderProperties contentHeaderProperties, int ticket)
    {
        final int size = (payload != null) ? payload.limit() : 0;
        BasicPublishBodyImpl publishBody = new BasicPublishBodyImpl(ticket, exchangeName, routingKey, mandatory, immediate);


        final int contentBodyFrameCount = calculateContentBodyFrameCount(payload);
        final AMQFrame[] frames = new AMQFrame[2 + contentBodyFrameCount];

        if (payload != null)
        {
            createContentBodies(payload, frames, 2, channelId);
        }



        AMQFrame contentHeaderFrame =
            ContentHeaderBody.createAMQFrame(channelId,
                                             publishBody.CLASS_ID,
                                             0, // weight
                                             contentHeaderProperties,
                                             size);

        frames[0] = new AMQFrame(channelId,publishBody);
        frames[1] = contentHeaderFrame;
        CompositeAMQDataBlock compositeFrame = new CompositeAMQDataBlock(frames);
        writeFrame(compositeFrame);
    }

    public <M extends AMQMethodBody> boolean methodReceived(AMQMethodEvent<M> evt) throws AMQException
    {
        boolean wasAnyoneInterested = false;
        if (!_frameListeners.isEmpty())
        {
            Iterator<SpecificMethodFrameListener> it = _frameListeners.iterator();
            while (it.hasNext())
            {
                final SpecificMethodFrameListener listener = it.next();
                wasAnyoneInterested = listener.methodReceived(evt) || wasAnyoneInterested;
            }
        }

        return wasAnyoneInterested;
    }

    public void error(Exception e)
    {
        if (!_frameListeners.isEmpty())
        {
            final Iterator<SpecificMethodFrameListener> it = _frameListeners.iterator();
            while (it.hasNext())
            {
                final SpecificMethodFrameListener ml = it.next();
                ml.error(e);
            }
        }
    }


    /**
      * Create content bodies. This will split a large message into numerous bodies depending on the negotiated
      * maximum frame size.
      *
      * @param payload
      * @param frames
      * @param offset
      * @param channelId @return the array of content bodies
      */
     private void createContentBodies(ByteBuffer payload, AMQFrame[] frames, int offset, int channelId)
     {

         if (frames.length == (offset + 1))
         {
             frames[offset] = ContentBody.createAMQFrame(channelId, new ContentBody(payload));
         }
         else
         {

             final long framePayloadMax = _session.getAMQConnection().getMaximumFrameSize() - 1;
             long remaining = payload.remaining();
             for (int i = offset; i < frames.length; i++)
             {
                 payload.position((int) framePayloadMax * (i - offset));
                 int length = (remaining >= framePayloadMax) ? (int) framePayloadMax : (int) remaining;
                 payload.limit(payload.position() + length);
                 frames[i] = ContentBody.createAMQFrame(channelId, new ContentBody(payload.slice()));

                 remaining -= length;
             }
         }

     }

     private int calculateContentBodyFrameCount(ByteBuffer payload)
     {
         // we substract one from the total frame maximum size to account for the end of frame marker in a body frame
         // (0xCE byte).
         int frameCount;
         if ((payload == null) || (payload.remaining() == 0))
         {
             frameCount = 0;
         }
         else
         {
             int dataLength = payload.remaining();
             final long framePayloadMax = _session.getAMQConnection().getMaximumFrameSize() - 1;
             int lastFrame = ((dataLength % framePayloadMax) > 0) ? 1 : 0;
             frameCount = (int) (dataLength / framePayloadMax) + lastFrame;
         }

         return frameCount;
     }



}
