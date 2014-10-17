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
package org.apache.qpid.codec;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import junit.framework.TestCase;

import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.framing.AMQProtocolVersionException;
import org.apache.qpid.framing.FrameCreatingMethodProcessor;
import org.apache.qpid.framing.HeartbeatBody;
import org.apache.qpid.framing.ProtocolVersion;

public class AMQDecoderTest extends TestCase
{

    private AMQDecoder _decoder;
    private FrameCreatingMethodProcessor _methodProcessor;


    public void setUp()
    {
        _methodProcessor = new FrameCreatingMethodProcessor(ProtocolVersion.v0_91);
        _decoder = new ClientDecoder(_methodProcessor);
    }
   
    
    private ByteBuffer getHeartbeatBodyBuffer() throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        HeartbeatBody.FRAME.writePayload(new DataOutputStream(baos));
        return ByteBuffer.wrap(baos.toByteArray());
    }
    
    public void testSingleFrameDecode() throws AMQProtocolVersionException, AMQFrameDecodingException, IOException
    {
        ByteBuffer msg = getHeartbeatBodyBuffer();
        _decoder.decodeBuffer(msg);
        List<AMQDataBlock> frames = _methodProcessor.getProcessedMethods();
        if (frames.get(0) instanceof AMQFrame)
        {
            assertEquals(HeartbeatBody.FRAME.getBodyFrame().getFrameType(), ((AMQFrame) frames.get(0)).getBodyFrame().getFrameType());
        }
        else
        {
            fail("decode was not a frame");
        }
    }
    
    public void testPartialFrameDecode() throws AMQProtocolVersionException, AMQFrameDecodingException, IOException
    {
        ByteBuffer msg = getHeartbeatBodyBuffer();
        ByteBuffer msgA = msg.slice();
        int msgbPos = msg.remaining() / 2;
        int msgaLimit = msg.remaining() - msgbPos;
        msgA.limit(msgaLimit);
        msg.position(msgbPos);
        ByteBuffer msgB = msg.slice();

        _decoder.decodeBuffer(msgA);
        List<AMQDataBlock> frames = _methodProcessor.getProcessedMethods();
        assertEquals(0, frames.size());

        _decoder.decodeBuffer(msgB);
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
    
    public void testMultipleFrameDecode() throws AMQProtocolVersionException, AMQFrameDecodingException, IOException
    {
        ByteBuffer msgA = getHeartbeatBodyBuffer();
        ByteBuffer msgB = getHeartbeatBodyBuffer();
        ByteBuffer msg = ByteBuffer.allocate(msgA.remaining() + msgB.remaining());
        msg.put(msgA);
        msg.put(msgB);
        msg.flip();
        _decoder.decodeBuffer(msg);
        List<AMQDataBlock> frames = _methodProcessor.getProcessedMethods();
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
    
    public void testMultiplePartialFrameDecode() throws AMQProtocolVersionException, AMQFrameDecodingException, IOException
    {
        ByteBuffer msgA = getHeartbeatBodyBuffer();
        ByteBuffer msgB = getHeartbeatBodyBuffer();
        ByteBuffer msgC = getHeartbeatBodyBuffer();
        
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

        _decoder.decodeBuffer(sliceA);
        List<AMQDataBlock> frames = _methodProcessor.getProcessedMethods();
        assertEquals(1, frames.size());
        frames.clear();
        _decoder.decodeBuffer(sliceB);
        assertEquals(1, frames.size());
        frames.clear();
        _decoder.decodeBuffer(msgC);
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
