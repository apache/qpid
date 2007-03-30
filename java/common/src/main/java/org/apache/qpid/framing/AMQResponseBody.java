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
    protected long responseId;
    protected long requestId;
    protected int batchOffset;
    protected AMQMethodBody methodPayload;

    // Constructor
    public AMQResponseBody()
    {
    }
    
    public AMQResponseBody(long responseId, long requestId,
            int batchOffset, AMQMethodBody methodPayload)
    {
        this.responseId = responseId;
        this.requestId = requestId;
        this.batchOffset = batchOffset;
        this.methodPayload = methodPayload;
    }

    // Field methods
    public long getResponseId() { return responseId; }
    public long getRequestId() { return requestId; }
    public int  getBatchOffset() { return batchOffset; }
    public AMQMethodBody getMethodPayload() { return methodPayload; }
    
    public byte getFrameType()
    {
        return TYPE;
    }
    
    protected int getSize()
    {
        return 8 + 8 + 4 + methodPayload.getSize();
    }
        
    protected void writePayload(ByteBuffer buffer)
    {
        EncodingUtils.writeLong(buffer, responseId);
        EncodingUtils.writeLong(buffer, requestId);
        // XXX
        EncodingUtils.writeInteger(buffer, batchOffset);
        methodPayload.writePayload(buffer);
    }
    
    protected void populateFromBuffer(ByteBuffer buffer, long size)
        throws AMQFrameDecodingException
    {            
        responseId = EncodingUtils.readLong(buffer);
        requestId = EncodingUtils.readLong(buffer);
        // XXX
        batchOffset = EncodingUtils.readInteger(buffer);

        AMQMethodBodyFactory methodBodyFactory = new AMQMethodBodyFactory();
        methodPayload = methodBodyFactory.createBody(buffer, size);
    }
    
    public String toString()
    {
        return "Res[" + responseId + " " + requestId + "-" + (requestId + batchOffset) + "] C" +
            methodPayload.getClazz() + " M" + methodPayload.getMethod();
    }
    
    public static AMQFrame createAMQFrame(int channelId, long responseId,
            long requestId, int batchOffset, AMQMethodBody methodPayload)
    {
        AMQResponseBody responseFrame = new AMQResponseBody(responseId,
            requestId, batchOffset, methodPayload);
        return new AMQFrame(channelId, responseFrame);
    }
}
