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

package org.apache.qpid.nclient.message;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public class AMQPApplicationMessage {

	private int bytesReceived = 0;
	private int channelId;
    private byte[] referenceId;
	private List<byte[]> contents = new LinkedList<byte[]>();
	private String deliveryTag;
    private boolean redeliveredFlag;
	private MessageHeaders messageHeaders;
    
	public AMQPApplicationMessage(MessageHeaders messageHeaders,byte[] content)
	{
		this.messageHeaders = messageHeaders;
        addContent(content);
	}
	
    public AMQPApplicationMessage(int channelId, byte[] referenceId)
    {
        this.channelId = channelId;
        this.referenceId = referenceId;
    }
    
    public AMQPApplicationMessage(int channelId, String deliveryTag, MessageHeaders messageHeaders, boolean redeliveredFlag)
    {
        this.channelId = channelId;
        this.deliveryTag = deliveryTag;
        this.messageHeaders = messageHeaders;
        this.redeliveredFlag = redeliveredFlag;
    }
    
    public AMQPApplicationMessage(int channelId, String deliveryTag, MessageHeaders messageHeaders, byte[] content, boolean redeliveredFlag)
    {
        this.channelId = channelId;
        this.deliveryTag = deliveryTag;
        this.messageHeaders = messageHeaders;
        this.redeliveredFlag = redeliveredFlag;
        addContent(content);
    }
    
	public void addContent(byte[] content)
    {
		contents.add(content);
		bytesReceived += content.length;
	}

    public int getBytesReceived()
    {
        return bytesReceived;
    }

    public int getChannelId()
    {
        return channelId;
    }
    
    public byte[] getReferenceId()
    {
        return referenceId;
    }
    
    public List<byte[]> getContents()
    {
        return contents;
    }
    
    public byte[] getContentsAsBytes()
    {
    	ByteBuffer buf = ByteBuffer.allocate(bytesReceived);
    	for (byte[] bytes: contents)
    	{
    		buf.put(bytes);
    	}
    	
    	return buf.array();
    }

    public String getDeliveryTag()
    {
        return deliveryTag;
    }
    
    public boolean getRedeliveredFlag()
    {
        return redeliveredFlag;
    }
    
    public MessageHeaders getMessageHeaders()
    {
        return messageHeaders;
    }
    
    public String toString()
    {
        return "UnprocessedMessage: ch=" + channelId + "; bytesReceived=" + bytesReceived + "; deliveryTag=" +
            deliveryTag + "; MsgHdrs=" + messageHeaders + "Num contents=" + contents.size() + "; First content=" +
            new String(contents.get(0));
    }

    public void setDeliveryTag(String deliveryTag)
    {
        this.deliveryTag = deliveryTag;
    }

	public void setMessageHeaders(MessageHeaders messageHeaders)
    {
		this.messageHeaders = messageHeaders;
	}

	public void setRedeliveredFlag(boolean redeliveredFlag)
    {
		this.redeliveredFlag = redeliveredFlag;
	}
}
