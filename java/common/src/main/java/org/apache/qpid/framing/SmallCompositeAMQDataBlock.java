package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;

public class SmallCompositeAMQDataBlock extends AMQDataBlock implements EncodableAMQDataBlock
{
    private ByteBuffer _encodedBlock;

    private AMQDataBlock _block;

    public SmallCompositeAMQDataBlock(AMQDataBlock block)
    {
        _block = block;
    }

    /**
     * The encoded block will be logically first before the AMQDataBlocks which are encoded
     * into the buffer afterwards.
     * @param encodedBlock already-encoded data
     * @param block a block to be encoded.
     */
    public SmallCompositeAMQDataBlock(ByteBuffer encodedBlock, AMQDataBlock block)
    {
        this(block);
        _encodedBlock = encodedBlock;
    }

    public AMQDataBlock getBlock()
    {
        return _block;
    }

    public ByteBuffer getEncodedBlock()
    {
        return _encodedBlock;
    }

    public long getSize()
    {
        long frameSize = _block.getSize();

        if (_encodedBlock != null)
        {
            _encodedBlock.rewind();
            frameSize += _encodedBlock.remaining();
        }
        return frameSize;
    }

    public void writePayload(ByteBuffer buffer)
    {
        if (_encodedBlock != null)
        {
            buffer.put(_encodedBlock);
        }
        _block.writePayload(buffer);

    }

    public String toString()
    {
        if (_block == null)
        {
            return "No blocks contained in composite frame";
        }
        else
        {
            StringBuilder buf = new StringBuilder(this.getClass().getName());
            buf.append("{encodedBlock=").append(_encodedBlock);

            buf.append(" _block=[").append(_block.toString()).append("]");

            buf.append("}");
            return buf.toString();
        }
    }
}
