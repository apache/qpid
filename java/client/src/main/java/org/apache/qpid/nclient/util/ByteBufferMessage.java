package org.apache.qpid.nclient.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.api.Message;

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
    private List<ByteBuffer> _data;// = new ArrayList<ByteBuffer>();
    private ByteBuffer _readBuffer;
    private int _dataSize;
    private DeliveryProperties _currentDeliveryProps;
    private MessageProperties _currentMessageProps;
    private int _transferId;
    private Header _header;

    public ByteBufferMessage(MessageProperties messageProperties, DeliveryProperties deliveryProperties)
    {
        _currentMessageProps = messageProperties;
        _currentDeliveryProps = deliveryProperties;
    }

    public void setHeader(Header header) {
        _header = header;
    }

    public Header getHeader() {
        return _header;
    }

    public ByteBufferMessage()
    {
        _currentDeliveryProps = new DeliveryProperties();
        _currentMessageProps = new MessageProperties();
    }

    public ByteBufferMessage(int transferId)
    {
        _transferId = transferId;
    }

    public int getMessageTransferId()
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
        if(_data == null)
        {
            _data = Collections.singletonList(src);
        }
        else
        {
            if(_data.size() == 1)
            {
                _data = new ArrayList<ByteBuffer>(_data);
            }
            _data.add(src);
        }
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

    public void readData(byte[] target)
    {
        getReadBuffer().get(target);
    }

    public ByteBuffer readData()
    {
        return getReadBuffer();
    }

    private void buildReadBuffer()
    {
        //optimize for the simple cases
        if(_data.size() == 1)
        {
            _readBuffer = _data.get(0).duplicate();
        }
        else
        {
            _readBuffer = ByteBuffer.allocate(_dataSize);
            for(ByteBuffer buf:_data)
            {
                _readBuffer.put(buf);
            }
            _readBuffer.flip();
        }
    }

    private ByteBuffer getReadBuffer()
    {
        if (_readBuffer != null )
        {
           return _readBuffer.slice();
        }
        else
        {
            if (_data.size() >0)
            {
               buildReadBuffer();
               return _readBuffer.slice();
            }
            else
            {
                return ByteBuffer.allocate(0);
            }
        }
    }

    //hack for testing
    @Override public String toString()
    {
        ByteBuffer temp = getReadBuffer();
        byte[] b = new byte[temp.remaining()];
        temp.get(b);
        return new String(b);
    }
}
