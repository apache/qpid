package org.apache.qpid.framing;

import org.apache.qpid.framing.abstraction.ProtocolVersionMethodConverter;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.AbstractMethodConverter;

import org.apache.mina.common.ByteBuffer;

public class MethodConverter_8_0 extends AbstractMethodConverter implements ProtocolVersionMethodConverter
{
    private int _basicPublishClassId;
    private int _basicPublishMethodId;

    public MethodConverter_8_0()
    {
        super((byte)8,(byte)0);


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

        _basicPublishClassId = BasicPublishBody.getClazz(getProtocolMajorVersion(),getProtocolMinorVersion());
        _basicPublishMethodId = BasicPublishBody.getMethod(getProtocolMajorVersion(),getProtocolMinorVersion());
                
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

    public AMQMethodBody convertToBody(MessagePublishInfo info)
    {

        return new BasicPublishBody(getProtocolMajorVersion(),
                                    getProtocolMinorVersion(),
                                    _basicPublishClassId,
                                    _basicPublishMethodId,
                                    info.getExchange(),
                                    info.isImmediate(),
                                    info.isMandatory(),
                                    info.getRoutingKey(),
                                    0) ; // ticket

    }
}
