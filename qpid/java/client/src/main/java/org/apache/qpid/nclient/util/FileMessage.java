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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.api.Message;

/**
 * FileMessage provides pull style semantics for
 * larges messages backed by a disk. 
 * Instead of loading all data into memeory it uses
 * FileChannel to map regions of the file into memeory
 * at a time.
 * 
 * The write methods are not supported. 
 * 
 * From the standpoint of performance it is generally 
 * only worth mapping relatively large files into memory.
 *    
 * FileMessage msg = new FileMessage(in,delProps,msgProps);
 * session.messageTransfer(dest,msg,0,0);
 * 
 * The messageTransfer method will read the file in chunks
 * and stream it.
 *
 */
public class FileMessage extends ReadOnlyMessage implements Message
{
    private FileChannel _fileChannel;
    private int _chunkSize;
    private long _fileSize;
    private long _pos = 0;
    
    public FileMessage(FileInputStream in,int chunkSize,DeliveryProperties deliveryProperties,MessageProperties messageProperties)throws IOException
    {
        _messageProperties = messageProperties;
        _deliveryProperties = deliveryProperties;
        
        _fileChannel = in.getChannel();
        _chunkSize = chunkSize;
        _fileSize = _fileChannel.size();
        
        if (_fileSize <= _chunkSize)
        {
            _chunkSize = (int)_fileSize;
        }
    }

    public void setHeader(Header header) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Header getHeader() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void readData(byte[] target) throws IOException
    {        
        throw new UnsupportedOperationException();              
    }
    
    public ByteBuffer readData() throws IOException
    {
        if (_pos == _fileSize)
        {
            throw new EOFException();
        }
        
        if (_pos + _chunkSize > _fileSize)
        {
            _chunkSize = (int)(_fileSize - _pos);
        }
        MappedByteBuffer bb = _fileChannel.map(FileChannel.MapMode.READ_ONLY, _pos, _chunkSize);        
        _pos += _chunkSize;
        return bb;
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
