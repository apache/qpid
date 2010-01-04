package org.apache.qpid.codec;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import junit.framework.TestCase;

import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.framing.AMQProtocolVersionException;
import org.apache.qpid.framing.HeartbeatBody;

public class AMQDecoderTest extends TestCase
{

    private AMQCodecFactory _factory;
    private AMQDecoder _decoder;


    public void setUp()
    {
        _factory = new AMQCodecFactory(false, null);
        _decoder = _factory.getDecoder();
    }
   
    
    public void testSingleFrameDecode() throws AMQProtocolVersionException, AMQFrameDecodingException
    {
        ByteBuffer msg = HeartbeatBody.FRAME.toNioByteBuffer();
        ArrayList<AMQDataBlock> frames = _decoder.decodeBuffer(msg);
        if (frames.get(0) instanceof AMQFrame)
        {
            assertEquals(HeartbeatBody.FRAME.getBodyFrame().getFrameType(), ((AMQFrame) frames.get(0)).getBodyFrame().getFrameType());
        }
        else
        {
            fail("decode was not a frame");
        }
    }
    
    public void testPartialFrameDecode() throws AMQProtocolVersionException, AMQFrameDecodingException
    {
        ByteBuffer msg = HeartbeatBody.FRAME.toNioByteBuffer();
        ByteBuffer msgA = msg.slice();
        int msgbPos = msg.remaining() / 2;
        int msgaLimit = msg.remaining() - msgbPos;
        msgA.limit(msgaLimit);
        msg.position(msgbPos);
        ByteBuffer msgB = msg.slice();
        ArrayList<AMQDataBlock> frames = _decoder.decodeBuffer(msgA);
        assertEquals(0, frames.size());
        frames = _decoder.decodeBuffer(msgB);
        assertEquals(1, frames.size());
        if (frames.get(0) instanceof AMQFrame)
        {
            assertEquals(HeartbeatBody.FRAME.getBodyFrame().getFrameType(), ((AMQFrame) frames.get(0)).getBodyFrame().getFrameType());
        }
        else
        {
            fail("decode was not a frame");
        }
    }
    
    public void testMultipleFrameDecode() throws AMQProtocolVersionException, AMQFrameDecodingException
    {
        ByteBuffer msgA = HeartbeatBody.FRAME.toNioByteBuffer();
        ByteBuffer msgB = HeartbeatBody.FRAME.toNioByteBuffer();
        ByteBuffer msg = ByteBuffer.allocate(msgA.remaining() + msgB.remaining());
        msg.put(msgA);
        msg.put(msgB);
        msg.flip();
        ArrayList<AMQDataBlock> frames = _decoder.decodeBuffer(msg);
        assertEquals(2, frames.size());
        for (AMQDataBlock frame : frames)
        {
            if (frame instanceof AMQFrame)
            {
                assertEquals(HeartbeatBody.FRAME.getBodyFrame().getFrameType(), ((AMQFrame) frame).getBodyFrame().getFrameType());
            }
            else
            {
                fail("decode was not a frame");
            }
        }
    }
    
    public void testMultiplePartialFrameDecode() throws AMQProtocolVersionException, AMQFrameDecodingException
    {
        ByteBuffer msgA = HeartbeatBody.FRAME.toNioByteBuffer();
        ByteBuffer msgB = HeartbeatBody.FRAME.toNioByteBuffer();
        ByteBuffer msgC = HeartbeatBody.FRAME.toNioByteBuffer();
        
        ByteBuffer sliceA = ByteBuffer.allocate(msgA.remaining() + msgB.remaining() / 2);
        sliceA.put(msgA);
        int limit = msgB.limit();
        int pos = msgB.remaining() / 2;
        msgB.limit(pos);
        sliceA.put(msgB);
        sliceA.flip();
        msgB.limit(limit);
        msgB.position(pos);
        
        ByteBuffer sliceB = ByteBuffer.allocate(msgB.remaining() + pos);
        sliceB.put(msgB);
        msgC.limit(pos);
        sliceB.put(msgC);
        sliceB.flip();
        msgC.limit(limit);
        
        ArrayList<AMQDataBlock> frames = _decoder.decodeBuffer(sliceA);
        assertEquals(1, frames.size());
        frames = _decoder.decodeBuffer(sliceB);
        assertEquals(1, frames.size());
        frames = _decoder.decodeBuffer(msgC);
        assertEquals(1, frames.size());
        for (AMQDataBlock frame : frames)
        {
            if (frame instanceof AMQFrame)
            {
                assertEquals(HeartbeatBody.FRAME.getBodyFrame().getFrameType(), ((AMQFrame) frame).getBodyFrame().getFrameType());
            }
            else
            {
                fail("decode was not a frame");
            }
        }
    }
    
}
