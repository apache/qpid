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
package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.store.StoreContext;

import org.apache.mina.common.ByteBuffer;

import java.util.LinkedList;
import java.util.List;

/**
 * This class agregates a message reference, which consists of
 * acculated content and one or more transfers to which it will
 * be routed.
 */
public class AMQReference
{
    private byte[] ref;
    private List<AMQMessage> messageList = new LinkedList();
    private List<ByteBuffer> contentList = new LinkedList();
    
    public AMQReference(byte[] ref)
    {
        this.ref = ref;
    }
    
    public byte[] getReference()
    {
        return ref;
    }
    
    public String getReferenceAsString()
    {
        return new String(ref);
    }
    
    public void addRefTransferBody(AMQMessage msg)
    {
        messageList.add(msg);
    }
    
    public List<AMQMessage> getMessageList()
    {
        return messageList;
    }
    
    public void appendContent(ByteBuffer content)
    {
        contentList.add(content);
    }
    
    public List<ByteBuffer> getContentList()
    {
        return contentList;
    }
    
    public long totalContentLength()
    {
        long tot = 0L;
        for (ByteBuffer bb : contentList)
        {
            tot += bb.limit();
        }
        return tot;
    }
    
    public void close(StoreContext storeContext) throws AMQException
    {
        throw new Error("XXX");
    }
    
    public String toString()
    {
        return "AMQReference " + getReferenceAsString() + ": Num messages=" + messageList.size() + "; Num contents=" + contentList.size() + "; Tot content length=" + totalContentLength();
    }
}
