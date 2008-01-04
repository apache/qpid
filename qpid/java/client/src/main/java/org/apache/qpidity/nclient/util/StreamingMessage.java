package org.apache.qpidity.nclient.util;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.qpidity.transport.DeliveryProperties;
import org.apache.qpidity.transport.MessageProperties;
import org.apache.qpidity.transport.Header;
import org.apache.qpidity.api.Message;

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
    public long getMessageTransferId()
    {
        throw new UnsupportedOperationException();
    }
}
