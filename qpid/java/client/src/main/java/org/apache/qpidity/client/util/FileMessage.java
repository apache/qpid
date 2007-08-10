package org.apache.qpidity.client.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.qpidity.DeliveryProperties;
import org.apache.qpidity.MessageProperties;
import org.apache.qpidity.api.Message;

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
public class FileMessage implements Message
{
    private MessageProperties _messageProperties;
    private DeliveryProperties _deliveryProperties;
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
    
    public void appendData(byte[] src)
    {
        throw new UnsupportedOperationException("This Message is read only after the initial source");
    }

    public void appendData(ByteBuffer src)
    {
        throw new UnsupportedOperationException("This Message is read only after the initial source");
    }

    public DeliveryProperties getDeliveryProperties()
    {
        return _deliveryProperties;
    }

    public MessageProperties getMessageProperties()
    {
        return _messageProperties;
    }

    public void readData(byte[] target) throws IOException
    {        
        int readLen = target.length <= _chunkSize ? target.length : _chunkSize;
        if (_pos + readLen > _fileSize)
        {
            readLen = (int)(_fileSize - _pos);
        }
        MappedByteBuffer bb = _fileChannel.map(FileChannel.MapMode.READ_ONLY, _pos, readLen);
        _pos += readLen;
        bb.get(target);
    }
    
    public ByteBuffer readData() throws IOException
    {
        if (_pos + _chunkSize > _fileSize)
        {
            _chunkSize = (int)(_fileSize - _pos);
        }
        MappedByteBuffer bb = _fileChannel.map(FileChannel.MapMode.READ_ONLY, _pos, _chunkSize);
        _pos += _chunkSize;
        return bb;
    }

}
