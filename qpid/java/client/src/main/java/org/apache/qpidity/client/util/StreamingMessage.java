package org.apache.qpidity.client.util;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.qpidity.DeliveryProperties;
import org.apache.qpidity.MessageProperties;
import org.apache.qpidity.api.Message;

public class StreamingMessage extends ReadOnlyMessage implements Message
{
    SocketChannel _socChannel;
    private int _chunkSize;
    private ByteBuffer _readBuf;
    
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

}
