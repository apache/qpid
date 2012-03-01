package org.apache.qpid.server.output;
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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicCancelOkBody;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.BasicGetOkBody;
import org.apache.qpid.framing.BasicReturnBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;
import org.apache.qpid.server.message.AMQMessage;
import org.apache.qpid.server.message.MessageContentSource;
import org.apache.qpid.server.message.MessageTransferMessage;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.transport.DeliveryProperties;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

class ProtocolOutputConverterImpl implements ProtocolOutputConverter
{
    private static final int BASIC_CLASS_ID = 60;

    private final MethodRegistry _methodRegistry;
    private final AMQProtocolSession _protocolSession;

    ProtocolOutputConverterImpl(AMQProtocolSession session, MethodRegistry methodRegistry)
    {
        _protocolSession = session;
        _methodRegistry = methodRegistry;
    }


    public AMQProtocolSession getProtocolSession()
    {
        return _protocolSession;
    }

    public void writeDeliver(QueueEntry entry, int channelId, long deliveryTag, AMQShortString consumerTag)
            throws AMQException
    {
        AMQBody deliverBody = createEncodedDeliverBody(entry, deliveryTag, consumerTag);
        writeMessageDelivery(entry, channelId, deliverBody);
    }


    private ContentHeaderBody getContentHeaderBody(QueueEntry entry)
            throws AMQException
    {
        if(entry.getMessage() instanceof AMQMessage)
        {
            return ((AMQMessage)entry.getMessage()).getContentHeaderBody();
        }
        else
        {
            final MessageTransferMessage message = (MessageTransferMessage) entry.getMessage();
            BasicContentHeaderProperties props = HeaderPropertiesConverter.convert(message, entry.getQueue().getVirtualHost());
            ContentHeaderBody chb = new ContentHeaderBody(props, BASIC_CLASS_ID);
            chb.setBodySize(message.getSize());
            return chb;
        }
    }


    private void writeMessageDelivery(QueueEntry entry, int channelId, AMQBody deliverBody)
            throws AMQException
    {
        writeMessageDelivery(entry.getMessage(), getContentHeaderBody(entry), channelId, deliverBody);
    }

    private void writeMessageDelivery(MessageContentSource message, ContentHeaderBody contentHeaderBody, int channelId, AMQBody deliverBody)
            throws AMQException
    {


        int bodySize = (int) message.getSize();

        if(bodySize == 0)
        {
            SmallCompositeAMQBodyBlock compositeBlock = new SmallCompositeAMQBodyBlock(channelId, deliverBody,
                                                                             contentHeaderBody);

            writeFrame(compositeBlock);
        }
        else
        {
            int maxBodySize = (int) getProtocolSession().getMaxFrameSize() - AMQFrame.getFrameOverhead();


            int capacity = bodySize > maxBodySize ? maxBodySize : bodySize;

            int writtenSize = capacity;

            AMQBody firstContentBody = new MessageContentSourceBody(message,0,capacity);

            CompositeAMQBodyBlock
                    compositeBlock = new CompositeAMQBodyBlock(channelId, deliverBody, contentHeaderBody, firstContentBody);
            writeFrame(compositeBlock);

            while(writtenSize < bodySize)
            {
                capacity = bodySize - writtenSize > maxBodySize ? maxBodySize : bodySize - writtenSize;
                MessageContentSourceBody body = new MessageContentSourceBody(message, writtenSize, capacity);
                writtenSize += capacity;

                writeFrame(new AMQFrame(channelId, body));
            }
        }
    }

    private class MessageContentSourceBody implements AMQBody
    {
        public static final byte TYPE = 3;
        private int _length;
        private MessageContentSource _message;
        private int _offset;

        public MessageContentSourceBody(MessageContentSource message, int offset, int length)
        {
            _message = message;
            _offset = offset;
            _length = length;
        }

        public byte getFrameType()
        {
            return TYPE;
        }

        public int getSize()
        {
            return _length;
        }

        public void writePayload(DataOutput buffer) throws IOException
        {
            ByteBuffer buf = _message.getContent(_offset, _length);

            if(buf.hasArray())
            {
                buffer.write(buf.array(), buf.arrayOffset()+buf.position(), buf.remaining());
            }
            else
            {

                byte[] data = new byte[_length];

                buf.get(data);

                buffer.write(data);
            }
        }

        public void handle(int channelId, AMQVersionAwareProtocolSession amqProtocolSession) throws AMQException
        {
            throw new UnsupportedOperationException();
        }
    }

    public void writeGetOk(QueueEntry entry, int channelId, long deliveryTag, int queueSize) throws AMQException
    {
        AMQBody deliver = createEncodedGetOkBody(entry, deliveryTag, queueSize);
        writeMessageDelivery(entry, channelId, deliver);
    }


    private AMQBody createEncodedDeliverBody(QueueEntry entry,
                                              final long deliveryTag,
                                              final AMQShortString consumerTag)
            throws AMQException
    {

        final AMQShortString exchangeName;
        final AMQShortString routingKey;

        if(entry.getMessage() instanceof AMQMessage)
        {
            final AMQMessage message = (AMQMessage) entry.getMessage();
            final MessagePublishInfo pb = message.getMessagePublishInfo();
            exchangeName = pb.getExchange();
            routingKey = pb.getRoutingKey();
        }
        else
        {
            MessageTransferMessage message = (MessageTransferMessage) entry.getMessage();
            DeliveryProperties delvProps = message.getHeader().getDeliveryProperties();
            exchangeName = (delvProps == null || delvProps.getExchange() == null) ? null : new AMQShortString(delvProps.getExchange());
            routingKey = (delvProps == null || delvProps.getRoutingKey() == null) ? null : new AMQShortString(delvProps.getRoutingKey());
        }

        final boolean isRedelivered = entry.isRedelivered();

        final AMQBody returnBlock = new AMQBody()
        {

            private AMQBody _underlyingBody;

            public AMQBody createAMQBody()
            {
                return _methodRegistry.createBasicDeliverBody(consumerTag,
                                                              deliveryTag,
                                                              isRedelivered,
                                                              exchangeName,
                                                              routingKey);





            }

            public byte getFrameType()
            {
                return AMQMethodBody.TYPE;
            }

            public int getSize()
            {
                if(_underlyingBody == null)
                {
                    _underlyingBody = createAMQBody();
                }
                return _underlyingBody.getSize();
            }

            public void writePayload(DataOutput buffer) throws IOException
            {
                if(_underlyingBody == null)
                {
                    _underlyingBody = createAMQBody();
                }
                _underlyingBody.writePayload(buffer);
            }

            public void handle(final int channelId, final AMQVersionAwareProtocolSession amqMinaProtocolSession)
                throws AMQException
            {
                throw new AMQException("This block should never be dispatched!");
            }
        };
        return returnBlock;
    }

    private AMQBody createEncodedGetOkBody(QueueEntry entry, long deliveryTag, int queueSize)
            throws AMQException
    {
        final AMQShortString exchangeName;
        final AMQShortString routingKey;

        if(entry.getMessage() instanceof AMQMessage)
        {
            final AMQMessage message = (AMQMessage) entry.getMessage();
            final MessagePublishInfo pb = message.getMessagePublishInfo();
            exchangeName = pb.getExchange();
            routingKey = pb.getRoutingKey();
        }
        else
        {
            MessageTransferMessage message = (MessageTransferMessage) entry.getMessage();
            DeliveryProperties delvProps = message.getHeader().getDeliveryProperties();
            exchangeName = (delvProps == null || delvProps.getExchange() == null) ? null : new AMQShortString(delvProps.getExchange());
            routingKey = (delvProps == null || delvProps.getRoutingKey() == null) ? null : new AMQShortString(delvProps.getRoutingKey());
        }

        final boolean isRedelivered = entry.isRedelivered();

        BasicGetOkBody getOkBody =
                _methodRegistry.createBasicGetOkBody(deliveryTag,
                                                    isRedelivered,
                                                    exchangeName,
                                                    routingKey,
                                                    queueSize);

        return getOkBody;
    }

    public byte getProtocolMinorVersion()
    {
        return _protocolSession.getProtocolMinorVersion();
    }

    public byte getProtocolMajorVersion()
    {
        return getProtocolSession().getProtocolMajorVersion();
    }

    private AMQBody createEncodedReturnFrame(MessagePublishInfo messagePublishInfo,
                                             int replyCode,
                                             AMQShortString replyText) throws AMQException
    {

        BasicReturnBody basicReturnBody =
                _methodRegistry.createBasicReturnBody(replyCode,
                                                     replyText,
                                                     messagePublishInfo.getExchange(),
                                                     messagePublishInfo.getRoutingKey());


        return basicReturnBody;
    }

    public void writeReturn(MessagePublishInfo messagePublishInfo, ContentHeaderBody header, MessageContentSource message, int channelId, int replyCode, AMQShortString replyText)
            throws AMQException
    {

        AMQBody returnFrame = createEncodedReturnFrame(messagePublishInfo, replyCode, replyText);

        writeMessageDelivery(message, header, channelId, returnFrame);
    }


    public void writeFrame(AMQDataBlock block)
    {
        getProtocolSession().writeFrame(block);
    }


    public void confirmConsumerAutoClose(int channelId, AMQShortString consumerTag)
    {

        BasicCancelOkBody basicCancelOkBody = _methodRegistry.createBasicCancelOkBody(consumerTag);
        writeFrame(basicCancelOkBody.generateFrame(channelId));

    }


    public static final class CompositeAMQBodyBlock extends AMQDataBlock
    {
        public static final int OVERHEAD = 3 * AMQFrame.getFrameOverhead();

        private final AMQBody _methodBody;
        private final AMQBody _headerBody;
        private final AMQBody _contentBody;
        private final int _channel;


        public CompositeAMQBodyBlock(int channel, AMQBody methodBody, AMQBody headerBody, AMQBody contentBody)
        {
            _channel = channel;
            _methodBody = methodBody;
            _headerBody = headerBody;
            _contentBody = contentBody;

        }

        public long getSize()
        {
            return OVERHEAD + _methodBody.getSize() + _headerBody.getSize() + _contentBody.getSize();
        }

        public void writePayload(DataOutput buffer) throws IOException
        {
            AMQFrame.writeFrames(buffer, _channel, _methodBody, _headerBody, _contentBody);
        }
    }

    public static final class SmallCompositeAMQBodyBlock extends AMQDataBlock
    {
        public static final int OVERHEAD = 2 * AMQFrame.getFrameOverhead();

        private final AMQBody _methodBody;
        private final AMQBody _headerBody;
        private final int _channel;


        public SmallCompositeAMQBodyBlock(int channel, AMQBody methodBody, AMQBody headerBody)
        {
            _channel = channel;
            _methodBody = methodBody;
            _headerBody = headerBody;

        }

        public long getSize()
        {
            return OVERHEAD + _methodBody.getSize() + _headerBody.getSize() ;
        }

        public void writePayload(DataOutput buffer) throws IOException
        {
            AMQFrame.writeFrames(buffer, _channel, _methodBody, _headerBody);
        }
    }

}