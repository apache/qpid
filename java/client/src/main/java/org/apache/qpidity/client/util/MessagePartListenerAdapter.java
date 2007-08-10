package org.apache.qpidity.client.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.qpidity.DeliveryProperties;
import org.apache.qpidity.MessageProperties;
import org.apache.qpidity.Struct;
import org.apache.qpidity.api.Message;
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
	Message _currentMsg;
    DeliveryProperties _currentDeliveryProps;
    MessageProperties _currentMessageProps;
    
	public MessagePartListenerAdapter(MessageListener listener)
	{
		_adaptee = listener;
        
        // temp solution.
        _currentMsg = new Message()
        {
            Queue<ByteBuffer> _data = new LinkedList<ByteBuffer>();
            ByteBuffer _readBuffer;
            private int dataSize; 
            
            public void appendData(byte[] src) throws IOException
            {
                appendData(ByteBuffer.wrap(src));
            }

            public void appendData(ByteBuffer src) throws IOException
            {
                _data.offer(src);
                dataSize += src.remaining();
            }
            
            public DeliveryProperties getDeliveryProperties()
            {
                return _currentDeliveryProps;
            }

            public MessageProperties getMessageProperties()
            {
                return _currentMessageProps;
            }

            // since we provide the message only after completion
            // we can assume that when this method is called we have
            // received all data.
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
                    _readBuffer = ByteBuffer.allocate(dataSize);
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
                byte[] b = new byte[_readBuffer.limit()];
                _readBuffer.get(b);
                return new String(b);
            }
        };
    }
    
    public void addData(ByteBuffer src)
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
                _currentDeliveryProps = (DeliveryProperties)struct;      
            }
            else if (struct instanceof MessageProperties)
            {
                _currentMessageProps = (MessageProperties)struct;      
            }
        }
	}
    
	public void messageReceived()
	{        
        _adaptee.onMessage(_currentMsg);
	}
}
