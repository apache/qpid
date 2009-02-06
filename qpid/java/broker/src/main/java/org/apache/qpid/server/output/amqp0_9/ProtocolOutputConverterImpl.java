package org.apache.qpid.server.output.amqp0_9;
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


import org.apache.mina.common.ByteBuffer;

import java.util.Iterator;

import org.apache.qpid.server.output.ProtocolOutputConverter;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQMessageHandle;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.framing.*;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.ProtocolVersionMethodConverter;
import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;

public class ProtocolOutputConverterImpl implements ProtocolOutputConverter
{
    private static final MethodRegistry METHOD_REGISTRY = MethodRegistry.getMethodRegistry(ProtocolVersion.v0_9);
    private static final ProtocolVersionMethodConverter PROTOCOL_METHOD_CONVERTER = METHOD_REGISTRY.getProtocolVersionMethodConverter();


    public static Factory getInstanceFactory()
    {
        return new Factory()
        {

            public ProtocolOutputConverter newInstance(AMQProtocolSession session)
            {
                return new ProtocolOutputConverterImpl(session);
            }
        };
    }

    private final AMQProtocolSession _protocolSession;

    private ProtocolOutputConverterImpl(AMQProtocolSession session)
    {
        _protocolSession = session;
    }


    public AMQProtocolSession getProtocolSession()
    {
        return _protocolSession;
    }

    public void writeDeliver(QueueEntry queueEntry, int channelId, long deliveryTag, AMQShortString consumerTag)
            throws AMQException
    {
        AMQMessage message = queueEntry.getMessage();

        AMQBody deliverBody = createEncodedDeliverFrame(queueEntry, channelId, deliveryTag, consumerTag);
        final ContentHeaderBody contentHeaderBody = message.getContentHeaderBody();


        final AMQMessageHandle messageHandle = message.getMessageHandle();
        final StoreContext storeContext = message.getStoreContext();


        final int bodyCount = messageHandle.getBodyCount(storeContext);

        if(bodyCount == 0)
        {
            SmallCompositeAMQBodyBlock compositeBlock = new SmallCompositeAMQBodyBlock(channelId, deliverBody,
                                                                             contentHeaderBody);

            writeFrame(compositeBlock);
        }
        else
        {


            //
            // Optimise the case where we have a single content body. In that case we create a composite block
            // so that we can writeDeliver out the deliver, header and body with a single network writeDeliver.
            //
            ContentChunk cb = messageHandle.getContentChunk(storeContext, 0);

            AMQBody firstContentBody = PROTOCOL_METHOD_CONVERTER.convertToBody(cb);

            CompositeAMQBodyBlock compositeBlock = new CompositeAMQBodyBlock(channelId, deliverBody, contentHeaderBody, firstContentBody);
            writeFrame(compositeBlock);

            //
            // Now start writing out the other content bodies
            //
            for(int i = 1; i < bodyCount; i++)
            {
                cb = messageHandle.getContentChunk(storeContext, i);
                writeFrame(new AMQFrame(channelId, PROTOCOL_METHOD_CONVERTER.convertToBody(cb)));
            }


        }
        
    }

    private AMQDataBlock createContentHeaderBlock(final int channelId, final ContentHeaderBody contentHeaderBody)
    {
        
        AMQDataBlock contentHeader = ContentHeaderBody.createAMQFrame(channelId,
                                                                      contentHeaderBody);
        return contentHeader;
    }


    public void writeGetOk(QueueEntry queueEntry, int channelId, long deliveryTag, int queueSize) throws AMQException
    {

        final AMQMessage message = queueEntry.getMessage();
        final AMQMessageHandle messageHandle = message.getMessageHandle();
        final StoreContext storeContext = message.getStoreContext();

        AMQFrame deliver = createEncodedGetOkFrame(queueEntry, channelId, deliveryTag, queueSize);


        AMQDataBlock contentHeader = createContentHeaderBlock(channelId, message.getContentHeaderBody());

        final int bodyCount = messageHandle.getBodyCount(storeContext);
        if(bodyCount == 0)
        {
            SmallCompositeAMQDataBlock compositeBlock = new SmallCompositeAMQDataBlock(deliver,
                                                                             contentHeader);
            writeFrame(compositeBlock);
        }
        else
        {


            //
            // Optimise the case where we have a single content body. In that case we create a composite block
            // so that we can writeDeliver out the deliver, header and body with a single network writeDeliver.
            //
            ContentChunk cb = messageHandle.getContentChunk(storeContext, 0);

            AMQDataBlock firstContentBody = new AMQFrame(channelId, PROTOCOL_METHOD_CONVERTER.convertToBody(cb));
            AMQDataBlock[] blocks = new AMQDataBlock[]{deliver, contentHeader, firstContentBody};
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(blocks);
            writeFrame(compositeBlock);

            //
            // Now start writing out the other content bodies
            //
            for(int i = 1; i < bodyCount; i++)
            {
                cb = messageHandle.getContentChunk(storeContext, i);
                writeFrame(new AMQFrame(channelId, PROTOCOL_METHOD_CONVERTER.convertToBody(cb)));
            }


        }


    }


    private AMQBody createEncodedDeliverFrame(QueueEntry queueEntry, final int channelId, final long deliveryTag, final AMQShortString consumerTag)
            throws AMQException
    {
        AMQMessage message= queueEntry.getMessage();

        final MessagePublishInfo pb = message.getMessagePublishInfo();

        final boolean isRedelivered = queueEntry.isRedelivered();
        final AMQShortString exchangeName = pb.getExchange();
        final AMQShortString routingKey = pb.getRoutingKey();

        final AMQBody returnBlock = new AMQBody()
        {

            public AMQBody _underlyingBody;

            public AMQBody createAMQBody()
            {
                return METHOD_REGISTRY.createBasicDeliverBody(consumerTag,
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

            public void writePayload(ByteBuffer buffer)
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

    private AMQFrame createEncodedGetOkFrame(QueueEntry queueEntry, int channelId, long deliveryTag, int queueSize)
            throws AMQException
    {
        final AMQMessage message = queueEntry.getMessage();
        final MessagePublishInfo pb = message.getMessagePublishInfo();

        BasicGetOkBody getOkBody =
                METHOD_REGISTRY.createBasicGetOkBody(deliveryTag,
                                                    queueEntry.isRedelivered(),
                                                    pb.getExchange(),
                                                    pb.getRoutingKey(),
                                                    queueSize);
        AMQFrame getOkFrame = getOkBody.generateFrame(channelId);

        return getOkFrame;
    }

    public byte getProtocolMinorVersion()
    {
        return getProtocolSession().getProtocolMinorVersion();
    }

    public byte getProtocolMajorVersion()
    {
        return getProtocolSession().getProtocolMajorVersion();
    }

    private AMQDataBlock createEncodedReturnFrame(AMQMessage message, int channelId, int replyCode, AMQShortString replyText) throws AMQException
    {

        BasicReturnBody basicReturnBody =
                METHOD_REGISTRY.createBasicReturnBody(replyCode,
                                                     replyText,
                                                     message.getMessagePublishInfo().getExchange(),
                                                     message.getMessagePublishInfo().getRoutingKey());
        AMQFrame returnFrame = basicReturnBody.generateFrame(channelId);

        return returnFrame;
    }

    public void writeReturn(AMQMessage message, int channelId, int replyCode, AMQShortString replyText)
            throws AMQException
    {
        AMQDataBlock returnFrame = createEncodedReturnFrame(message, channelId, replyCode, replyText);

        AMQDataBlock contentHeader = createContentHeaderBlock(channelId, message.getContentHeaderBody());

        Iterator<AMQDataBlock> bodyFrameIterator = message.getBodyFrameIterator(getProtocolSession(), channelId);
        //
        // Optimise the case where we have a single content body. In that case we create a composite block
        // so that we can writeDeliver out the deliver, header and body with a single network writeDeliver.
        //
        if (bodyFrameIterator.hasNext())
        {
            AMQDataBlock firstContentBody = bodyFrameIterator.next();
            AMQDataBlock[] blocks = new AMQDataBlock[]{returnFrame, contentHeader, firstContentBody};
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(blocks);
            writeFrame(compositeBlock);
        }
        else
        {
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(new AMQDataBlock[]{returnFrame, contentHeader});

            writeFrame(compositeBlock);
        }

        //
        // Now start writing out the other content bodies
        // TODO: MINA needs to be fixed so the the pending writes buffer is not unbounded
        //
        while (bodyFrameIterator.hasNext())
        {
            writeFrame(bodyFrameIterator.next());
        }
    }


    public void writeFrame(AMQDataBlock block)
    {
        getProtocolSession().writeFrame(block);
    }


    public void confirmConsumerAutoClose(int channelId, AMQShortString consumerTag)
    {

        BasicCancelOkBody basicCancelOkBody = METHOD_REGISTRY.createBasicCancelOkBody(consumerTag);
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

        public void writePayload(ByteBuffer buffer)
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

        public void writePayload(ByteBuffer buffer)
        {
            AMQFrame.writeFrames(buffer, _channel, _methodBody, _headerBody);
        }
    }

}
