package org.apache.qpidity.nclient.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.qpidity.transport.DeliveryProperties;
import org.apache.qpidity.transport.MessageProperties;
import org.apache.qpidity.transport.Header;
import org.apache.qpidity.nclient.MessagePartListener;

/**
 * This is a simple message assembler.
 * Will call onMessage method of the adaptee
 * when all message data is read.
 *
 * This is a good convinience utility for handling
 * small messages
 */
public class MessagePartListenerAdapter implements MessagePartListener
{
	MessageListener _adaptee;
    ByteBufferMessage _currentMsg;

	public MessagePartListenerAdapter(MessageListener listener)
	{
		_adaptee = listener;
    }

    public void messageTransfer(int transferId)
    {
        _currentMsg = new ByteBufferMessage(transferId);
    }

    public void data(ByteBuffer src)
    {
        try
        {
            _currentMsg.appendData(src);
        }
        catch(IOException e)
        {
            // A chance for IO exception
            // doesn't occur as we are using
            // a ByteBuffer
        }
    }

    public void messageHeader(Header header)
    {
        _currentMsg.setDeliveryProperties(header.get(DeliveryProperties.class));
        _currentMsg.setMessageProperties(header.get(MessageProperties.class));
    }

    public void messageReceived()
    {
        _adaptee.onMessage(_currentMsg);
    }

}
