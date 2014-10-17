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
package org.apache.qpid.server.protocol.v0_8;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

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
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageContentSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.protocol.MessageConverterRegistry;
import org.apache.qpid.util.GZIPUtils;

public class ProtocolOutputConverterImpl implements ProtocolOutputConverter
{
    private static final int BASIC_CLASS_ID = 60;

    private final AMQProtocolEngine _connection;
    private static final AMQShortString GZIP_ENCODING = AMQShortString.valueOf(GZIPUtils.GZIP_CONTENT_ENCODING);

    public ProtocolOutputConverterImpl(AMQProtocolEngine connection)
    {
        _connection = connection;
    }


    public long writeDeliver(final ServerMessage m,
                             final InstanceProperties props, int channelId,
                             long deliveryTag,
                             AMQShortString consumerTag)
    {
        final AMQMessage msg = convertToAMQMessage(m);
        final boolean isRedelivered = Boolean.TRUE.equals(props.getProperty(InstanceProperties.Property.REDELIVERED));
        AMQBody deliverBody = createEncodedDeliverBody(msg, isRedelivered, deliveryTag, consumerTag);
        return writeMessageDelivery(msg, channelId, deliverBody);
    }

    private AMQMessage convertToAMQMessage(ServerMessage serverMessage)
    {
        if(serverMessage instanceof AMQMessage)
        {
            return (AMQMessage) serverMessage;
        }
        else
        {
            return getMessageConverter(serverMessage).convert(serverMessage, _connection.getVirtualHost());
        }
    }

    private <M extends ServerMessage> MessageConverter<M, AMQMessage> getMessageConverter(M message)
    {
        Class<M> clazz = (Class<M>) message.getClass();
        return MessageConverterRegistry.getConverter(clazz, AMQMessage.class);
    }

    private long writeMessageDelivery(AMQMessage message, int channelId, AMQBody deliverBody)
    {
        return writeMessageDelivery(message, message.getContentHeaderBody(), channelId, deliverBody);
    }

    private long writeMessageDelivery(MessageContentSource message, ContentHeaderBody contentHeaderBody, int channelId, AMQBody deliverBody)
    {

        int bodySize = (int) message.getSize();
        boolean msgCompressed = isCompressed(contentHeaderBody);
        byte[] modifiedContent;

        // straight through case
        boolean compressionSupported = _connection.isCompressionSupported();

        if(msgCompressed && !compressionSupported &&
                (modifiedContent = GZIPUtils.uncompressBufferToArray(message.getContent(0,bodySize))) != null)
        {
            BasicContentHeaderProperties modifiedProps =
                    new BasicContentHeaderProperties(contentHeaderBody.getProperties());
            modifiedProps.setEncoding((String)null);

            writeMessageDeliveryModified(channelId, deliverBody, modifiedProps, modifiedContent);

            return modifiedContent.length;
        }
        else if(!msgCompressed
                && compressionSupported
                && contentHeaderBody.getProperties().getEncoding()==null
                && bodySize > _connection.getMessageCompressionThreshold()
                && (modifiedContent = GZIPUtils.compressBufferToArray(message.getContent(0, bodySize))) != null)
        {
            BasicContentHeaderProperties modifiedProps =
                    new BasicContentHeaderProperties(contentHeaderBody.getProperties());
            modifiedProps.setEncoding(GZIP_ENCODING);

            writeMessageDeliveryModified(channelId, deliverBody, modifiedProps, modifiedContent);

            return modifiedContent.length;
        }
        else
        {
            writeMessageDeliveryUnchanged(message, contentHeaderBody, channelId, deliverBody, bodySize);

            return bodySize;
        }
    }

    private int writeMessageDeliveryModified(final int channelId,
                                             final AMQBody deliverBody,
                                             final BasicContentHeaderProperties modifiedProps,
                                             final byte[] content)
    {
        final int bodySize;
        bodySize = content.length;
        ContentHeaderBody modifiedHeaderBody =
                new ContentHeaderBody(modifiedProps, bodySize);
        final MessageContentSource wrappedSource = new MessageContentSource()
        {
            @Override
            public int getContent(final ByteBuffer buf, final int offset)
            {
                int size = Math.min(buf.remaining(), content.length - offset);
                buf.put(content, offset, size);
                return size;
            }

            @Override
            public ByteBuffer getContent(final int offset, final int size)
            {
                return ByteBuffer.wrap(content, offset, size);
            }

            @Override
            public long getSize()
            {
                return content.length;
            }
        };
        writeMessageDeliveryUnchanged(wrappedSource, modifiedHeaderBody, channelId, deliverBody, bodySize);
        return bodySize;
    }

    private void writeMessageDeliveryUnchanged(final MessageContentSource message,
                                               final ContentHeaderBody contentHeaderBody,
                                               final int channelId, final AMQBody deliverBody, final int bodySize)
    {
        if (bodySize == 0)
        {
            SmallCompositeAMQBodyBlock compositeBlock = new SmallCompositeAMQBodyBlock(channelId, deliverBody,
                                                                                       contentHeaderBody);

            writeFrame(compositeBlock);
        }
        else
        {
            int maxBodySize = (int) _connection.getMaxFrameSize() - AMQFrame.getFrameOverhead();


            int capacity = bodySize > maxBodySize ? maxBodySize : bodySize;

            int writtenSize = capacity;

            AMQBody firstContentBody = new MessageContentSourceBody(message, 0, capacity);

            CompositeAMQBodyBlock
                    compositeBlock =
                    new CompositeAMQBodyBlock(channelId, deliverBody, contentHeaderBody, firstContentBody);
            writeFrame(compositeBlock);

            while (writtenSize < bodySize)
            {
                capacity = bodySize - writtenSize > maxBodySize ? maxBodySize : bodySize - writtenSize;
                MessageContentSourceBody body = new MessageContentSourceBody(message, writtenSize, capacity);
                writtenSize += capacity;

                writeFrame(new AMQFrame(channelId, body));
            }
        }
    }

    private boolean isCompressed(final ContentHeaderBody contentHeaderBody)
    {
        return GZIP_ENCODING.equals(contentHeaderBody.getProperties().getEncoding());
    }

    private class MessageContentSourceBody implements AMQBody
    {
        public static final byte TYPE = 3;
        private final int _length;
        private final MessageContentSource _message;
        private final int _offset;

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

        @Override
        public String toString()
        {
            return "[" + getClass().getSimpleName() + " offset: " + _offset + ", length: " + _length + "]";
        }

    }

    public long writeGetOk(final ServerMessage msg,
                           final InstanceProperties props,
                           int channelId,
                           long deliveryTag,
                           int queueSize)
    {
        AMQBody deliver = createEncodedGetOkBody(msg, props, deliveryTag, queueSize);
        return writeMessageDelivery(convertToAMQMessage(msg), channelId, deliver);
    }


    private AMQBody createEncodedDeliverBody(AMQMessage message,
                                             boolean isRedelivered,
                                             final long deliveryTag,
                                             final AMQShortString consumerTag)
    {

        final AMQShortString exchangeName;
        final AMQShortString routingKey;

        final MessagePublishInfo pb = message.getMessagePublishInfo();
        exchangeName = pb.getExchange();
        routingKey = pb.getRoutingKey();

        final AMQBody returnBlock = new EncodedDeliveryBody(deliveryTag, routingKey, exchangeName, consumerTag, isRedelivered);
        return returnBlock;
    }

    private class EncodedDeliveryBody implements AMQBody
    {
        private final long _deliveryTag;
        private final AMQShortString _routingKey;
        private final AMQShortString _exchangeName;
        private final AMQShortString _consumerTag;
        private final boolean _isRedelivered;
        private AMQBody _underlyingBody;

        private EncodedDeliveryBody(long deliveryTag, AMQShortString routingKey, AMQShortString exchangeName, AMQShortString consumerTag, boolean isRedelivered)
        {
            _deliveryTag = deliveryTag;
            _routingKey = routingKey;
            _exchangeName = exchangeName;
            _consumerTag = consumerTag;
            _isRedelivered = isRedelivered;
        }

        public AMQBody createAMQBody()
        {
            return _connection.getMethodRegistry().createBasicDeliverBody(_consumerTag,
                                                                               _deliveryTag,
                                                                               _isRedelivered,
                                                                               _exchangeName,
                                                                               _routingKey);
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

        @Override
        public String toString()
        {
            return "[" + getClass().getSimpleName() + " underlyingBody: " + String.valueOf(_underlyingBody) + "]";
        }
    }

    private AMQBody createEncodedGetOkBody(ServerMessage msg, InstanceProperties props, long deliveryTag, int queueSize)
    {
        final AMQShortString exchangeName;
        final AMQShortString routingKey;

        final AMQMessage message = convertToAMQMessage(msg);
        final MessagePublishInfo pb = message.getMessagePublishInfo();
        exchangeName = pb.getExchange();
        routingKey = pb.getRoutingKey();

        final boolean isRedelivered = Boolean.TRUE.equals(props.getProperty(InstanceProperties.Property.REDELIVERED));

        BasicGetOkBody getOkBody =
                _connection.getMethodRegistry().createBasicGetOkBody(deliveryTag,
                                                                          isRedelivered,
                                                                          exchangeName,
                                                                          routingKey,
                                                                          queueSize);

        return getOkBody;
    }

    private AMQBody createEncodedReturnFrame(MessagePublishInfo messagePublishInfo,
                                             int replyCode,
                                             AMQShortString replyText)
    {

        BasicReturnBody basicReturnBody =
                _connection.getMethodRegistry().createBasicReturnBody(replyCode,
                                                                           replyText,
                                                                           messagePublishInfo.getExchange(),
                                                                           messagePublishInfo.getRoutingKey());


        return basicReturnBody;
    }

    public void writeReturn(MessagePublishInfo messagePublishInfo, ContentHeaderBody header, MessageContentSource message, int channelId, int replyCode, AMQShortString replyText)
    {

        AMQBody returnFrame = createEncodedReturnFrame(messagePublishInfo, replyCode, replyText);

        writeMessageDelivery(message, header, channelId, returnFrame);
    }


    public void writeFrame(AMQDataBlock block)
    {
        _connection.writeFrame(block);
    }


    public void confirmConsumerAutoClose(int channelId, AMQShortString consumerTag)
    {

        BasicCancelOkBody basicCancelOkBody = _connection.getMethodRegistry().createBasicCancelOkBody(consumerTag);
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

        @Override
        public String toString()
        {
            StringBuilder builder = new StringBuilder();
            builder.append("[").append(getClass().getSimpleName())
                .append(" methodBody=").append(_methodBody)
                .append(", headerBody=").append(_headerBody)
                .append(", contentBody=").append(_contentBody)
                .append(", channel=").append(_channel).append("]");
            return builder.toString();
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

        @Override
        public String toString()
        {
            StringBuilder builder = new StringBuilder();
            builder.append(getClass().getSimpleName())
                .append("methodBody=").append(_methodBody)
                .append(", headerBody=").append(_headerBody)
                .append(", channel=").append(_channel).append("]");
            return builder.toString();
        }
    }

}
