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

public class Content
{
	public enum TypeEnum
    {
    	INLINE_T((byte)0), REF_T((byte)1);
        private byte type;
        TypeEnum(byte type) { this.type = type; }
        public byte toByte() { return type; }
        public static TypeEnum toContentEnum(byte b)
        {
        	switch (b)
            {
            	case 0: return INLINE_T;
            	case 1: return REF_T;
                default: throw new IllegalArgumentException("Illegal value " + b +
                	", not represented in TypeEnum.");
            }
        }
    }
    
    private TypeEnum contentType;
    private ByteBuffer content;
    
    // Constructors
    
    public Content()
    {
    	contentType = TypeEnum.INLINE_T; // default
        content = null;
    }
    
    public Content(TypeEnum contentType, byte[] content)
    {
    	if (contentType == TypeEnum.REF_T)
        {
        	if (content == null)
            	throw new IllegalArgumentException("Content cannot be null for a ref type.");
        	if (content.length == 0)
            	throw new IllegalArgumentException("Content cannot be empty for a ref type.");
        }
    	this.contentType = contentType;
        this.content = ByteBuffer.allocate(content.length);
        this.content.put(content);
        this.content.flip();
    }
    
    public Content(TypeEnum contentType, String contentStr)
    {
        this(contentType, contentStr.getBytes());
    }
    
    public Content(TypeEnum contentType, ByteBuffer content)
    {
    	if (contentType == TypeEnum.REF_T)
        {
        	if (content == null)
            	throw new IllegalArgumentException("Content cannot be null for a ref type.");
        	if (content.array().length == 0)
            	throw new IllegalArgumentException("Content cannot be empty for a ref type.");
        }
    	this.contentType = contentType;
        this.content = content;
    }
    
    // Get functions
    
    public TypeEnum getContentType()
    {
        return contentType;
    }
    
    public ByteBuffer getContent()
    {
        return content.duplicate();
    }
    
    public byte[] getContentAsByteArray()
    {
        ByteBuffer dup = content.duplicate();
        byte[] ba = new byte[dup.remaining()];
        dup.get(ba);
        return ba;
    }
    
    public String getContentAsString()
    {
    	if (content == null)
        	return null;
        return new String(getContentAsByteArray());
    }
    
    // Wire functions
    
    public long getEncodedSize()
    {
    	if (content == null)
    		return 1 + 4;
     	return 1 + 4 + content.remaining();   
    }
    
    public void writePayload(ByteBuffer buffer)
    {
    	EncodingUtils.writeUnsignedByte(buffer, contentType.toByte());
        if (content == null) {
            EncodingUtils.writeUnsignedInteger(buffer, 0);
        } else {
            EncodingUtils.writeUnsignedInteger(buffer, content.remaining());
            buffer.put(content.duplicate());
        }
    }
    
    public void populateFromBuffer(ByteBuffer buffer) throws AMQFrameDecodingException
    {
        contentType = TypeEnum.toContentEnum(buffer.get());
        int length = buffer.getInt();
        content = buffer.slice();
        buffer.skip(length);
        content.limit(length);
    }
    
    public synchronized String toString()
    {
        return getContent().toString();
    }
}
