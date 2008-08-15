package org.apache.qpid.nclient.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.qpid.transport.*;
import org.apache.qpid.nclient.MessagePartListener;

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

    public void messageTransfer(MessageTransfer xfr)
    {
        _currentMsg = new ByteBufferMessage(xfr.getId());

        for (Struct st : xfr.getHeader().getStructs())
        {
            if(st instanceof DeliveryProperties)
            {
                _currentMsg.setDeliveryProperties((DeliveryProperties)st);

            }
            else if(st instanceof MessageProperties)
            {
                _currentMsg.setMessageProperties((MessageProperties)st);
            }

        }


        ByteBuffer body = xfr.getBody();
        if (body == null)
        {
            body = ByteBuffer.allocate(0);
        }


        try
        {
            _currentMsg.appendData(body);
        }
        catch(IOException e)
        {
            // A chance for IO exception
            // doesn't occur as we are using
            // a ByteBuffer
        }

        _adaptee.onMessage(_currentMsg);
    }

}
