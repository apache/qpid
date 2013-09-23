package org.apache.qpid.server.store;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.qpid.framing.EncodingUtils;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.util.ByteBufferOutputStream;

public class TestMessageMetaData implements StorableMessageMetaData
{
    public static final MessageMetaDataType.Factory<TestMessageMetaData> FACTORY = new TestMessageMetaDataFactory();

    private static final TestMessageMetaDataType TYPE = new TestMessageMetaDataType();

    private int _contentSize;
    private long _messageId;

    public TestMessageMetaData(long messageId, int contentSize)
    {
        _contentSize = contentSize;
        _messageId = messageId;
    }

    @Override
    public int getContentSize()
    {
        return _contentSize;
    }

    @Override
    public int getStorableSize()
    {
        int storeableSize = 8 + //message id, long, 8bytes/64bits
                            4;  //content size, int, 4bytes/32bits

        return storeableSize;
    }

    @Override
    public MessageMetaDataType getType()
    {
        return TYPE;
    }

    @Override
    public boolean isPersistent()
    {
        return true;
    }

    @Override
    public int writeToBuffer(int offsetInMetaData, ByteBuffer dest)
    {
        int oldPosition = dest.position();
        try
        {
            DataOutputStream dataOutputStream = new DataOutputStream(new ByteBufferOutputStream(dest));
            EncodingUtils.writeLong(dataOutputStream, _messageId);
            EncodingUtils.writeInteger(dataOutputStream, _contentSize);
        }
        catch (IOException e)
        {
            // This shouldn't happen as we are not actually using anything that can throw an IO Exception
            throw new RuntimeException(e);
        }

        return dest.position() - oldPosition;
    };
}
