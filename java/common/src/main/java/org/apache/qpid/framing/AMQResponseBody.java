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
package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;

public class AMQResponseBody extends AMQBody
{
    public static final byte TYPE = (byte)AmqpConstants.frameResponseAsInt();
       
    // Fields declared in specification
    public long responseId;
    public long requestId;
    public int batchOffset;
    public AMQMethodBody methodPayload;

    // Constructor
    public AMQResponseBody() {}

    // Field methods
    public long getResponseId() { return responseId; }
    public long getRequestId() { return requestId; }
    public int  getBatchOffset() { return batchOffset; }
    public AMQMethodBody getMethodPayload() { return methodPayload; }
    
    protected byte getFrameType()
    {
    	return TYPE;
    }
    
    protected int getSize()
    {
    	return 8 + 8 + 4 + methodPayload.getBodySize();
    }
        
    protected void writePayload(ByteBuffer buffer)
    {
        EncodingUtils.writeLong(buffer, responseId);
        EncodingUtils.writeLong(buffer, requestId);
        EncodingUtils.writeUnsignedShort(buffer, batchOffset);
        methodPayload.writePayload(buffer);
    }
    
    protected void populateFromBuffer(ByteBuffer buffer, long size)
        throws AMQFrameDecodingException, AMQProtocolVersionException
    {
        responseId = EncodingUtils.readLong(buffer);
        requestId = EncodingUtils.readLong(buffer);
        batchOffset = EncodingUtils.readShort(buffer);
        methodPayload.populateFromBuffer(buffer, size - 8 - 8 - 4);
    }
    
    public static AMQFrame createAMQFrame(int channelId, long requestId,
            long responseMark, int batchOffset, AMQMethodBody methodPayload)
    {
        AMQResponseBody responseFrame = new AMQResponseBody();
        responseFrame.responseId = responseId;
        responseFrame.requestId = requestId;
        responseFrame.batchOffset = batchOffset;
        responseFrame.methodPayload = methodPayload;
        
        AMQFrame frame = new AMQFrame();
        frame.channel = channelId;
        frame.bodyFrame = responseFrame;
        return frame;
    }
}
