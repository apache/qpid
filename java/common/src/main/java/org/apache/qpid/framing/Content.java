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
	public enum ContentTypeEnum
    {
    	CONTENT_TYPE_INLINE((byte)0), CONTENT_TYPE_REFERENCE((byte)1);
        private byte type;
        ContentTypeEnum(byte type) { this.type = type; }
        public byte toByte() { return type; }
        public static ContentTypeEnum toContentEnum(byte b)
        {
        	switch (b)
            {
            	case 0: return CONTENT_TYPE_INLINE;
            	case 1: return CONTENT_TYPE_REFERENCE;
                default: throw new IllegalArgumentException("Illegal value " + b +
                	", not represented in ContentTypeEnum.");
            }
        }
    }
    
    public ContentTypeEnum contentType;
    public ByteBuffer content;
    
    // Constructors
    
    public Content()
    {
    	contentType = ContentTypeEnum.CONTENT_TYPE_INLINE; // default
        content = null;
    }
    
    public Content(ContentTypeEnum contentType, byte[] content)
    {
    	if (contentType == ContentTypeEnum.CONTENT_TYPE_REFERENCE)
        {
        	if (content == null)
            	throw new IllegalArgumentException("Content cannot be null for a ref type.");
        	if (content.length == 0)
            	throw new IllegalArgumentException("Content cannot be empty for a ref type.");
        }
    	this.contentType = contentType;
        this.content = ByteBuffer.allocate(content.length);
        this.content.put(content);
    }
    
    public Content(ContentTypeEnum contentType, String contentStr)
    {
        this(contentType, contentStr.getBytes());
    }
    
    public Content(ContentTypeEnum contentType, ByteBuffer content)
    {
    	if (contentType == ContentTypeEnum.CONTENT_TYPE_REFERENCE)
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
    
    public ContentTypeEnum getContentType() { return contentType; }
    public ByteBuffer getContent() { return content; }
    
    public byte[] getContentAsByteArray()
    {
        byte[] ba = new byte[content.remaining()];
        content.get(ba);
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
    	EncodingUtils.writeUnsignedInteger(buffer, content.remaining());
        buffer.put(content);
    }
    
    public void populateFromBuffer(ByteBuffer buffer) throws AMQFrameDecodingException
    {
        contentType = ContentTypeEnum.toContentEnum(buffer.get());
        int length = buffer.getInt();
        content = buffer.slice();
        buffer.skip(length);
        content.limit(length);
    }
    
    public String toString()
    {
        return content.toString();
    }
}
