package org.apache.qpid.nclient.util;
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


import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.api.Message;

public class StreamingMessage extends ReadOnlyMessage implements Message
{
    SocketChannel _socChannel;
    private int _chunkSize;
    private ByteBuffer _readBuf;

    public Header getHeader() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setHeader(Header header) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public StreamingMessage(SocketChannel in,int chunkSize,DeliveryProperties deliveryProperties,MessageProperties messageProperties)throws IOException
    {
        _messageProperties = messageProperties;
        _deliveryProperties = deliveryProperties;
        
        _socChannel = in;
        _chunkSize = chunkSize;
        _readBuf = ByteBuffer.allocate(_chunkSize);
    }
    
    public void readData(byte[] target) throws IOException
    {
        throw new UnsupportedOperationException(); 
    }

    public ByteBuffer readData() throws IOException
    {
        if(_socChannel.isConnected() && _socChannel.isOpen())
        {
            _readBuf.clear();
            _socChannel.read(_readBuf);
        }
        else
        {
            throw new EOFException("The underlying socket/channel has closed");
        }
        
        return _readBuf.duplicate();
    }
    
    /**
     * This message is used by an application user to
     * provide data to the client library using pull style
     * semantics. Since the message is not transfered yet, it
     * does not have a transfer id. Hence this method is not
     * applicable to this implementation.    
     */
    public int getMessageTransferId()
    {
        throw new UnsupportedOperationException();
    }
}
