package org.apache.qpid.framing.amqp_0_9;

import org.apache.mina.common.ByteBuffer;

import org.apache.qpid.framing.abstraction.AbstractMethodConverter;
import org.apache.qpid.framing.abstraction.ProtocolVersionMethodConverter;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.*;
import org.apache.qpid.framing.amqp_0_9.*;
import org.apache.qpid.framing.amqp_0_9.BasicPublishBodyImpl;

public class MethodConverter_0_9 extends AbstractMethodConverter implements ProtocolVersionMethodConverter
{
    private int _basicPublishClassId;
    private int _basicPublishMethodId;

    public MethodConverter_0_9()
    {
        super((byte)0,(byte)9);


    }

    public AMQBody convertToBody(ContentChunk contentChunk)
    {
        return new ContentBody(contentChunk.getData());
    }

    public ContentChunk convertToContentChunk(AMQBody body)
    {
        final ContentBody contentBodyChunk = (ContentBody) body;

        return new ContentChunk()
        {

            public int getSize()
            {
                return contentBodyChunk.getSize();
            }

            public ByteBuffer getData()
            {
                return contentBodyChunk.payload;
            }

            public void reduceToFit()
            {
                contentBodyChunk.reduceBufferToFit();
            }
        };

    }

    public void configure()
    {

        _basicPublishClassId = org.apache.qpid.framing.amqp_0_9.BasicPublishBodyImpl.CLASS_ID;
        _basicPublishMethodId = BasicPublishBodyImpl.METHOD_ID;

    }

    public MessagePublishInfo convertToInfo(AMQMethodBody methodBody)
    {
        final BasicPublishBody publishBody = ((BasicPublishBody) methodBody);

        final AMQShortString exchange = publishBody.getExchange();
        final AMQShortString routingKey = publishBody.getRoutingKey();

        return new MethodConverter_0_9.MessagePublishInfoImpl(exchange == null ? null : exchange.intern(),
                                          publishBody.getImmediate(),
                                          publishBody.getMandatory(),
                                          routingKey == null ? null : routingKey.intern());

    }

    public AMQMethodBody convertToBody(MessagePublishInfo info)
    {

        return new BasicPublishBodyImpl(0,
                                    info.getExchange(),
                                    info.getRoutingKey(),
                                    info.isMandatory(),
                                    info.isImmediate()) ;

    }

    private static class MessagePublishInfoImpl implements MessagePublishInfo
    {
        private final AMQShortString _exchange;
        private final boolean _immediate;
        private final boolean _mandatory;
        private final AMQShortString _routingKey;

        public MessagePublishInfoImpl(final AMQShortString exchange,
                                      final boolean immediate,
                                      final boolean mandatory,
                                      final AMQShortString routingKey)
        {
            _exchange = exchange;
            _immediate = immediate;
            _mandatory = mandatory;
            _routingKey = routingKey;
        }

        public AMQShortString getExchange()
        {
            return _exchange;
        }

        public boolean isImmediate()
        {
            return _immediate;
        }

        public boolean isMandatory()
        {
            return _mandatory;
        }

        public AMQShortString getRoutingKey()
        {
            return _routingKey;
        }
    }
}
