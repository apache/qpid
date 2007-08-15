package org.apache.qpidity.client.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.qpidity.DeliveryProperties;
import org.apache.qpidity.MessageProperties;
import org.apache.qpidity.Struct;
import org.apache.qpidity.client.MessageListener;
import org.apache.qpidity.client.MessagePartListener;

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
    
    public void messageTransfer(long transferId)
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

	public void messageHeaders(Struct... headers)
	{
		for(Struct struct: headers)
        {
		    if(struct instanceof DeliveryProperties)
            {
                _currentMsg.setDeliveryProperties((DeliveryProperties)struct);      
            }
            else if (struct instanceof MessageProperties)
            {
                _currentMsg.setMessageProperties((MessageProperties)struct);      
            }
        }
	}
    
	public void messageReceived()
	{        
        _adaptee.onMessage(_currentMsg);
	}
}
