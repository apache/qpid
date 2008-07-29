package org.apache.qpid.nclient.util;

import java.nio.ByteBuffer;

import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.api.Message;

public abstract class ReadOnlyMessage implements Message
{
    MessageProperties _messageProperties;
    DeliveryProperties _deliveryProperties;
        
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
    
    public void clearData()
    {
        throw new UnsupportedOperationException("This Message is read only after the initial source, cannot clear data");
    }
}
