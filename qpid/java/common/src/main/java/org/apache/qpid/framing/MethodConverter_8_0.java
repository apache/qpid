package org.apache.qpid.framing;

import org.apache.qpid.framing.abstraction.ProtocolVersionMethodConverter;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.AbstractMethodConverter;
import org.apache.qpid.framing.amqp_8_0.BasicPublishBodyImpl;

import org.apache.mina.common.ByteBuffer;

public class MethodConverter_8_0 extends AbstractMethodConverter implements ProtocolVersionMethodConverter
{
    private int _basicPublishClassId;
    private int _basicPublishMethodId;

    public MethodConverter_8_0()
    {
        super((byte)8,(byte)0);


    }

    public AMQBodyImpl convertToBody(ContentChunk contentChunk)
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

        _basicPublishClassId = BasicPublishBodyImpl.CLASS_ID;
        _basicPublishMethodId = BasicPublishBodyImpl.METHOD_ID;
                
    }

    public MessagePublishInfo convertToInfo(AMQMethodBody methodBody)
    {
        final BasicPublishBody body = (BasicPublishBody) methodBody;
        
        return new MessagePublishInfo()
        {

            public AMQShortString getExchange()
            {
                return body.getExchange();
            }

            public boolean isImmediate()
            {
                return body.getImmediate();
            }

            public boolean isMandatory()
            {
                return body.getMandatory();
            }

            public AMQShortString getRoutingKey()
            {
                return body.getRoutingKey();
            }
        };

    }

    public AMQMethodBodyImpl convertToBody(MessagePublishInfo info)
    {

        return new BasicPublishBodyImpl(0, // ticket
                                        info.getExchange(),
                                        info.getRoutingKey(),
                                    info.isMandatory(),
                                    info.isImmediate()
                                    ) ;

    }
}
