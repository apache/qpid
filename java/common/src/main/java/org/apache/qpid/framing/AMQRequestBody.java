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

public class AMQRequestBody extends AMQBody
{
    public static final byte TYPE = (byte)AmqpConstants.frameRequestAsInt();

    // Fields declared in specification
    protected long requestId;
    protected long responseMark;
    protected AMQMethodBody methodPayload;
    
    // Constructor
    public AMQRequestBody()
    {
    }
    
    public AMQRequestBody(long requestId, long responseMark,
            AMQMethodBody methodPayload)
    {
        this.requestId = requestId;
        this.responseMark = responseMark;
        this.methodPayload = methodPayload;
    }


    // Field methods
    public long getRequestId() { return requestId; }
    public long getResponseMark() { return responseMark; }
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
        EncodingUtils.writeLong(buffer, requestId);
        EncodingUtils.writeLong(buffer, responseMark);
        EncodingUtils.writeInteger(buffer, 0); // reserved, set to 0
        methodPayload.writePayload(buffer);
    }
    
    protected void populateFromBuffer(ByteBuffer buffer, long size)
        throws AMQFrameDecodingException
    {   
        requestId = EncodingUtils.readLong(buffer);
        responseMark = EncodingUtils.readLong(buffer);
        int reserved = EncodingUtils.readInteger(buffer); // reserved, throw away
        
        AMQMethodBodyFactory methodBodyFactory = new AMQMethodBodyFactory();
        methodPayload = methodBodyFactory.createBody(buffer, size);
    }
    
    public String toString()
    {
        return "Req[" + requestId + " " + responseMark + "] C" +
            methodPayload.getClazz() + " M" + methodPayload.getMethod();
    }
    
    public static AMQFrame createAMQFrame(int channelId, long requestId,
            long responseMark, AMQMethodBody methodPayload)
    {
        AMQRequestBody requestFrame = new AMQRequestBody(requestId, responseMark,
            methodPayload);
        return new AMQFrame(channelId, requestFrame);
    }
}
