package org.apache.qpid.server.output.amqp0_9;

import org.apache.mina.common.ByteBuffer;

import java.util.Iterator;

import org.apache.qpid.server.output.ProtocolOutputConverter;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQMessageHandle;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.framing.*;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.AMQException;

public class ProtocolOutputConverterImpl implements ProtocolOutputConverter
{
    private static final MethodRegistry METHOD_REGISTRY = MethodRegistry.getMethodRegistry(ProtocolVersion.v0_9);


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

    public void writeDeliver(AMQMessage message, int channelId, long deliveryTag, AMQShortString consumerTag)
            throws AMQException
    {
        AMQDataBlock deliver = createEncodedDeliverFrame(message, channelId, deliveryTag, consumerTag);
        final ContentHeaderBody contentHeaderBody = message.getContentHeaderBody();
        AMQDataBlock contentHeader = ContentHeaderBody.createAMQFrame(channelId,
                                                                      contentHeaderBody);

        final AMQMessageHandle messageHandle = message.getMessageHandle();
        final StoreContext storeContext = message.getStoreContext();
        final Long messageId = message.getMessageId();

        final int bodyCount = messageHandle.getBodyCount(storeContext,messageId);

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
            ContentChunk cb = messageHandle.getContentChunk(storeContext,messageId, 0);

            AMQDataBlock firstContentBody = new AMQFrame(channelId, getProtocolSession().getMethodRegistry().getProtocolVersionMethodConverter().convertToBody(cb));
            AMQDataBlock[] headerAndFirstContent = new AMQDataBlock[]{contentHeader, firstContentBody};
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(deliver, headerAndFirstContent);
            writeFrame(compositeBlock);

            //
            // Now start writing out the other content bodies
            //
            for(int i = 1; i < bodyCount; i++)
            {
                cb = messageHandle.getContentChunk(storeContext,messageId, i);
                writeFrame(new AMQFrame(channelId, getProtocolSession().getMethodRegistry().getProtocolVersionMethodConverter().convertToBody(cb)));
            }


        }


    }


    public void writeGetOk(AMQMessage message, int channelId, long deliveryTag, int queueSize) throws AMQException
    {

        final AMQMessageHandle messageHandle = message.getMessageHandle();
        final StoreContext storeContext = message.getStoreContext();
        final long messageId = message.getMessageId();

        AMQFrame deliver = createEncodedGetOkFrame(message, channelId, deliveryTag, queueSize);


        AMQDataBlock contentHeader = ContentHeaderBody.createAMQFrame(channelId,
                                                                      message.getContentHeaderBody());

        final int bodyCount = messageHandle.getBodyCount(storeContext,messageId);
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
            ContentChunk cb = messageHandle.getContentChunk(storeContext,messageId, 0);

            AMQDataBlock firstContentBody = new AMQFrame(channelId, getProtocolSession().getMethodRegistry().getProtocolVersionMethodConverter().convertToBody(cb));
            AMQDataBlock[] headerAndFirstContent = new AMQDataBlock[]{contentHeader, firstContentBody};
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(deliver, headerAndFirstContent);
            writeFrame(compositeBlock);

            //
            // Now start writing out the other content bodies
            //
            for(int i = 1; i < bodyCount; i++)
            {
                cb = messageHandle.getContentChunk(storeContext, messageId, i);
                writeFrame(new AMQFrame(channelId, getProtocolSession().getMethodRegistry().getProtocolVersionMethodConverter().convertToBody(cb)));
            }


        }


    }


    private AMQDataBlock createEncodedDeliverFrame(AMQMessage message, final int channelId, final long deliveryTag, final AMQShortString consumerTag)
            throws AMQException
    {
        final MessagePublishInfo pb = message.getMessagePublishInfo();
        final AMQMessageHandle messageHandle = message.getMessageHandle();


        final boolean isRedelivered = messageHandle.isRedelivered();
        final AMQShortString exchangeName = pb.getExchange();
        final AMQShortString routingKey = pb.getRoutingKey();

        final AMQDataBlock returnBlock = new DeferredDataBlock()
        {

            protected AMQDataBlock createAMQDataBlock()
            {
                BasicDeliverBody deliverBody =
                        METHOD_REGISTRY.createBasicDeliverBody(consumerTag,
                                                              deliveryTag,
                                                              isRedelivered,
                                                              exchangeName,
                                                              routingKey);
                AMQFrame deliverFrame = deliverBody.generateFrame(channelId);


                return deliverFrame;

            }
        };
        return returnBlock;
    }

    private AMQFrame createEncodedGetOkFrame(AMQMessage message, int channelId, long deliveryTag, int queueSize)
            throws AMQException
    {
        final MessagePublishInfo pb = message.getMessagePublishInfo();
        final AMQMessageHandle messageHandle = message.getMessageHandle();


        BasicGetOkBody getOkBody =
                METHOD_REGISTRY.createBasicGetOkBody(deliveryTag,
                                                    messageHandle.isRedelivered(),
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

        AMQDataBlock contentHeader = ContentHeaderBody.createAMQFrame(channelId,
                                                                      message.getContentHeaderBody());

        Iterator<AMQDataBlock> bodyFrameIterator = message.getBodyFrameIterator(getProtocolSession(), channelId);
        //
        // Optimise the case where we have a single content body. In that case we create a composite block
        // so that we can writeDeliver out the deliver, header and body with a single network writeDeliver.
        //
        if (bodyFrameIterator.hasNext())
        {
            AMQDataBlock firstContentBody = bodyFrameIterator.next();
            AMQDataBlock[] headerAndFirstContent = new AMQDataBlock[]{contentHeader, firstContentBody};
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(returnFrame, headerAndFirstContent);
            writeFrame(compositeBlock);
        }
        else
        {
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(returnFrame,
                                                                             new AMQDataBlock[]{contentHeader});

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
}
