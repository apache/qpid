package org.apache.qpidity.impl;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.qpidity.DeliveryProperties;
import org.apache.qpidity.MessageProperties;
import org.apache.qpidity.Struct;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.client.MessageListener;
import org.apache.qpidity.client.MessagePartListener;

/** 
 * 
 * Will call onMessage method as soon as data is avialable
 * The client can then start to process the data while
 * the rest of the data is read.
 *
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
            
            public void appendData(byte[] src)
            {
                appendData(ByteBuffer.wrap(src));
            }

            public void appendData(ByteBuffer src)
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
            public void readData(byte[] target)
            {
                if (_readBuffer == null)
                {
                    buildReadBuffer();
                }
                
                _readBuffer.get(target);
            }
            
            private void buildReadBuffer()
            {
                _readBuffer = ByteBuffer.allocate(dataSize);
                for(ByteBuffer buf:_data)
                {
                    _readBuffer.put(buf);
                }
            }
        };
    }
    
    public void addData(ByteBuffer src)
    {
        _currentMsg.appendData(src);
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
