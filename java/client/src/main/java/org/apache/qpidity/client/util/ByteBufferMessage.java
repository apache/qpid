package org.apache.qpidity.client.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.qpidity.DeliveryProperties;
import org.apache.qpidity.MessageProperties;
import org.apache.qpidity.api.Message;

/**
 * <p>A Simple implementation of the message interface
 * for small messages. When the readData methods are called
 * we assume the message is complete. i.e there want be any
 * appendData operations after that.</p>
 * 
 * <p>If you need large message support please see 
 * <code>FileMessage</code> and <code>StreamingMessage</code>
 * </p>
 */
public class ByteBufferMessage implements Message
{
    private Queue<ByteBuffer> _data = new LinkedList<ByteBuffer>();
    private ByteBuffer _readBuffer;
    private int _dataSize; 
    private DeliveryProperties _currentDeliveryProps;
    private MessageProperties _currentMessageProps;
    private long _transferId;
    
    public ByteBufferMessage(long transferId)
    {
        _transferId = transferId;
    }    
    
    public long getMessageTransferId()
    {
        return _transferId;
    }
    
    public void clearData()
    {
        _data = new LinkedList<ByteBuffer>();
        _readBuffer = null;
    }
        
    public void appendData(byte[] src) throws IOException
    {
        appendData(ByteBuffer.wrap(src));
    }

    /**
     * write the data from the current position up to the buffer limit
     */
    public void appendData(ByteBuffer src) throws IOException
    {
        _data.offer(src);
        _dataSize += src.remaining();        
    }
    
    public DeliveryProperties getDeliveryProperties()
    {
        return _currentDeliveryProps;
    }

    public MessageProperties getMessageProperties()
    {
        return _currentMessageProps;
    }
    
    public void setDeliveryProperties(DeliveryProperties props)
    {
        _currentDeliveryProps = props;
    }

    public void setMessageProperties(MessageProperties props)
    {
        _currentMessageProps = props;
    }
    
    public void readData(byte[] target) throws IOException
    {
        if (_data.size() >0 && _readBuffer == null)
        {
            buildReadBuffer();
        }
        
        _readBuffer.get(target);
    }
    
    public ByteBuffer readData() throws IOException
    {
        if (_data.size() >0 && _readBuffer == null)
        {
            buildReadBuffer();
        }
        
        return _readBuffer;
    }
    
    private void buildReadBuffer()
    {
        //optimize for the simple cases
        if(_data.size() == 1)
        {
            _readBuffer = _data.element().duplicate();
        }
        else
        {
            _readBuffer = ByteBuffer.allocate(_dataSize);
            for(ByteBuffer buf:_data)
            {
                _readBuffer.put(buf);
            }
        }
    }
    
    //hack for testing
    @Override public String toString()
    {
        if (_data.size() >0 && _readBuffer == null)
        {
            buildReadBuffer();
        }
        ByteBuffer temp = _readBuffer.duplicate();
        byte[] b = new byte[temp.remaining()];
        temp.get(b);
        return new String(b);
    }
}
