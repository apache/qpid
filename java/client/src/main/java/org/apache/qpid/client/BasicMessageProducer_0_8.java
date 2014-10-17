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
package org.apache.qpid.client;

import java.nio.ByteBuffer;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.message.AMQMessageDelegate_0_8;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.QpidMessageProperties;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.CompositeAMQDataBlock;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.util.GZIPUtils;

public class BasicMessageProducer_0_8 extends BasicMessageProducer
{
    private static final Logger _logger = LoggerFactory.getLogger(BasicMessageProducer_0_8.class);
    private static final boolean SET_EXPIRATION_AS_TTL = Boolean.getBoolean(ClientProperties.SET_EXPIRATION_AS_TTL);

    BasicMessageProducer_0_8(AMQConnection connection, AMQDestination destination, boolean transacted, int channelId,
            AMQSession session, AMQProtocolHandler protocolHandler, long producerId, Boolean immediate, Boolean mandatory) throws AMQException
    {
        super(_logger,connection, destination,transacted,channelId,session, producerId, immediate, mandatory);
    }

    void declareDestination(AMQDestination destination) throws AMQException
    {

        if (destination.getDestSyntax() == AMQDestination.DestSyntax.ADDR)
        {
            getSession().resolveAddress(destination, false, false);

            getSession().handleLinkCreation(destination);
            getSession().sync();
        }
        else
        {
            if (getSession().isDeclareExchanges())
            {
                final MethodRegistry methodRegistry = getSession().getMethodRegistry();
                ExchangeDeclareBody body =
                        methodRegistry.createExchangeDeclareBody(getSession().getTicket(),
                                                                 destination.getExchangeName(),
                                                                 destination.getExchangeClass(),
                                                                 destination.getExchangeName()
                                                                         .toString()
                                                                         .startsWith("amq."),
                                                                 destination.isExchangeDurable(),
                                                                 destination.isExchangeAutoDelete(),
                                                                 destination.isExchangeInternal(),
                                                                 true,
                                                                 null);
                AMQFrame declare = body.generateFrame(getChannelId());

                getConnection().getProtocolHandler().writeFrame(declare);
            }
        }
    }

    void sendMessage(AMQDestination destination, Message origMessage, AbstractJMSMessage message,
                     UUID messageId, int deliveryMode,int priority, long timeToLive, boolean mandatory,
                     boolean immediate) throws JMSException
    {



        AMQMessageDelegate_0_8 delegate = (AMQMessageDelegate_0_8) message.getDelegate();
        BasicContentHeaderProperties contentHeaderProperties = delegate.getContentHeaderProperties();

        AMQShortString routingKey = destination.getRoutingKey();

        FieldTable headers = delegate.getContentHeaderProperties().getHeaders();

        if (destination.getDestSyntax() == AMQDestination.DestSyntax.ADDR &&
            (destination.getSubject() != null
             || (headers != null && headers.get(QpidMessageProperties.QPID_SUBJECT) != null)))
        {

            if (headers.get(QpidMessageProperties.QPID_SUBJECT) == null)
            {
                // use default subject in address string
                headers.setString(QpidMessageProperties.QPID_SUBJECT, destination.getSubject());
            }

            if (destination.getAddressType() == AMQDestination.TOPIC_TYPE)
            {
               routingKey = AMQShortString.valueOf(headers.getString(QpidMessageProperties.QPID_SUBJECT));
            }
        }

        BasicPublishBody body = getSession().getMethodRegistry().createBasicPublishBody(getSession().getTicket(),
                                                                                    destination.getExchangeName(),
                                                                                    routingKey,
                                                                                    mandatory,
                                                                                    immediate);

        AMQFrame publishFrame = body.generateFrame(getChannelId());

        message.prepareForSending();
        ByteBuffer payload = message.getData();

        contentHeaderProperties.setUserId(getUserID());

        //Set the JMS_QPID_DESTTYPE for 0-8/9 messages
        int type;
        if (destination instanceof Topic)
        {
            type = AMQDestination.TOPIC_TYPE;
        }
        else if (destination instanceof Queue)
        {
            type = AMQDestination.QUEUE_TYPE;
        }
        else
        {
            type = AMQDestination.UNKNOWN_TYPE;
        }

        //Set JMS_QPID_DESTTYPE
        delegate.getContentHeaderProperties().getHeaders().setInteger(CustomJMSXProperty.JMS_QPID_DESTTYPE.getShortStringName(), type);

        if (!isDisableTimestamps())
        {
            final long currentTime = System.currentTimeMillis();
            contentHeaderProperties.setTimestamp(currentTime);

            if (timeToLive > 0)
            {
                if(!SET_EXPIRATION_AS_TTL)
                {
                    //default behaviour used by Qpid
                    contentHeaderProperties.setExpiration(currentTime + timeToLive);
                }
                else
                {
                    //alternative behaviour for brokers interpreting the expiration header directly as a TTL.
                    contentHeaderProperties.setExpiration(timeToLive);
                }
            }
            else
            {
                contentHeaderProperties.setExpiration(0);
            }
        }

        contentHeaderProperties.setDeliveryMode((byte) deliveryMode);
        contentHeaderProperties.setPriority((byte) priority);

        int size = (payload != null) ? payload.remaining() : 0;

        byte[] compressed;
        if(size > getConnection().getMessageCompressionThresholdSize()
               && getConnection().getDelegate().isMessageCompressionSupported()
               && getConnection().isMessageCompressionDesired()
               && contentHeaderProperties.getEncoding() == null
               && (compressed = GZIPUtils.compressBufferToArray(payload)) != null)
        {
            contentHeaderProperties.setEncoding("gzip");
            payload = ByteBuffer.wrap(compressed);
            size = compressed.length;

        }
        final int contentBodyFrameCount = calculateContentBodyFrameCount(payload);
        final AMQFrame[] frames = new AMQFrame[2 + contentBodyFrameCount];

        if (payload != null)
        {
            createContentBodies(payload, frames, 2, getChannelId());
        }

        if ((contentBodyFrameCount != 0) && getLogger().isDebugEnabled())
        {
            getLogger().debug("Sending content body frames to " + destination);
        }


        // TODO: This is a hacky way of getting the AMQP class-id for the Basic class
        int classIfForBasic = getSession().getMethodRegistry().createBasicQosOkBody().getClazz();

        AMQFrame contentHeaderFrame =
            ContentHeaderBody.createAMQFrame(getChannelId(),
                                             contentHeaderProperties, size);
        if(contentHeaderFrame.getSize() > getSession().getAMQConnection().getMaximumFrameSize())
        {
            throw new JMSException("Unable to send message as the headers are too large ("
                                   + contentHeaderFrame.getSize()
                                   + " bytes, but the maximum negotiated frame size is "
                                   + getSession().getAMQConnection().getMaximumFrameSize()
                                   + ").");
        }
        if (getLogger().isDebugEnabled())
        {
            getLogger().debug("Sending content header frame to " + destination);
        }

        frames[0] = publishFrame;
        frames[1] = contentHeaderFrame;
        CompositeAMQDataBlock compositeFrame = new CompositeAMQDataBlock(frames);

        try
        {
            getSession().checkFlowControl();
        }
        catch (InterruptedException e)
        {
            JMSException jmse = new JMSException("Interrupted while waiting for flow control to be removed");
            jmse.setLinkedException(e);
            jmse.initCause(e);
            throw jmse;
        }

        getConnection().getProtocolHandler().writeFrame(compositeFrame);
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
            byte[] data = new byte[payload.remaining()];
            payload.get(data);
            frames[offset] = ContentBody.createAMQFrame(channelId, new ContentBody(data));
        }
        else
        {

            final long framePayloadMax = getSession().getAMQConnection().getMaximumFrameSize() - 1;
            long remaining = payload.remaining();
            for (int i = offset; i < frames.length; i++)
            {
                payload.position((int) framePayloadMax * (i - offset));
                int length = (remaining >= framePayloadMax) ? (int) framePayloadMax : (int) remaining;
                payload.limit(payload.position() + length);
                byte[] data = new byte[payload.remaining()];
                payload.get(data);

                frames[i] = ContentBody.createAMQFrame(channelId, new ContentBody(data));

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
            final long framePayloadMax = getSession().getAMQConnection().getMaximumFrameSize() - 1;
            int lastFrame = ((dataLength % framePayloadMax) > 0) ? 1 : 0;
            frameCount = (int) (dataLength / framePayloadMax) + lastFrame;
        }

        return frameCount;
    }

    @Override
    public AMQSession_0_8 getSession()
    {
        return (AMQSession_0_8) super.getSession();
    }
}
